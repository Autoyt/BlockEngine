package dev.auto.turtle.runtime;

import dev.auto.turtle.api.blocks.BlockCreateContext;
import dev.auto.turtle.api.blocks.BlockData;
import dev.auto.turtle.pdc.TurtleChunkData;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TurtleCreateContext implements BlockCreateContext {
    private final @NotNull Location location;
    private final @Nullable Player player;
    private final @Nullable BlockFace placedAgainst;
    private final @NotNull String blockId;
    private final @NotNull String defaultState;

    public TurtleCreateContext(
            @NotNull Location location,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst,
            @NotNull String blockId,
            @NotNull String defaultState
    ) {
        this.location = location.clone();
        this.player = player;
        this.placedAgainst = placedAgainst;
        this.blockId = blockId;
        this.defaultState = defaultState;
    }

    @Override
    public @NotNull Location location() {
        return location.clone();
    }

    @Override
    public @Nullable Player player() {
        return player;
    }

    @Override
    public @Nullable BlockFace placedAgainst() {
        return placedAgainst;
    }

    @Override
    public @NotNull String blockId() {
        return blockId;
    }

    @Override
    public @NotNull BlockData createData() {
        return new TurtleChunkData.SimpleBlockData(blockId, defaultState);
    }
}
