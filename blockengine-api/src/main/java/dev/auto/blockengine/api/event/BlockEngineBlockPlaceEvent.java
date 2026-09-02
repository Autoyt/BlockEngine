package dev.auto.blockengine.api.event;

import dev.auto.blockengine.api.blocks.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Fired before BlockEngine places or replaces a custom block record.
 *
 * <p>Cancel this event to stop the placement before BlockEngine updates its
 * stored custom block data, backing vanilla block, and managed displays.</p>
 */
public class BlockEngineBlockPlaceEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block block;
    private final @NotNull BlockDefinition definition;
    private final @Nullable Player player;
    private final @Nullable BlockFace placedAgainst;
    private final @NotNull String stateId;
    private final @Nullable String previousBlockId;
    private final @Nullable String previousStateId;
    private boolean cancelled;

    /**
     * Creates a new pre-placement event.
     *
     * @param block Bukkit block being placed into
     * @param definition custom block definition being placed
     * @param player placing player, or null for API/plugin placement
     * @param placedAgainst face the block was placed against, or null
     * @param stateId state id being placed
     * @param previousBlockId previous custom block id, or null
     * @param previousStateId previous state id, or null
     */
    public BlockEngineBlockPlaceEvent(
            @NotNull Block block,
            @NotNull BlockDefinition definition,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst,
            @NotNull String stateId,
            @Nullable String previousBlockId,
            @Nullable String previousStateId
    ) {
        this.block = Objects.requireNonNull(block, "block");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.player = player;
        this.placedAgainst = placedAgainst;
        this.stateId = Objects.requireNonNull(stateId, "stateId");
        this.previousBlockId = previousBlockId;
        this.previousStateId = previousStateId;
    }

    /**
     * Returns the Bukkit block being modified.
     *
     * @return affected block
     */
    public @NotNull Block block() {
        return block;
    }

    /**
     * Returns the custom block definition being placed.
     *
     * @return block definition
     */
    public @NotNull BlockDefinition definition() {
        return definition;
    }

    /**
     * Returns the full BlockEngine id being placed.
     *
     * @return full block id
     */
    public @NotNull String blockId() {
        return definition.id();
    }

    /**
     * Returns the player placing the block, if any.
     *
     * @return placing player, or null
     */
    public @Nullable Player player() {
        return player;
    }

    /**
     * Returns the face this block was placed against, if known.
     *
     * @return placed-against face, or null
     */
    public @Nullable BlockFace placedAgainst() {
        return placedAgainst;
    }

    /**
     * Returns the state id being placed.
     *
     * @return state id
     */
    public @NotNull String stateId() {
        return stateId;
    }

    /**
     * Returns the previous custom block id at this position, if one existed.
     *
     * @return previous block id, or null
     */
    public @Nullable String previousBlockId() {
        return previousBlockId;
    }

    /**
     * Returns the previous custom block state id at this position, if one
     * existed.
     *
     * @return previous state id, or null
     */
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

    /**
     * Returns the handler list for Bukkit's event system.
     *
     * @return event handler list
     */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
