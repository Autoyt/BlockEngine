package dev.auto.blockengine.runtime;

import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.event.BlockEngineBlockDataSaveEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockDataSavedEvent;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
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

    public @Nullable BlockContext context(
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
        return new BlockContext(definition.adapter(), data, block, player);
    }

    public void save(@NotNull Block block, @NotNull BlockContext context) {
        BlockDefinition definition = BlockRegistry.getBlock(context.blockId());
        if (definition == null) {
            return;
        }
        definition.apiDefinition().state(context.stateId());

        RuntimeBlockView previous = ChunkEngine.getBlock(new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
        if (BlockEngineEvents.callCancellable(new BlockEngineBlockDataSaveEvent(
                block,
                context,
                previous == null ? null : previous.storedBlock().blockId(),
                previous == null ? null : previous.storedBlock().stateId()
        ))) {
            return;
        }

        ChunkEngine.Data chunkData = ChunkEngine.data(block.getChunk());
        chunkData.setBlock(
                block.getX() & 15,
                block.getY(),
                block.getZ() & 15,
                context.data(),
                definition.apiDefinition(),
                definition.adapter().save(context.data())
        );
        ChunkEngine.changed(block);
        BlockEngineEvents.call(new BlockEngineBlockDataSavedEvent(
                block,
                context.data(),
                previous == null ? null : previous.storedBlock().blockId(),
                previous == null ? null : previous.storedBlock().stateId()
        ));
    }
}

