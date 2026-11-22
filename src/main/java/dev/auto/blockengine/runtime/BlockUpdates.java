package dev.auto.blockengine.runtime;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class BlockUpdates {
    private static final List<BlockFace> FACES = List.of(
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
            BlockFace.UP,
            BlockFace.DOWN
    );

    private BlockUpdates() {
    }

    public static void update(@NotNull Block block) {
        poke(block);
        for (BlockFace face : FACES) {
            int x = block.getX() + face.getModX();
            int y = block.getY() + face.getModY();
            int z = block.getZ() + face.getModZ();
            if (y >= block.getWorld().getMinHeight() && y < block.getWorld().getMaxHeight()
                    && block.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
                poke(block.getRelative(face));
            }
        }
    }

    private static void poke(@NotNull Block block) {
        block.setBlockData(block.getBlockData(), true);
    }
}
