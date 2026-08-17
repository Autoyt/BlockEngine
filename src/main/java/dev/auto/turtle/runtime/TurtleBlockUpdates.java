package dev.auto.turtle.runtime;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class TurtleBlockUpdates {
    private static final List<BlockFace> FACES = List.of(
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
            BlockFace.UP,
            BlockFace.DOWN
    );

    private TurtleBlockUpdates() {
    }

    public static void update(@NotNull Block block) {
        poke(block);
        for (BlockFace face : FACES) {
            poke(block.getRelative(face));
        }
    }

    private static void poke(@NotNull Block block) {
        block.setBlockData(block.getBlockData(), true);
    }
}
