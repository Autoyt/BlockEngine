package dev.auto.turtle.types;

import org.bukkit.Chunk;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record ChunkKey(@NotNull UUID worldId, int x, int z) {
    public static @NotNull ChunkKey from(@NotNull Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    public boolean contains(@NotNull BlockLocationKey block) {
        return worldId.equals(block.worldId()) && (block.x() >> 4) == x && (block.z() >> 4) == z;
    }
}
