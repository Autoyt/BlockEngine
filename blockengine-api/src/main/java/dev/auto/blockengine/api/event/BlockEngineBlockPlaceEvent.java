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

    public @NotNull Block block() {
        return block;
    }

    public @NotNull BlockDefinition definition() {
        return definition;
    }

    public @NotNull String blockId() {
        return definition.id();
    }

    public @Nullable Player player() {
        return player;
    }

    public @Nullable BlockFace placedAgainst() {
        return placedAgainst;
    }

    public @NotNull String stateId() {
        return stateId;
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
