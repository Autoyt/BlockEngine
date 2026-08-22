package dev.auto.blockengine.api.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired after a player breaks and BlockEngine removes a custom block.
 */
public class BlockEngineBlockBrokenEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block block;
    private final @NotNull Player player;
    private final @NotNull String blockId;
    private final @NotNull String stateId;
    private final boolean droppedItem;

    public BlockEngineBlockBrokenEvent(
            @NotNull Block block,
            @NotNull Player player,
            @NotNull String blockId,
            @NotNull String stateId,
            boolean droppedItem
    ) {
        this.block = Objects.requireNonNull(block, "block");
        this.player = Objects.requireNonNull(player, "player");
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        this.stateId = Objects.requireNonNull(stateId, "stateId");
        this.droppedItem = droppedItem;
    }

    public @NotNull Block block() {
        return block;
    }

    public @NotNull Player player() {
        return player;
    }

    public @NotNull String blockId() {
        return blockId;
    }

    public @NotNull String stateId() {
        return stateId;
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
}
