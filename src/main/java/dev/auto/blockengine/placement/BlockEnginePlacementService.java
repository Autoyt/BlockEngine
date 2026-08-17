package dev.auto.blockengine.placement;

import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.items.BlockEngineItemManager;
import dev.auto.blockengine.pdc.BlockEngineChunkData;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;
import dev.auto.blockengine.runtime.BlockEngineBlockContext;
import dev.auto.blockengine.runtime.BlockEngineCreateContext;
import dev.auto.blockengine.runtime.BlockEngineMutationBatcher;
import dev.auto.blockengine.types.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BlockEnginePlacementService {
    private BlockEnginePlacementService() {
    }

    public static boolean place(@NotNull BlockPlaceEvent event) {
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

        Block block = event.getBlockPlaced();
        String stateId = BlockEngineItemManager.stateId(event.getItemInHand());
        String defaultState = stateId == null || stateId.isBlank()
                ? definition.apiDefinition().defaultState()
                : stateId;
        definition.apiDefinition().state(defaultState);

        BlockEngineCreateContext createContext = new BlockEngineCreateContext(
                block.getLocation(),
                event.getPlayer(),
                event.getBlockAgainst().getFace(block),
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
        BlockEngineChunkData chunkData = BlockEngineMutationBatcher.data(block.getChunk());
        chunkData.setBlock(block.getX() & 15, block.getY(), block.getZ() & 15, data, definition.apiDefinition(), payload);

        block.setType(BlockEngineBackingBlock.material(), false);
        BlockEngineMutationBatcher.changed(block);

        definition.adapter().onPlace(new BlockEngineBlockContext(definition.adapter(), data, block, event.getPlayer()));
        return true;
    }

    public static boolean place(
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
        BlockEngineChunkData chunkData = BlockEngineMutationBatcher.data(block.getChunk());
        chunkData.setBlock(block.getX() & 15, block.getY(), block.getZ() & 15, data, definition.apiDefinition(), payload);

        block.setType(BlockEngineBackingBlock.material(), false);
        BlockEngineMutationBatcher.changed(block);

        definition.adapter().onPlace(new BlockEngineBlockContext(definition.adapter(), data, block, player));
        return true;
    }
}
