package dev.auto.blockengine.api.event;

import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Fired after BlockEngine has placed or replaced a custom block record.
 *
 * <p>At this point the custom block data exists and listeners can inspect the
 * created mutable data object.</p>
 */
public class BlockEngineBlockPlacedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block block;
    private final @NotNull BlockDefinition definition;
    private final @NotNull BlockData data;
    private final @Nullable Player player;
    private final @Nullable BlockFace placedAgainst;
    private final @Nullable String previousBlockId;
    private final @Nullable String previousStateId;

    /**
     * Creates a new post-placement event.
     *
     * @param block Bukkit block that was modified
     * @param definition custom block definition that was placed
     * @param data mutable data for the placed block
     * @param player placing player, or null for API/plugin placement
     * @param placedAgainst face the block was placed against, or null
     * @param previousBlockId previous custom block id, or null
     * @param previousStateId previous state id, or null
     */
    public BlockEngineBlockPlacedEvent(
            @NotNull Block block,
            @NotNull BlockDefinition definition,
            @NotNull BlockData data,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst,
            @Nullable String previousBlockId,
            @Nullable String previousStateId
    ) {
        this.block = Objects.requireNonNull(block, "block");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.data = Objects.requireNonNull(data, "data");
        this.player = player;
        this.placedAgainst = placedAgainst;
        this.previousBlockId = previousBlockId;
        this.previousStateId = previousStateId;
    }

    /**
     * Returns the Bukkit block that was modified.
     *
     * @return affected block
     */
    public @NotNull Block block() {
        return block;
    }

    /**
     * Returns the custom block definition that was placed.
     *
     * @return block definition
     */
    public @NotNull BlockDefinition definition() {
        return definition;
    }

    /**
     * Returns the mutable data created for the placed block.
     *
     * @return placed-block data
     */
    public @NotNull BlockData data() {
        return data;
    }

    /**
     * Returns the full BlockEngine id that was placed.
     *
     * @return full block id
     */
    public @NotNull String blockId() {
        return data.blockId();
    }

    /**
     * Returns the state id that was placed.
     *
     * @return state id
     */
    public @NotNull String stateId() {
        return data.stateId();
    }

    /**
     * Returns the player that placed the block, if any.
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
