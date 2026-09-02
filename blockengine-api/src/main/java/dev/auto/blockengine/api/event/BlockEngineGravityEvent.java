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
 *
 * <p>External listeners may cancel this event to keep the block in place, or
 * change {@link #stopBlock(Block)} before BlockEngine performs the move.</p>
 */
public class BlockEngineGravityEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block startBlock;
    private final @NotNull BlockDefinition definition;
    private final @NotNull String stateId;
    private @NotNull Block stopBlock;
    private boolean cancelled;

    /**
     * Creates a new gravity movement event.
     *
     * @param startBlock current Bukkit block position
     * @param stopBlock target Bukkit block position
     * @param definition custom block definition being moved
     * @param stateId state id being moved
     */
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

    /**
     * Returns the current Bukkit block position.
     *
     * @return starting block
     */
    public @NotNull Block startBlock() {
        return startBlock;
    }

    /**
     * Returns the target Bukkit block position.
     *
     * @return target block
     */
    public @NotNull Block stopBlock() {
        return stopBlock;
    }

    /**
     * Changes the target Bukkit block position.
     *
     * @param stopBlock target block
     */
    public void stopBlock(@NotNull Block stopBlock) {
        this.stopBlock = Objects.requireNonNull(stopBlock, "stopBlock");
    }

    /**
     * Returns the custom block definition being moved.
     *
     * @return block definition
     */
    public @NotNull BlockDefinition definition() {
        return definition;
    }

    /**
     * Returns the full custom block id being moved.
     *
     * @return full block id
     */
    public @NotNull String blockId() {
        return definition.id();
    }

    /**
     * Returns the state id being moved.
     *
     * @return state id
     */
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

    /**
     * Returns the handler list for Bukkit's event system.
     *
     * @return event handler list
     */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
