package dev.auto.blockengine.flare;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class InspectorTextTest {
    @Test
    void padsNamesAndMarksTruncationWithoutLeakingPreviousTarget() {
        assertArrayEquals(new byte[]{'A', 'B', 0, 0, 0}, InspectorText.encode("ab", 5));
        assertEquals("MINECRA~", new String(InspectorText.encode("minecraft:stone", 8), StandardCharsets.US_ASCII));
        assertArrayEquals(new byte[5], InspectorText.encode(null, 5));
    }

    @Test
    void replacesUnsupportedCodepointsAndRejectsEmptyBuffers() {
        assertArrayEquals(new byte[]{'A', '?', 'B', 0}, InspectorText.encode("a😀b", 4));
        assertThrows(IllegalArgumentException.class, () -> InspectorText.encode("x", 0));
    }
}
