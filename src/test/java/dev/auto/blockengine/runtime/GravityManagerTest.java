package dev.auto.blockengine.runtime;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GravityManagerTest {
    @Test
    void interpolatesTheLandingAcrossTheOriginalFallTime() {
        assertEquals(7, GravityManager.fallDuration(1));
        assertEquals(80, GravityManager.fallDuration(500));
        assertTrue(GravityManager.fallDuration(20) > GravityManager.fallDuration(5));
    }

    @Test
    void recognizesOneFullCube() {
        assertTrue(GravityManager.isFullBlockCollision(List.of(
                new BoundingBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        )));
    }

    @Test
    void rejectsHalfHeightCollision() {
        assertFalse(GravityManager.isFullBlockCollision(List.of(
                new BoundingBox(0.0, 0.0, 0.0, 1.0, 0.5, 1.0)
        )));
    }

    @Test
    void rejectsMultipartAndEmptyCollision() {
        assertFalse(GravityManager.isFullBlockCollision(List.of(
                new BoundingBox(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
                new BoundingBox(0.0, 0.5, 0.0, 0.5, 1.0, 1.0)
        )));
        assertFalse(GravityManager.isFullBlockCollision(List.of()));
    }
}
