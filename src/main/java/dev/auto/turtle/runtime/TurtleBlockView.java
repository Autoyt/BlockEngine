package dev.auto.turtle.runtime;

import dev.auto.turtle.api.blocks.BlockView;
import dev.auto.turtle.types.BlockLocationKey;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TurtleBlockView implements BlockView {
    private final @NotNull Block block;
    private final @Nullable RuntimeBlockView turtleBlock;

    public TurtleBlockView(@NotNull Block block) {
        this.block = block;
        this.turtleBlock = TurtleChunkRuntime.getBlock(new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
    }

    @Override
    public int x() {
        return block.getX();
    }

    @Override
    public int y() {
        return block.getY();
    }

    @Override
    public int z() {
        return block.getZ();
    }

    @Override
    public @NotNull Material material() {
        return block.getType();
    }

    @Override
    public boolean isAir() {
        return block.getType().isAir();
    }

    @Override
    public boolean isSolid() {
        return block.getType().isSolid();
    }

    @Override
    public boolean isLiquid() {
        return block.isLiquid();
    }

    @Override
    public boolean isReplaceable() {
        return block.isReplaceable();
    }

    @Override
    public boolean isPassable() {
        return block.isPassable();
    }

    @Override
    public boolean isTurtleBlock() {
        return turtleBlock != null;
    }

    @Override
    public @Nullable String turtleBlockId() {
        return turtleBlock == null ? null : turtleBlock.storedBlock().blockId();
    }

    @Override
    public @Nullable String turtleStateId() {
        return turtleBlock == null ? null : turtleBlock.storedBlock().stateId();
    }
}
