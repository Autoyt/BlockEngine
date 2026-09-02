package dev.auto.blockengine.api.event;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after BlockEngine modifies or reconciles a custom block record.
 *
 * <p>This event represents mutations performed through BlockEngine's own
 * systems, such as API placement, API clearing, normal custom block removal, or
 * stale-record reconciliation. It is not fired for arbitrary vanilla block
 * changes unless BlockEngine reacts by changing its persisted custom block
 * state.</p>
 */
public class BlockEngineModificationEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Action action;
    private final @NotNull Block block;
    private final @Nullable String previousBlockId;
    private final @Nullable String previousStateId;
    private final @Nullable String newBlockId;
    private final @Nullable String newStateId;

    /**
     * Creates a new BlockEngine modification event.
     *
     * @param action mutation action
     * @param block Bukkit block whose BlockEngine state changed
     * @param previousBlockId custom block id before the mutation, or null
     * @param previousStateId state id before the mutation, or null
     * @param newBlockId custom block id after the mutation, or null
     * @param newStateId state id after the mutation, or null
     */
    public BlockEngineModificationEvent(
            @NotNull Action action,
            @NotNull Block block,
            @Nullable String previousBlockId,
            @Nullable String previousStateId,
            @Nullable String newBlockId,
            @Nullable String newStateId
    ) {
        this.action = action;
        this.block = block;
        this.previousBlockId = previousBlockId;
        this.previousStateId = previousStateId;
        this.newBlockId = newBlockId;
        this.newStateId = newStateId;
    }

    /**
     * Returns the kind of BlockEngine mutation that occurred.
     *
     * @return mutation action
     */
    public @NotNull Action action() {
        return action;
    }

    /**
     * Returns the Bukkit block whose BlockEngine state changed.
     *
     * @return affected block
     */
    public @NotNull Block block() {
        return block;
    }

    /**
     * Returns the custom block id before the mutation, if any.
     *
     * @return previous custom block id, or null
     */
    public @Nullable String previousBlockId() {
        return previousBlockId;
    }

    /**
     * Returns the custom block state id before the mutation, if any.
     *
     * @return previous state id, or null
     */
    public @Nullable String previousStateId() {
        return previousStateId;
    }

    /**
     * Returns the custom block id after the mutation, if any.
     *
     * @return new custom block id, or null
     */
    public @Nullable String newBlockId() {
        return newBlockId;
    }

    /**
     * Returns the custom block state id after the mutation, if any.
     *
     * @return new state id, or null
     */
    public @Nullable String newStateId() {
        return newStateId;
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

    /**
     * BlockEngine world mutation categories.
     */
    public enum Action {
        /**
         * A custom block was placed or replaced through BlockEngine.
         */
        SET_CUSTOM_BLOCK,

        /**
         * A custom block was intentionally cleared through BlockEngine.
         */
        CLEAR_CUSTOM_BLOCK,

        /**
         * A custom block was removed by BlockEngine break/removal logic.
         */
        REMOVE_CUSTOM_BLOCK,

        /**
         * A persisted custom block record was removed because the real world no
         * longer contained the expected backing block.
         */
        RECONCILE_STALE_BLOCK
    }
}
