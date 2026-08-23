package dev.auto.blockengine.api.event;

import dev.auto.blockengine.api.blocks.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired when BlockEngine detects a custom block should fall.
 */
public class BlockEngineGravityEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block startBlock;
    private final @NotNull BlockDefinition definition;
    private final @NotNull String stateId;
    private @NotNull Block stopBlock;
    private boolean cancelled;

    public BlockEngineGravityEvent(
            @NotNull Block startBlock,
            @NotNull Block stopBlock,
            @NotNull BlockDefinition definition,
            @NotNull String stateId
    ) {
        this.startBlock = Objects.requireNonNull(startBlock, "startBlock");
        this.stopBlock = Objects.requireNonNull(stopBlock, "stopBlock");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.stateId = Objects.requireNonNull(stateId, "stateId");
    }

    public @NotNull Block startBlock() {
        return startBlock;
    }

    public @NotNull Block stopBlock() {
        return stopBlock;
    }

    public void stopBlock(@NotNull Block stopBlock) {
        this.stopBlock = Objects.requireNonNull(stopBlock, "stopBlock");
    }

    public @NotNull BlockDefinition definition() {
        return definition;
    }

    public @NotNull String blockId() {
        return definition.id();
    }

    public @NotNull String stateId() {
        return stateId;
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
