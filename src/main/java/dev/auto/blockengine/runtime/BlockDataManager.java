package dev.auto.blockengine.runtime;

import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BlockDataManager {
    private static final BlockDataManager instance = new BlockDataManager();

    private BlockDataManager() {
    }

    public static @NotNull BlockDataManager getInstance() {
        return instance;
    }

    public @Nullable BlockEngineBlockContext context(
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
            data = ChunkEngine.SimpleBlockData.copyOf(view.storedBlock().data());
        }
        return new BlockEngineBlockContext(definition.adapter(), data, block, player);
    }

    public void save(@NotNull Block block, @NotNull BlockEngineBlockContext context) {
        BlockDefinition definition = BlockRegistry.getBlock(context.blockId());
        if (definition == null) {
            return;
        }
        definition.apiDefinition().state(context.stateId());

        ChunkEngine.Data chunkData = BlockEngineMutationBatcher.data(block.getChunk());
        chunkData.setBlock(
                block.getX() & 15,
                block.getY(),
                block.getZ() & 15,
                context.data(),
                definition.apiDefinition(),
                definition.adapter().save(context.data())
        );
        BlockEngineMutationBatcher.changed(block);
    }
}

