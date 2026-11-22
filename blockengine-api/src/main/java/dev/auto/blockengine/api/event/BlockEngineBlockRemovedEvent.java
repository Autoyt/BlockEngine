package dev.auto.blockengine.api.event;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
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
    private final @NotNull List<ItemStack> drops;

    /**
     * Creates a new custom block removal event.
     *
     * <p>This compatibility constructor preserves the supplied boolean but has
     * no detailed item snapshot, so {@link #drops()} returns an empty list.</p>
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
        this.drops = List.of();
    }

    /**
     * Creates a removal event with a snapshot of the items actually spawned.
     *
     * <p>The list and its stacks are copied. {@link #droppedItem()} is true
     * exactly when this snapshot is nonempty.</p>
     *
     * @param block Bukkit block whose custom record was removed
     * @param blockId full custom block id that was removed
     * @param stateId state id that was removed
     * @param reason removal reason
     * @param drops stacks from surviving spawned item entities
     */
    public BlockEngineBlockRemovedEvent(
            @NotNull Block block,
            @NotNull String blockId,
            @NotNull String stateId,
            @NotNull Reason reason,
            @NotNull List<ItemStack> drops
    ) {
        this.block = Objects.requireNonNull(block, "block");
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        this.stateId = Objects.requireNonNull(stateId, "stateId");
        this.reason = Objects.requireNonNull(reason, "reason");

        this.drops = Objects.requireNonNull(drops, "drops").stream()
                .map(item -> Objects.requireNonNull(item, "drop").clone())
                .toList();
        this.droppedItem = !this.drops.isEmpty();
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
     * Returns whether BlockEngine spawned any item drops.
     *
     * <p>Events created with the compatibility boolean constructor return the
     * caller's supplied flag even though no detailed snapshot is available.</p>
     *
     * @return true if an item was dropped
     */
    public boolean droppedItem() {
        return droppedItem;
    }

    /**
     * Returns a snapshot of the items actually spawned by this removal.
     *
     * <p>Each call returns an unmodifiable list of fresh stack copies. Changing
     * a returned stack cannot affect the event or the world's item entities.
     * The compatibility boolean constructor provides no item details and
     * always returns an empty list here.</p>
     *
     * @return copied spawned item stacks
     */
    public @NotNull List<ItemStack> drops() {
        return drops.stream().map(ItemStack::clone).toList();
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
