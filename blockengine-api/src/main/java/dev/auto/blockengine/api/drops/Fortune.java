package dev.auto.blockengine.api.drops;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Built-in Fortune count rules. Random choices are managed by BlockEngine.
 *
 * <p>All rules reject negative inputs and throw {@link ArithmeticException} when
 * the resulting count cannot be represented as an integer.</p>
 */
public enum Fortune implements FortuneRule {
    /** Leaves the base count unchanged. */
    NONE {
        @Override
        public int apply(int baseCount, int fortuneLevel) {
            validateInputs(baseCount, fortuneLevel);
            return baseCount;
        }
    },

    /**
     * Multiplies the base count by {@code max(1, roll)}, where {@code roll} is
     * uniformly selected from zero through {@code fortuneLevel + 1}, inclusive.
     * At Fortune III, the multipliers are 1, 1, 2, 3, and 4 with equal chances.
     */
    ORE_MULTIPLIER {
        @Override
        public int apply(int baseCount, int fortuneLevel) {
            validateInputs(baseCount, fortuneLevel);
            long roll = ThreadLocalRandom.current().nextLong((long) fortuneLevel + 2);
            return oreCount(baseCount, fortuneLevel, roll);
        }
    },

    /** Adds a uniformly selected integer from zero through the Fortune level. */
    UNIFORM_BONUS {
        @Override
        public int apply(int baseCount, int fortuneLevel) {
            validateInputs(baseCount, fortuneLevel);
            long bonus = ThreadLocalRandom.current().nextLong((long) fortuneLevel + 1);
            return bonusCount(baseCount, fortuneLevel, bonus);
        }
    };

    static int oreCount(int baseCount, int fortuneLevel, long roll) {
        validateInputs(baseCount, fortuneLevel);
        if (roll < 0 || roll > (long) fortuneLevel + 1) {
            throw new IllegalArgumentException("Ore roll is outside the Fortune range: " + roll);
        }

        return Math.toIntExact((long) baseCount * Math.max(1L, roll));
    }

    static int bonusCount(int baseCount, int fortuneLevel, long bonus) {
        validateInputs(baseCount, fortuneLevel);
        if (bonus < 0 || bonus > fortuneLevel) {
            throw new IllegalArgumentException("Bonus is outside the Fortune range: " + bonus);
        }

        return Math.toIntExact((long) baseCount + bonus);
    }

    private static void validateInputs(int baseCount, int fortuneLevel) {
        if (baseCount < 0) {
            throw new IllegalArgumentException("Base count cannot be negative: " + baseCount);
        }
        if (fortuneLevel < 0) {
            throw new IllegalArgumentException("Fortune level cannot be negative: " + fortuneLevel);
        }
    }
}
