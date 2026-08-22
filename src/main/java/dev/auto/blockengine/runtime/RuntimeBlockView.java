package dev.auto.blockengine.runtime;

import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public record RuntimeBlockView(
        @NotNull ChunkEngine.Key chunkKey,
        @NotNull BlockLocationKey location,
        @NotNull Material displayMaterial,
        @NotNull ChunkEngine.StoredBlock storedBlock,
        boolean exposed
) {
}
