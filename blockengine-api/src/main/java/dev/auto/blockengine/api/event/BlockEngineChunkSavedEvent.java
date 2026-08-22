package dev.auto.blockengine.api.event;

import org.bukkit.Chunk;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired after BlockEngine writes pending custom chunk data to a Bukkit chunk.
 */
public class BlockEngineChunkSavedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Chunk chunk;
    private final int blockCount;
    private final int displayCount;
    private final boolean removedPersistentData;

    public BlockEngineChunkSavedEvent(
            @NotNull Chunk chunk,
            int blockCount,
            int displayCount,
            boolean removedPersistentData
    ) {
        this.chunk = Objects.requireNonNull(chunk, "chunk");
        this.blockCount = blockCount;
        this.displayCount = displayCount;
        this.removedPersistentData = removedPersistentData;
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

    public boolean removedPersistentData() {
        return removedPersistentData;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
