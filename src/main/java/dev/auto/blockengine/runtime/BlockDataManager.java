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

        long loadStarted = System.nanoTime();
        BlockData data = view.storedBlock().loadData(definition);
        PerformanceMetrics.record(PerformanceMetrics.ADAPTER_LOAD, System.nanoTime() - loadStarted, 1, view.storedBlock().payloadBytes());
        if (data == null) {
            return null;
        }
        return new BlockContext(definition.adapter(), data, block, player, view.storedBlock());
    }

    public boolean saveIfChanged(
            @NotNull Block block, @NotNull BlockContext context, @NotNull RuntimeBlockView expected,
            @NotNull ChunkEngine.SimpleBlockData before
    ) {
        RuntimeBlockView current = ChunkEngine.getBlock(expected.location());
        if (current == null || current.storedBlock().data() != expected.storedBlock().data()) {
            return false;
        }

        // An adapter can change its private payload without touching any public data maps.
        boolean modified = !before.equalsData(context.data())
                || !expected.storedBlock().matchesPayload(context.adapter().save(context.data()));
        return modified && save(block, context, expected);
    }

    public boolean save(@NotNull Block block, @NotNull BlockContext context) {
        return save(block, context, null);
    }

    public boolean save(@NotNull Block block, @NotNull BlockContext context, @Nullable RuntimeBlockView expected) {
        BlockDefinition definition = BlockRegistry.getBlock(context.blockId());
        if (definition == null) {
            return false;
        }
        definition.apiDefinition().state(context.stateId());

        BlockLocationKey location = new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
        RuntimeBlockView previous = ChunkEngine.getBlock(location);
        if (previous == null || !context.matches(previous)
                || expected != null && previous.storedBlock().data() != expected.storedBlock().data()) {
            return false;
        }

        if (BlockEngineEvents.callCancellable(new BlockEngineBlockDataSaveEvent(
                block,
                context,
                previous.storedBlock().blockId(),
                previous.storedBlock().stateId()
        ))) {
            return false;
        }

        long saveStarted = System.nanoTime();
        byte[] payload = definition.adapter().save(context.data());
        PerformanceMetrics.record(PerformanceMetrics.ADAPTER_SAVE, System.nanoTime() - saveStarted,
                1, payload == null ? 0 : payload.length);
        RuntimeBlockView current = ChunkEngine.getBlock(location);
        if (current == null || current.storedBlock().data() != previous.storedBlock().data()) {
            return false;
        }

        ChunkEngine.data(block.getChunk()).setBlock(ChunkEngine.StoredBlock.from(
                block.getX() & 15,
                block.getY(),
                block.getZ() & 15,
                context.data(),
                definition.apiDefinition(),
                payload,
                current.storedBlock().displays(),
                current.storedBlock().orientation()
        ));
        ChunkEngine.changed(block);
        BlockEngineEvents.call(new BlockEngineBlockDataSavedEvent(
                block,
                context.data(),
                previous.storedBlock().blockId(),
                previous.storedBlock().stateId()
        ));
        return true;
    }
}

