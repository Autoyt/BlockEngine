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

    /**
     * Creates a new post-break event.
     *
     * @param block Bukkit block that was broken
     * @param player player who broke the block
     * @param blockId full custom block id that was removed
     * @param stateId state id that was removed
     * @param droppedItem true if BlockEngine dropped the custom block item
     */
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

    /**
     * Returns the Bukkit block that was broken.
     *
     * @return affected block
     */
    public @NotNull Block block() {
        return block;
    }

    /**
     * Returns the player who broke the block.
     *
     * @return breaking player
     */
    public @NotNull Player player() {
        return player;
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
}
