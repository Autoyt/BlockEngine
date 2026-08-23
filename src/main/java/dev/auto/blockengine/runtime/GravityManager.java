package dev.auto.blockengine.runtime;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.event.BlockEngineModificationEvent;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GravityManager {
    private static final GravityManager instance = new GravityManager();
    private final @NotNull Map<UUID, PendingFall> fallingBlocks = new HashMap<>();

    private GravityManager() {
    }

    public static @NotNull GravityManager getInstance() {
        return instance;
    }

    public void clear() {
        fallingBlocks.clear();
    }

    public boolean check(@NotNull Block block) {
        RuntimeBlockView customBlock = ChunkEngine.getBlock(location(block));
        if (customBlock == null || !gravityEnabled(customBlock)) {
            return false;
        }

        Block below = block.getRelative(BlockFace.DOWN);
        if (!BlockMover.canMove(block, customBlock, below, BlockAdapter.MoveCause.GRAVITY)) {
            return false;
        }

        return launch(block, customBlock);
    }

    public boolean land(@NotNull EntityChangeBlockEvent event) {
        PendingFall pending = fallingBlocks.remove(event.getEntity().getUniqueId());
        if (pending == null) {
            return false;
        }

        event.setCancelled(true);
        event.getEntity().remove();

        Block target = event.getBlock();
        BlockDefinition definition = BlockRegistry.getBlock(pending.storedBlock().blockId());
        if (definition == null || !BlockMover.canOccupy(target)) {
            drop(target, pending);
            return true;
        }

        Block origin = pending.origin().getBlock();
        BlockData data = pending.loadData(definition);
        BlockContext context = new BlockContext(definition.adapter(), data, origin, null);
        if (!definition.adapter().canMove(context, origin, target, BlockAdapter.MoveCause.GRAVITY)) {
            drop(target, pending);
            return true;
        }

        definition.adapter().onMove(context, origin, target, BlockAdapter.MoveCause.GRAVITY);
        byte[] payload = definition.adapter().save(context.data());
        ChunkEngine.StoredBlock moved = BlockMover.movedBlock(
                origin,
                pending.storedBlock(),
                target,
                context.data(),
                definition,
                payload
        );
        ChunkEngine.data(target.getChunk()).setBlock(moved);
        target.setType(Main.getBackingBlock(), false);
        ChunkEngine.changed(target);
        BlockIntegrityManager.getInstance().callEvent(
                BlockEngineModificationEvent.Action.SET_CUSTOM_BLOCK,
                target,
                null,
                null,
                moved.blockId(),
                moved.stateId()
        );
        return true;
    }

    private boolean launch(@NotNull Block block, @NotNull RuntimeBlockView customBlock) {
        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return false;
        }

        ChunkEngine.Data data = ChunkEngine.data(block.getChunk());
        data.removeBlock(block.getX() & 15, block.getY(), block.getZ() & 15);
        block.setType(Material.AIR, false);
        ChunkEngine.changed(block);
        BlockIntegrityManager.getInstance().callEvent(
                BlockEngineModificationEvent.Action.REMOVE_CUSTOM_BLOCK,
                block,
                customBlock.storedBlock().blockId(),
                customBlock.storedBlock().stateId(),
                null,
                null
        );

        FallingBlock fallingBlock = block.getWorld().spawnFallingBlock(
                block.getLocation().add(0.5, 0.0, 0.5),
                customBlock.storedBlock().fallbackBlock().createBlockData()
        );
        fallingBlock.setDropItem(false);
        fallingBlock.setCancelDrop(true);
        fallingBlocks.put(fallingBlock.getUniqueId(), new PendingFall(block.getLocation(), customBlock.storedBlock()));
        return true;
    }

    private boolean gravityEnabled(@NotNull RuntimeBlockView customBlock) {
        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return false;
        }

        try {
            return definition.apiDefinition()
                    .state(customBlock.storedBlock().stateId())
                    .movement()
                    .gravity();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void drop(@NotNull Block target, @NotNull PendingFall pending) {
        BlockDefinition definition = BlockRegistry.getBlock(pending.storedBlock().blockId());
        if (definition == null || !pending.storedBlock().dropsItem()) {
            return;
        }
        target.getWorld().dropItemNaturally(target.getLocation().add(0.5, 0.5, 0.5),
                ItemManager.create(definition, pending.storedBlock().stateId()));
    }

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private record PendingFall(
            @NotNull Location origin,
            @NotNull ChunkEngine.StoredBlock storedBlock
    ) {
        private @NotNull BlockData loadData(@NotNull BlockDefinition definition) {
            @Nullable BlockData loaded = storedBlock.loadData(definition);
            return loaded == null ? ChunkEngine.SimpleBlockData.copyOf(storedBlock.data()) : loaded;
        }
    }
}
