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
 */
public class BlockEngineBlockDataSaveEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block block;
    private final @NotNull BlockContext context;
    private final @Nullable String previousBlockId;
    private final @Nullable String previousStateId;
    private boolean cancelled;

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

    public @Nullable String previousBlockId() {
        return previousBlockId;
    }

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

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
