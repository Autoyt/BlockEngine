package dev.auto.blockengine.runtime;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockData;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BlockContext extends dev.auto.blockengine.api.blocks.BlockContext {
    private final @NotNull Block block;
    private final @Nullable Player player;
    private final @Nullable ChunkEngine.StoredBlock source;

    public BlockContext(
            @NotNull BlockAdapter adapter,
            @NotNull BlockData data,
            @NotNull Block block,
            @Nullable Player player
    ) {
        this(adapter, data, block, player, null);
    }

    BlockContext(
            @NotNull BlockAdapter adapter, @NotNull BlockData data, @NotNull Block block,
            @Nullable Player player, @Nullable ChunkEngine.StoredBlock source
    ) {
        super(adapter, data);
        this.source = source;
        this.block = block;
        this.player = player;
    }

    boolean matches(@Nullable RuntimeBlockView view) {
        return view != null && (source == null || source.data() == view.storedBlock().data());
    }

    @Override
    public @Nullable Player player() {
        return player;
    }

    @Override
    public @NotNull dev.auto.blockengine.api.blocks.BlockView relative(int dx, int dy, int dz) {
        return new BlockView(block.getRelative(dx, dy, dz));
    }

    @Override
    public @NotNull dev.auto.blockengine.api.blocks.BlockView neighbor(@NotNull BlockFace face) {
        return new BlockView(block.getRelative(face));
    }

    @Override
    public @NotNull List<dev.auto.blockengine.api.blocks.BlockView> neighbors() {
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
    public @NotNull List<dev.auto.blockengine.api.blocks.BlockView> horizontalNeighbors() {
        return List.of(
                neighbor(BlockFace.NORTH),
                neighbor(BlockFace.SOUTH),
                neighbor(BlockFace.EAST),
                neighbor(BlockFace.WEST)
        );
    }
}
