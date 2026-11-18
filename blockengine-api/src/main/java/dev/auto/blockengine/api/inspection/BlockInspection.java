package dev.auto.blockengine.api.inspection;

import java.util.Objects;
import java.util.UUID;

/** Immutable diagnostic snapshot; does not retain a chunk, block, or mutable adapter data. */
public record BlockInspection(
        UUID worldId, String worldName, int x, int y, int z,
        Kind kind, String blockId, String stateId, String backingMaterial,
        int displayCount, boolean exposed, boolean unbreakable,
        float hardness, float miningSpeed, int payloadBytes
) {
    public enum Kind { VANILLA, CUSTOM, PLACEHOLDER }

    public BlockInspection {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(stateId, "stateId");
        Objects.requireNonNull(backingMaterial, "backingMaterial");
        if (displayCount < 0 || payloadBytes < 0) {
            throw new IllegalArgumentException("Diagnostic counts must not be negative");
        }
    }

    public int chunkX() { return x >> 4; }
    public int chunkZ() { return z >> 4; }
    public int localX() { return x & 15; }
    public int localZ() { return z & 15; }
}
