package dev.auto.blockengine.api.event;

import dev.auto.blockengine.api.blocks.BlockContext;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Fired before BlockEngine saves mutable custom block data back to storage.
 *
 * <p>Cancel this event to stop the pending data update from being written.
 * This does not automatically undo changes that listener code already made to
 * the mutable {@link BlockContext#data()} object.</p>
 */
public class BlockEngineBlockDataSaveEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block block;
    private final @NotNull BlockContext context;
    private final @Nullable String previousBlockId;
    private final @Nullable String previousStateId;
    private boolean cancelled;

    /**
     * Creates a new pre-save event for mutable block data.
     *
     * @param block Bukkit block whose data is being saved
     * @param context custom block context
     * @param previousBlockId previously stored block id, or null
     * @param previousStateId previously stored state id, or null
     */
    public BlockEngineBlockDataSaveEvent(
            @NotNull Block block,
            @NotNull BlockContext context,
            @Nullable String previousBlockId,
            @Nullable String previousStateId
    ) {
        this.block = Objects.requireNonNull(block, "block");
        this.context = Objects.requireNonNull(context, "context");
        this.previousBlockId = previousBlockId;
        this.previousStateId = previousStateId;
    }

    /**
     * Returns the Bukkit block whose data is being saved.
     *
     * @return affected block
     */
    public @NotNull Block block() {
        return block;
    }

    /**
     * Returns the custom block context being saved.
     *
     * @return block context
     */
    public @NotNull BlockContext context() {
        return context;
    }

    /**
     * Returns the full custom block id being saved.
     *
     * @return full block id
     */
    public @NotNull String blockId() {
        return context.blockId();
    }

    /**
     * Returns the state id being saved.
     *
     * @return state id
     */
    public @NotNull String stateId() {
        return context.stateId();
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
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
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
