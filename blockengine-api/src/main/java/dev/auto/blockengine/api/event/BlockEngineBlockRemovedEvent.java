package dev.auto.blockengine.api.event;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired after BlockEngine removes a custom block record.
 */
public class BlockEngineBlockRemovedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block block;
    private final @NotNull String blockId;
    private final @NotNull String stateId;
    private final @NotNull Reason reason;
    private final boolean droppedItem;

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

    public @NotNull Block block() {
        return block;
    }

    public @NotNull String blockId() {
        return blockId;
    }

    public @NotNull String stateId() {
        return stateId;
    }

    public @NotNull Reason reason() {
        return reason;
    }

    public boolean droppedItem() {
        return droppedItem;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum Reason {
        PLAYER_BREAK,
        EXPLOSION,
        API_CLEAR,
        RECONCILE_STALE,
        PLUGIN_REQUEST
    }
}
