package dev.auto.turtle.runtime;

import dev.auto.turtle.pdc.TurtleChunkData;
import dev.auto.turtle.types.BlockLocationKey;
import dev.auto.turtle.types.ChunkKey;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public record RuntimeBlockView(
        @NotNull ChunkKey chunkKey,
        @NotNull BlockLocationKey location,
        @NotNull Material displayMaterial,
        @NotNull TurtleChunkData.StoredBlock storedBlock,
        boolean exposed
) {
}
