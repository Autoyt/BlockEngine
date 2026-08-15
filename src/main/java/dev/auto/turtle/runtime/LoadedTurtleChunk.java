package dev.auto.turtle.runtime;

import dev.auto.turtle.types.ChunkKey;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LoadedTurtleChunk {
    private final @NotNull ChunkKey key;
    private final @NotNull List<RuntimeBlockView> blocks = new ArrayList<>();
    private final @NotNull List<RuntimeBlockView> exposedBlocks = new ArrayList<>();

    public LoadedTurtleChunk(@NotNull ChunkKey key) {
        this.key = key;
    }

    public @NotNull ChunkKey key() {
        return key;
    }

    public void add(@NotNull RuntimeBlockView block) {
        blocks.add(block);
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
}
