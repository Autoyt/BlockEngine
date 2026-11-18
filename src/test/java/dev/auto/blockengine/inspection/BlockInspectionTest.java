package dev.auto.blockengine.inspection;

import dev.auto.blockengine.api.inspection.BlockInspection;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BlockInspectionTest {
    @Test
    void signedCoordinatesProduceCorrectChunkAndLocalPositions() {
        var view = new BlockInspection(UUID.randomUUID(), "world", -17, -64, -1,
                BlockInspection.Kind.CUSTOM, "demo:test", "default", "minecraft:barrier",
                3, true, false, 1.5f, 2.0f, 42);

        assertEquals(-2, view.chunkX());
        assertEquals(-1, view.chunkZ());
        assertEquals(15, view.localX());
        assertEquals(15, view.localZ());
        assertEquals(42, view.payloadBytes());
    }

    @Test
    void rejectsInvalidInspectionData() {
        assertThrows(IllegalArgumentException.class, () -> new BlockInspection(UUID.randomUUID(), "world", 0, 0, 0,
                BlockInspection.Kind.CUSTOM, "demo:test", "default", "minecraft:barrier",
                -1, true, false, 1, 1, 0));
    }
}
