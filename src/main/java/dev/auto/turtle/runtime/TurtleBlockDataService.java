package dev.auto.turtle.runtime;

import dev.auto.turtle.api.blocks.BlockData;
import dev.auto.turtle.pdc.TurtleChunkData;
import dev.auto.turtle.registry.BlockRegistry;
import dev.auto.turtle.types.BlockDefinition;
import dev.auto.turtle.types.ChunkKey;
import dev.auto.turtle.visibility.VisibilityService;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TurtleBlockDataService {
    private TurtleBlockDataService() {
    }

    public static @Nullable TurtleBlockContext context(
            @NotNull Block block,
            @NotNull RuntimeBlockView view,
            @Nullable Player player
    ) {
        BlockDefinition definition = BlockRegistry.getBlock(view.storedBlock().blockId());
        if (definition == null) {
            return null;
        }

        BlockData data = view.storedBlock().loadData(definition);
        if (data == null) {
            data = TurtleChunkData.SimpleBlockData.copyOf(view.storedBlock().data());
        }
        return new TurtleBlockContext(definition.adapter(), data, block, player);
    }

    public static void save(@NotNull Block block, @NotNull TurtleBlockContext context) {
        BlockDefinition definition = BlockRegistry.getBlock(context.blockId());
        if (definition == null) {
            return;
        }
        definition.apiDefinition().state(context.stateId());

        TurtleChunkData chunkData = TurtleChunkData.load(block.getChunk(), TurtleChunkRuntime.chunkDataKey());
        chunkData.setBlock(
                block.getX() & 15,
                block.getY(),
                block.getZ() & 15,
                context.data(),
                definition.apiDefinition(),
                definition.adapter().save(context.data())
        );
        TurtleChunkData.save(block.getChunk(), TurtleChunkRuntime.chunkDataKey(), chunkData);
        TurtleChunkRuntime.loadChunk(block.getChunk(), VisibilityService.config());
        VisibilityService.refreshPlayersNear(ChunkKey.from(block.getChunk()));
    }
}
