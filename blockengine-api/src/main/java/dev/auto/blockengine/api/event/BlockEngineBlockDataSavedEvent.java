package dev.auto.blockengine.api.event;

import dev.auto.blockengine.api.blocks.BlockData;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Fired after BlockEngine saves mutable custom block data back to pending chunk storage.
 */
public class BlockEngineBlockDataSavedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Block block;
    private final @NotNull BlockData data;
    private final @Nullable String previousBlockId;
    private final @Nullable String previousStateId;

    public BlockEngineBlockDataSavedEvent(
            @NotNull Block block,
            @NotNull BlockData data,
            @Nullable String previousBlockId,
            @Nullable String previousStateId
    ) {
        this.block = Objects.requireNonNull(block, "block");
        this.data = Objects.requireNonNull(data, "data");
        this.previousBlockId = previousBlockId;
        this.previousStateId = previousStateId;
    }

    public @NotNull Block block() {
        return block;
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
