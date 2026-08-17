package dev.auto.turtle.placement;

import dev.auto.turtle.api.blocks.BlockData;
import dev.auto.turtle.items.TurtleItemManager;
import dev.auto.turtle.pdc.TurtleChunkData;
import dev.auto.turtle.registry.BlockRegistry;
import dev.auto.turtle.registry.NamespaceRegistry;
import dev.auto.turtle.runtime.TurtleBlockContext;
import dev.auto.turtle.runtime.TurtleCreateContext;
import dev.auto.turtle.runtime.TurtleMutationBatcher;
import dev.auto.turtle.types.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TurtlePlacementService {
    private TurtlePlacementService() {
    }

    public static boolean place(@NotNull BlockPlaceEvent event) {
        String blockId = TurtleItemManager.blockId(event.getItemInHand());
        if (blockId == null) {
            return false;
        }

        String namespace = TurtleItemManager.namespace(event.getItemInHand());
        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (namespace == null
                || !NamespaceRegistry.loaded(namespace)
                || definition == null
                || !definition.apiDefinition().item().placeable()) {
            event.setCancelled(true);
            return true;
        }

        Block block = event.getBlockPlaced();
        String stateId = TurtleItemManager.stateId(event.getItemInHand());
        String defaultState = stateId == null || stateId.isBlank()
                ? definition.apiDefinition().defaultState()
                : stateId;
        definition.apiDefinition().state(defaultState);

        TurtleCreateContext createContext = new TurtleCreateContext(
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
        TurtleChunkData chunkData = TurtleMutationBatcher.data(block.getChunk());
        chunkData.setBlock(block.getX() & 15, block.getY(), block.getZ() & 15, data, definition.apiDefinition(), payload);

        block.setType(TurtleBackingBlock.material(), false);
        TurtleMutationBatcher.changed(block);

        definition.adapter().onPlace(new TurtleBlockContext(definition.adapter(), data, block, event.getPlayer()));
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

        TurtleCreateContext createContext = new TurtleCreateContext(
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
        TurtleChunkData chunkData = TurtleMutationBatcher.data(block.getChunk());
        chunkData.setBlock(block.getX() & 15, block.getY(), block.getZ() & 15, data, definition.apiDefinition(), payload);

        block.setType(TurtleBackingBlock.material(), false);
        TurtleMutationBatcher.changed(block);

        definition.adapter().onPlace(new TurtleBlockContext(definition.adapter(), data, block, player));
        return true;
    }
}
