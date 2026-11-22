package dev.auto.blockengine.api.drops;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DropTableValidationTest {
    @Test
    void rejectsInvalidFixedCountsAndRangesBeforeAnyBreak() {
        assertThrows(IllegalArgumentException.class, () -> table(entry -> entry.count(-1)));
        assertThrows(IllegalArgumentException.class, () -> table(entry -> entry.count(-1, 3)));
        assertThrows(IllegalArgumentException.class, () -> table(entry -> entry.count(3, 2)));

        assertDoesNotThrow(() -> table(entry -> entry.count(0)));
        assertDoesNotThrow(() -> table(entry -> entry.count(0, 0)));
        assertDoesNotThrow(() -> table(entry -> entry.count(Integer.MAX_VALUE, Integer.MAX_VALUE)));
    }

    @Test
    void rejectsInvalidProbabilities() {
        for (double chance : new double[]{-0.1, 1.1, Double.NaN,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> table(entry -> entry.chance(chance)));
        }

        assertDoesNotThrow(() -> table(entry -> entry.chance(0)));
        assertDoesNotThrow(() -> table(entry -> entry.chance(1)));
    }

    @Test
    void customCountsAndFortuneRulesAreAcceptedWithoutBeingRunAtRegistration() {
        assertDoesNotThrow(() -> table(entry -> entry
                .count(context -> {
                    throw new AssertionError("Dynamic count ran before removal.");
                })
                .fortune((count, level) -> {
                    throw new AssertionError("Fortune rule ran before removal.");
                })));
    }

    @Test
    void rejectsMissingSuppliersAndRules() {
        assertThrows(NullPointerException.class, () -> DropTable.builder().item(null, entry -> entry.count(1)));
        assertThrows(NullPointerException.class, () -> DropTable.builder().item(() -> null, null));
        assertThrows(NullPointerException.class, () -> table(entry -> entry.count(null)));
        assertThrows(NullPointerException.class, () -> table(entry -> entry.fortune(null)));
        assertThrows(NullPointerException.class, () -> DropTable.builder().build().roll(null));
    }

    private static DropTable table(Consumer<DropTable.EntryBuilder> configure) {
        return DropTable.builder().item(() -> {
            throw new AssertionError("Item supplier ran before removal.");
        }, configure).build();
    }
}
