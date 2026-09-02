package dev.auto.blockengine.api.event;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired after BlockEngine removes a custom block record.
 *
 * <p>This event covers removals from player breaks, explosions, API calls,
 * reconciliation, and direct plugin requests.</p>
 */
public class BlockEngineBlockRemovedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block block;
    private final @NotNull String blockId;
    private final @NotNull String stateId;
    private final @NotNull Reason reason;
    private final boolean droppedItem;

    /**
     * Creates a new custom block removal event.
     *
     * @param block Bukkit block whose custom record was removed
     * @param blockId full custom block id that was removed
     * @param stateId state id that was removed
     * @param reason removal reason
     * @param droppedItem true if BlockEngine dropped the custom block item
     */
    public BlockEngineBlockRemovedEvent(
            @NotNull Block block,
            @NotNull String blockId,
            @NotNull String stateId,
            @NotNull Reason reason,
            boolean droppedItem
    ) {
        this.block = Objects.requireNonNull(block, "block");
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        this.stateId = Objects.requireNonNull(stateId, "stateId");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.droppedItem = droppedItem;
    }

    /**
     * Returns the Bukkit block whose custom record was removed.
     *
     * @return affected block
     */
    public @NotNull Block block() {
        return block;
    }

    /**
     * Returns the full custom block id that was removed.
     *
     * @return full block id
     */
    public @NotNull String blockId() {
        return blockId;
    }

    /**
     * Returns the state id that was removed.
     *
     * @return state id
     */
    public @NotNull String stateId() {
        return stateId;
    }

    /**
     * Returns why the custom block was removed.
     *
     * @return removal reason
     */
    public @NotNull Reason reason() {
        return reason;
    }

    /**
     * Returns whether BlockEngine dropped the custom block item.
     *
     * @return true if an item was dropped
     */
    public boolean droppedItem() {
        return droppedItem;
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
     * Reason a custom block record was removed.
     */
    public enum Reason {
        /**
         * Removed because a player broke the block.
         */
        PLAYER_BREAK,
        /**
         * Removed because an explosion destroyed the block.
         */
        EXPLOSION,
        /**
         * Removed through the public world API.
         */
        API_CLEAR,
        /**
         * Removed because persisted data no longer matched the world.
         */
        RECONCILE_STALE,
        /**
         * Removed by direct plugin/runtime request.
         */
        PLUGIN_REQUEST
    }
}
