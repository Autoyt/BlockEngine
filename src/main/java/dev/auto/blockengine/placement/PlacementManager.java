package dev.auto.blockengine.placement;

import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.event.BlockEngineBlockPlaceEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockPlacedEvent;
import dev.auto.blockengine.api.event.BlockEngineModificationEvent;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.entity.ManagedDisplayManager;
import dev.auto.blockengine.runtime.BlockContext;
import dev.auto.blockengine.runtime.BlockDataManager;
import dev.auto.blockengine.runtime.CreateContext;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.logging.Level;

public final class PlacementManager {
    private static final PlacementManager instance = new PlacementManager();

    private PlacementManager() {
    }

    public static @NotNull PlacementManager getInstance() {
        return instance;
    }

    public boolean place(
            @NotNull Block block,
            @NotNull BlockDefinition definition,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst,
            @Nullable String stateId
    ) {
        return place(block, definition, player, placedAgainst, stateId, () -> true);
    }

    public boolean place(
            @NotNull Block block,
            @NotNull BlockDefinition definition,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst,
            @Nullable String stateId,
            @NotNull BooleanSupplier beforeReplace
    ) {
        BlockOrientation orientation = BlockOrientation.forPlacement(definition.apiDefinition().placement(),
                player == null ? null : player.getFacing(), placedAgainst);
        return place(block, definition, player, placedAgainst, stateId, beforeReplace, orientation);
    }

    public boolean place(
            @NotNull Block block, @NotNull BlockDefinition definition, @Nullable Player player,
            @Nullable BlockFace placedAgainst, @Nullable String stateId, @NotNull BlockOrientation orientation
    ) {
        return place(block, definition, player, placedAgainst, stateId, () -> true, orientation);
    }

    private boolean place(
            @NotNull Block block, @NotNull BlockDefinition definition, @Nullable Player player,
            @Nullable BlockFace placedAgainst, @Nullable String stateId,
            @NotNull BooleanSupplier beforeReplace, @NotNull BlockOrientation orientation
    ) {
        if (block.getY() < block.getWorld().getMinHeight() || block.getY() >= block.getWorld().getMaxHeight()
                || waterloggedSolid(block)) {
            return false;
        }

        String defaultState = stateId == null || stateId.isBlank()
                ? definition.apiDefinition().defaultState()
                : stateId;
        if (!definition.apiDefinition().states().containsKey(defaultState)) {
            return false;
        }

        BlockLocationKey location = new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
        ChunkEngine.Data chunkData;
        try {
            chunkData = ChunkEngine.data(block.getChunk());
        } catch (IllegalStateException unreadableChunk) {
            // Loading logs the underlying failure. Leave the original block and item untouched.
            return false;
        }
        RuntimeBlockView previous = ChunkEngine.getBlock(location);
        var originalBlockData = block.getBlockData();
        if (BlockEngineEvents.callCancellable(new BlockEngineBlockPlaceEvent(
                block,
                definition.apiDefinition(),
                player,
                placedAgainst,
                defaultState,
                previous == null ? null : previous.storedBlock().blockId(),
                previous == null ? null : previous.storedBlock().stateId()
        ))) {
            return false;
        }

        CreateContext createContext = new CreateContext(
                block.getLocation(),
                player,
                placedAgainst,
                definition.id(),
                defaultState
        );

        BlockData data = definition.adapter().createDefaultData(createContext);
        data.blockId(definition.id());
        if (data.stateId() == null || data.stateId().isBlank()) {
            data.stateId(defaultState);
        }
        definition.apiDefinition().state(data.stateId());

        byte[] payload = definition.adapter().save(data);
        ChunkEngine.StoredBlock prepared = ChunkEngine.StoredBlock.from(
                block.getX() & 15, block.getY(), block.getZ() & 15, data, definition.apiDefinition(), payload
        ).withOrientation(orientation);

        // Placement listeners and adapter callbacks can replace the same position.
        RuntimeBlockView current = ChunkEngine.getBlock(location);
        if ((current == null ? null : current.storedBlock()) != (previous == null ? null : previous.storedBlock())
                || !originalBlockData.equals(block.getBlockData()) || chunkData != ChunkEngine.data(block.getChunk())) {
            return false;
        }
        if (!beforeReplace.getAsBoolean()) {
            return false;
        }

        if (previous != null) {
            ManagedDisplayManager.getInstance().removeBlockAttached(location);
        }
        chunkData.setBlock(prepared);
        block.setType(Main.getBackingBlock(), false);
        ChunkEngine.changed(block);

        sound(block, definition, data.stateId());
        RuntimeBlockView placed = ChunkEngine.getBlock(location);
        BlockContext context = new BlockContext(definition.adapter(), data, block, player);
        try {
            definition.adapter().onPlace(context);
            if (placed != null) {
                BlockDataManager.getInstance().saveIfChanged(block, context, placed, prepared.data());
            }
        } catch (RuntimeException exception) {
            Main.getInstance().getLogger().log(Level.WARNING, "Placement callback failed for " + definition.id(), exception);
        }
        BlockEngineEvents.call(new BlockEngineBlockPlacedEvent(
                block,
                definition.apiDefinition(),
                data,
                player,
                placedAgainst,
                previous == null ? null : previous.storedBlock().blockId(),
                previous == null ? null : previous.storedBlock().stateId()
        ));
        BlockIntegrityManager.getInstance().callEvent(
                BlockEngineModificationEvent.Action.SET_CUSTOM_BLOCK,
                block,
                previous == null ? null : previous.storedBlock().blockId(),
                previous == null ? null : previous.storedBlock().stateId(),
                data.blockId(),
                data.stateId()
        );
        return true;
    }

    private void sound(@NotNull Block block, @NotNull BlockDefinition definition, @NotNull String stateId) {
        String sound = placeSound(definition, stateId);
        if (sound == null) {
            return;
        }

        World world = block.getWorld();
        world.playSound(
                block.getLocation().add(0.5, 0.5, 0.5),
                sound,
                SoundCategory.BLOCKS,
                0.8f,
                1.0f
        );
    }

    private @Nullable String placeSound(@NotNull BlockDefinition definition, @NotNull String stateId) {
        try {
            String sound = definition.apiDefinition()
                    .state(stateId)
                    .sounds()
                    .place();
            return sound == null || NamespacedKey.fromString(sound) == null ? null : sound;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean waterloggedSolid(@NotNull Block block) {
        return !block.isLiquid()
                && block.getBlockData() instanceof Waterlogged waterlogged
                && waterlogged.isWaterlogged();
    }
}

