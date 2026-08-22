package dev.auto.blockengine.runtime;

import dev.auto.blockengine.api.blocks.BlockView;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BlockEngineBlockView implements BlockView {
    private final @NotNull Block block;
    private final @Nullable RuntimeBlockView blockEngineBlock;

    public BlockEngineBlockView(@NotNull Block block) {
        this.block = block;
        this.blockEngineBlock = ChunkEngine.getBlock(new BlockLocationKey(
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
    public boolean isBlockEngineBlock() {
        return blockEngineBlock != null;
    }

    @Override
    public @Nullable String blockEngineBlockId() {
        return blockEngineBlock == null ? null : blockEngineBlock.storedBlock().blockId();
    }

    @Override
    public @Nullable String blockEngineStateId() {
        return blockEngineBlock == null ? null : blockEngineBlock.storedBlock().stateId();
    }
}
