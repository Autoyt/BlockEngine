package dev.auto.blockengine.api.drops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FortuneTest {
    @Test
    void oreFortuneUsesTheExpectedWeightedMultipliers() {
        assertArrayEquals(new int[]{2, 2}, oreOutcomes(2, 0));
        assertArrayEquals(new int[]{2, 2, 4}, oreOutcomes(2, 1));
        assertArrayEquals(new int[]{2, 2, 4, 6}, oreOutcomes(2, 2));
        assertArrayEquals(new int[]{2, 2, 4, 6, 8}, oreOutcomes(2, 3));
    }

    @Test
    void uniformFortuneAddsToTheBaseCount() {
        assertArrayEquals(new int[]{5, 6, 7, 8}, new int[]{
                Fortune.bonusCount(5, 3, 0),
                Fortune.bonusCount(5, 3, 1),
                Fortune.bonusCount(5, 3, 2),
                Fortune.bonusCount(5, 3, 3)
        });
    }

    @Test
    void noFortuneAndUnenchantedToolsPreserveTheirCounts() {
        for (int base : new int[]{0, 1, 17, Integer.MAX_VALUE}) {
            assertEquals(base, Fortune.NONE.apply(base, 3));
            assertEquals(base, Fortune.ORE_MULTIPLIER.apply(base, 0));
            assertEquals(base, Fortune.UNIFORM_BONUS.apply(base, 0));
        }
    }

    @Test
    void oreFortuneDoesNotCreateItemsFromAZeroBase() {
        assertArrayEquals(new int[]{0, 0, 0, 0, 0}, oreOutcomes(0, 3));
    }

    @Test
    void invalidCountsLevelsAndRollsAreRejected() {
        for (Fortune fortune : Fortune.values()) {
            assertThrows(IllegalArgumentException.class, () -> fortune.apply(-1, 0));
            assertThrows(IllegalArgumentException.class, () -> fortune.apply(1, -1));
        }

        assertThrows(IllegalArgumentException.class, () -> Fortune.oreCount(1, 3, -1));
        assertThrows(IllegalArgumentException.class, () -> Fortune.oreCount(1, 3, 5));
        assertThrows(IllegalArgumentException.class, () -> Fortune.bonusCount(1, 3, -1));
        assertThrows(IllegalArgumentException.class, () -> Fortune.bonusCount(1, 3, 4));
    }

    @Test
    void highCountsAndLevelsCannotWrapAround() {
        assertEquals(Integer.MAX_VALUE, Fortune.oreCount(Integer.MAX_VALUE, 0, 1));
        assertEquals(Integer.MAX_VALUE, Fortune.bonusCount(Integer.MAX_VALUE - 1, 1, 1));
        assertEquals(0, Fortune.oreCount(0, Integer.MAX_VALUE, (long) Integer.MAX_VALUE + 1));

        assertThrows(ArithmeticException.class, () -> Fortune.oreCount(Integer.MAX_VALUE, 1, 2));
        assertThrows(ArithmeticException.class, () -> Fortune.oreCount(1, Integer.MAX_VALUE,
                (long) Integer.MAX_VALUE + 1));
        assertThrows(ArithmeticException.class, () -> Fortune.bonusCount(Integer.MAX_VALUE, 1, 1));
    }

    private static int[] oreOutcomes(int base, int level) {
        int[] outcomes = new int[level + 2];
        for (int roll = 0; roll < outcomes.length; roll++) {
            outcomes[roll] = Fortune.oreCount(base, level, roll);
        }
        return outcomes;
    }
}
