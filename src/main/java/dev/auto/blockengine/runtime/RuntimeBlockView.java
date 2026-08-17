package dev.auto.blockengine.runtime;

import dev.auto.blockengine.pdc.BlockEngineChunkData;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.types.ChunkKey;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public record RuntimeBlockView(
        @NotNull ChunkKey chunkKey,
        @NotNull BlockLocationKey location,
        @NotNull Material displayMaterial,
        @NotNull BlockEngineChunkData.StoredBlock storedBlock,
        boolean exposed
) {
}
