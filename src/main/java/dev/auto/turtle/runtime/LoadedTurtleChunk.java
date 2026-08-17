package dev.auto.turtle.runtime;

import dev.auto.turtle.types.ChunkKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LoadedTurtleChunk {
    private final @NotNull ChunkKey key;
    private final @NotNull List<RuntimeBlockView> blocks = new ArrayList<>();
    private final @NotNull List<RuntimeBlockView> exposedBlocks = new ArrayList<>();
    private final @NotNull Map<Long, RuntimeBlockView> byLocalPosition = new HashMap<>();

    public LoadedTurtleChunk(@NotNull ChunkKey key) {
        this.key = key;
    }

    public @NotNull ChunkKey key() {
        return key;
    }

    public void add(@NotNull RuntimeBlockView block) {
        blocks.add(block);
        byLocalPosition.put(localKey(block.location().x() & 15, block.location().y(), block.location().z() & 15), block);
        if (block.exposed()) {
            exposedBlocks.add(block);
        }
    }

    public @NotNull List<RuntimeBlockView> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    public @NotNull List<RuntimeBlockView> exposedBlocks() {
        return Collections.unmodifiableList(exposedBlocks);
    }

    public @Nullable RuntimeBlockView block(int localX, int y, int localZ) {
        return byLocalPosition.get(localKey(localX, y, localZ));
    }

    private long localKey(int localX, int y, int localZ) {
        return ((long) localX & 15L) << 36
                | ((long) localZ & 15L) << 32
                | ((long) y & 0xffffffffL);
    }
}
