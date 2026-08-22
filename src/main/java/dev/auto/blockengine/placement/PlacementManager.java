package dev.auto.blockengine.placement;

import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.event.BlockEngineBlockPlaceEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockPlacedEvent;
import dev.auto.blockengine.api.event.BlockEngineModificationEvent;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;
import dev.auto.blockengine.runtime.BlockContext;
import dev.auto.blockengine.runtime.CreateContext;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlacementManager {
    private static final PlacementManager instance = new PlacementManager();

    private PlacementManager() {
    }

    public static @NotNull PlacementManager getInstance() {
        return instance;
    }

    public boolean place(@NotNull BlockPlaceEvent event) {
        String blockId = ItemManager.blockId(event.getItemInHand());
        if (blockId == null) {
            return false;
        }

        String namespace = ItemManager.namespace(event.getItemInHand());
        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (namespace == null
                || !NamespaceRegistry.loaded(namespace)
                || definition == null
                || !definition.apiDefinition().item().placeable()) {
            event.setCancelled(true);
            return true;
        }

        BlockFace placedAgainst = event.getBlockAgainst().getFace(event.getBlockPlaced());
        PlacementVerificationEngine.Result verification = PlacementVerificationEngine.verify(
                new PlacementVerificationEngine.Request(
                        event.getBlockPlaced(),
                        definition,
                        ItemManager.stateId(event.getItemInHand()),
                        event.getPlayer(),
                        placedAgainst,
                        event.getHand()
                )
        );
        if (!verification.allowed()) {
            event.setCancelled(true);
            return true;
        }

        return place(
                event.getBlockPlaced(),
                definition,
                event.getPlayer(),
                placedAgainst,
                ItemManager.stateId(event.getItemInHand())
        );
    }

    public boolean place(
            @NotNull Block block,
            @NotNull BlockDefinition definition,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst,
            @Nullable String stateId
    ) {
        String defaultState = stateId == null || stateId.isBlank()
                ? definition.apiDefinition().defaultState()
                : stateId;
        definition.apiDefinition().state(defaultState);

        RuntimeBlockView previous = ChunkEngine.getBlock(new dev.auto.blockengine.types.BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
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
        ChunkEngine.Data chunkData = ChunkEngine.data(block.getChunk());
        chunkData.setBlock(block.getX() & 15, block.getY(), block.getZ() & 15, data, definition.apiDefinition(), payload);

        block.setType(Main.getBackingBlock(), false);
        ChunkEngine.changed(block);

        definition.adapter().onPlace(new BlockContext(definition.adapter(), data, block, player));
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
}

