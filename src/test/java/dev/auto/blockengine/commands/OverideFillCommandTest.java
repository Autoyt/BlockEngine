package dev.auto.blockengine.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverideFillCommandTest {
    @Test
    void countsBothEndsOfTheFillAndAcceptsTheLimit() {
        assertTrue(OverideFillCommand.withinFillLimit(-16, 15, 0, 31, -1, 30));
        assertFalse(OverideFillCommand.withinFillLimit(-16, 16, 0, 31, -1, 30));
    }

    @Test
    void rejectsAnOversizedFillEvenWhenItsVolumeWouldOverflow() {
        assertFalse(OverideFillCommand.withinFillLimit(
                Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 1, 0, 1));
        assertFalse(OverideFillCommand.withinFillLimit(
                -2_000_000, 2_000_000, -2_000_000, 2_000_000, -2_000_000, 2_000_000));
    }
}
