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

    /**
     * Creates a new pre-chunk-save event.
     *
     * @param chunk Bukkit chunk being written
     * @param blockCount number of custom block records being written
     * @param displayCount number of managed display records being written
     * @param removingPersistentData true when BlockEngine is removing stored data
     */
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

    /**
     * Returns the Bukkit chunk being written.
     *
     * @return chunk being saved
     */
    public @NotNull Chunk chunk() {
        return chunk;
    }

    /**
     * Returns the number of custom block records being written.
     *
     * @return custom block record count
     */
    public int blockCount() {
        return blockCount;
    }

    /**
     * Returns the number of managed display records being written.
     *
     * @return managed display record count
     */
    public int displayCount() {
        return displayCount;
    }

    /**
     * Returns whether BlockEngine is removing its persistent data from the
     * chunk.
     *
     * @return true when persistent data is being removed
     */
    public boolean removingPersistentData() {
        return removingPersistentData;
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
