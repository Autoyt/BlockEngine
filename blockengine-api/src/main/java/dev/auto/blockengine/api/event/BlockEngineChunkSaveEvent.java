package dev.auto.blockengine.api.event;

import org.bukkit.Chunk;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired before BlockEngine writes pending custom chunk data to a Bukkit chunk.
 */
public class BlockEngineChunkSaveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Chunk chunk;
    private final int blockCount;
    private final int displayCount;
    private final boolean removingPersistentData;

    public BlockEngineChunkSaveEvent(
            @NotNull Chunk chunk,
            int blockCount,
            int displayCount,
            boolean removingPersistentData
    ) {
        this.chunk = Objects.requireNonNull(chunk, "chunk");
        this.blockCount = blockCount;
        this.displayCount = displayCount;
        this.removingPersistentData = removingPersistentData;
    }

    public @NotNull Chunk chunk() {
        return chunk;
    }

    public int blockCount() {
        return blockCount;
    }

    public int displayCount() {
        return displayCount;
    }

    public boolean removingPersistentData() {
        return removingPersistentData;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
