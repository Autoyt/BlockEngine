package dev.auto.blockengine.placement;

import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.items.BlockEngineItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;
import dev.auto.blockengine.runtime.BlockEngineBlockContext;
import dev.auto.blockengine.runtime.BlockEngineCreateContext;
import dev.auto.blockengine.runtime.BlockEngineMutationBatcher;
import dev.auto.blockengine.runtime.ChunkEngine;
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
        String blockId = BlockEngineItemManager.blockId(event.getItemInHand());
        if (blockId == null) {
            return false;
        }

        String namespace = BlockEngineItemManager.namespace(event.getItemInHand());
        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (namespace == null
                || !NamespaceRegistry.loaded(namespace)
                || definition == null
                || !definition.apiDefinition().item().placeable()) {
            event.setCancelled(true);
            return true;
        }

        return place(
                event.getBlockPlaced(),
                definition,
                event.getPlayer(),
                event.getBlockAgainst().getFace(event.getBlockPlaced()),
                BlockEngineItemManager.stateId(event.getItemInHand())
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

        BlockEngineCreateContext createContext = new BlockEngineCreateContext(
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
        ChunkEngine.Data chunkData = BlockEngineMutationBatcher.data(block.getChunk());
        chunkData.setBlock(block.getX() & 15, block.getY(), block.getZ() & 15, data, definition.apiDefinition(), payload);

        block.setType(Main.getBackingBlock(), false);
        BlockEngineMutationBatcher.changed(block);

        definition.adapter().onPlace(new BlockEngineBlockContext(definition.adapter(), data, block, player));
        return true;
    }
}

