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
 */
public class BlockEngineBlockBreakEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block block;
    private final @NotNull BlockContext context;
    private final @NotNull Player player;
    private boolean cancelled;

    public BlockEngineBlockBreakEvent(
            @NotNull Block block,
            @NotNull BlockContext context,
            @NotNull Player player
    ) {
        this.block = Objects.requireNonNull(block, "block");
        this.context = Objects.requireNonNull(context, "context");
        this.player = Objects.requireNonNull(player, "player");
    }

    public @NotNull Block block() {
        return block;
    }

    public @NotNull BlockContext context() {
        return context;
    }

    public @NotNull String blockId() {
        return context.blockId();
    }

    public @NotNull String stateId() {
        return context.stateId();
    }

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

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
