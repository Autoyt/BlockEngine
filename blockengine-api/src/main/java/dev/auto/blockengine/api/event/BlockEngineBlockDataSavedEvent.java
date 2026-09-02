package dev.auto.blockengine.api.event;

import dev.auto.blockengine.api.blocks.BlockData;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Fired after BlockEngine saves mutable custom block data back to pending chunk storage.
 */
public class BlockEngineBlockDataSavedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block block;
    private final @NotNull BlockData data;
    private final @Nullable String previousBlockId;
    private final @Nullable String previousStateId;

    /**
     * Creates a new post-save event for mutable block data.
     *
     * @param block Bukkit block whose data was saved
     * @param data saved block data
     * @param previousBlockId previously stored block id, or null
     * @param previousStateId previously stored state id, or null
     */
    public BlockEngineBlockDataSavedEvent(
            @NotNull Block block,
            @NotNull BlockData data,
            @Nullable String previousBlockId,
            @Nullable String previousStateId
    ) {
        this.block = Objects.requireNonNull(block, "block");
        this.data = Objects.requireNonNull(data, "data");
        this.previousBlockId = previousBlockId;
        this.previousStateId = previousStateId;
    }

    /**
     * Returns the Bukkit block whose data was saved.
     *
     * @return affected block
     */
    public @NotNull Block block() {
        return block;
    }

    /**
     * Returns the saved block data.
     *
     * @return saved block data
     */
    public @NotNull BlockData data() {
        return data;
    }

    /**
     * Returns the full custom block id that was saved.
     *
     * @return full block id
     */
    public @NotNull String blockId() {
        return data.blockId();
    }

    /**
     * Returns the state id that was saved.
     *
     * @return state id
     */
    public @NotNull String stateId() {
        return data.stateId();
    }

    /**
     * Returns the previously stored custom block id, if any.
     *
     * @return previous block id, or null
     */
    public @Nullable String previousBlockId() {
        return previousBlockId;
    }

    /**
     * Returns the previously stored custom block state id, if any.
     *
     * @return previous state id, or null
     */
    public @Nullable String previousStateId() {
        return previousStateId;
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
