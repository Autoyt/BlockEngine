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

    public @NotNull Block block() {
        return block;
    }

    public @NotNull BlockDefinition definition() {
        return definition;
    }

    public @NotNull BlockData data() {
        return data;
    }

    public @NotNull String blockId() {
        return data.blockId();
    }

    public @NotNull String stateId() {
        return data.stateId();
    }

    public @Nullable Player player() {
        return player;
    }

    public @Nullable BlockFace placedAgainst() {
        return placedAgainst;
    }

    public @Nullable String previousBlockId() {
        return previousBlockId;
    }

    public @Nullable String previousStateId() {
        return previousStateId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
