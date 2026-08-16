package dev.auto.turtle.runtime;

import dev.auto.turtle.api.blocks.BlockAdapter;
import dev.auto.turtle.api.blocks.BlockContext;
import dev.auto.turtle.api.blocks.BlockData;
import dev.auto.turtle.api.blocks.BlockView;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class TurtleBlockContext extends BlockContext {
    private final @NotNull Block block;
    private final @Nullable Player player;

    public TurtleBlockContext(
            @NotNull BlockAdapter adapter,
            @NotNull BlockData data,
            @NotNull Block block,
            @Nullable Player player
    ) {
        super(adapter, data);
        this.block = block;
        this.player = player;
    }

    @Override
    public @Nullable Player player() {
        return player;
    }

    @Override
    public @NotNull BlockView relative(int dx, int dy, int dz) {
        return new TurtleBlockView(block.getRelative(dx, dy, dz));
    }

    @Override
    public @NotNull BlockView neighbor(@NotNull BlockFace face) {
        return new TurtleBlockView(block.getRelative(face));
    }

    @Override
    public @NotNull List<BlockView> neighbors() {
        return List.of(
                neighbor(BlockFace.NORTH),
                neighbor(BlockFace.SOUTH),
                neighbor(BlockFace.EAST),
                neighbor(BlockFace.WEST),
                neighbor(BlockFace.UP),
                neighbor(BlockFace.DOWN)
        );
    }

    @Override
    public @NotNull List<BlockView> horizontalNeighbors() {
        return List.of(
                neighbor(BlockFace.NORTH),
                neighbor(BlockFace.SOUTH),
                neighbor(BlockFace.EAST),
                neighbor(BlockFace.WEST)
        );
    }
}
