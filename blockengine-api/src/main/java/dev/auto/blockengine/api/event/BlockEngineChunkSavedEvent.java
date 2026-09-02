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

    /**
     * Creates a new post-chunk-save event.
     *
     * @param chunk Bukkit chunk that was written
     * @param blockCount number of custom block records written
     * @param displayCount number of managed display records written
     * @param removedPersistentData true when BlockEngine removed stored data
     */
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

    /**
     * Returns the Bukkit chunk that was written.
     *
     * @return chunk that was saved
     */
    public @NotNull Chunk chunk() {
        return chunk;
    }

    /**
     * Returns the number of custom block records written.
     *
     * @return custom block record count
     */
    public int blockCount() {
        return blockCount;
    }

    /**
     * Returns the number of managed display records written.
     *
     * @return managed display record count
     */
    public int displayCount() {
        return displayCount;
    }

    /**
     * Returns whether BlockEngine removed its persistent data from the chunk.
     *
     * @return true when persistent data was removed
     */
    public boolean removedPersistentData() {
        return removedPersistentData;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the handler list for Bukkit's event system.
     *
     * @return event handler list
     */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
