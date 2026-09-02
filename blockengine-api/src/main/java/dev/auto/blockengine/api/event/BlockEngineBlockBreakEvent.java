package dev.auto.blockengine.api.event;

import dev.auto.blockengine.api.blocks.BlockContext;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired before a player breaks a BlockEngine custom block.
 *
 * <p>Cancel this event to stop BlockEngine's custom break handling for the
 * block.</p>
 */
public class BlockEngineBlockBreakEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block block;
    private final @NotNull BlockContext context;
    private final @NotNull Player player;
    private boolean cancelled;

    /**
     * Creates a new pre-break event.
     *
     * @param block Bukkit block being broken
     * @param context custom block context
     * @param player player breaking the block
     */
    public BlockEngineBlockBreakEvent(
            @NotNull Block block,
            @NotNull BlockContext context,
            @NotNull Player player
    ) {
        this.block = Objects.requireNonNull(block, "block");
        this.context = Objects.requireNonNull(context, "context");
        this.player = Objects.requireNonNull(player, "player");
    }

    /**
     * Returns the Bukkit block being broken.
     *
     * @return affected block
     */
    public @NotNull Block block() {
        return block;
    }

    /**
     * Returns the custom block context for the break action.
     *
     * @return block context
     */
    public @NotNull BlockContext context() {
        return context;
    }

    /**
     * Returns the full BlockEngine id being broken.
     *
     * @return full block id
     */
    public @NotNull String blockId() {
        return context.blockId();
    }

    /**
     * Returns the state id being broken.
     *
     * @return state id
     */
    public @NotNull String stateId() {
        return context.stateId();
    }

    /**
     * Returns the player breaking the block.
     *
     * @return breaking player
     */
    public @NotNull Player player() {
        return player;
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
