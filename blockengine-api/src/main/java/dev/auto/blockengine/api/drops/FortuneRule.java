package dev.auto.blockengine.api.drops;

/**
 * Computes a final item count from a base count and an effective Fortune level.
 *
 * <p>Use a {@link Fortune} preset or supply a lambda for custom progression.
 * Implementations must return a nonnegative count and must not silently overflow.</p>
 */
@FunctionalInterface
public interface FortuneRule {
    /**
     * Applies the Fortune rule to one independently rolled drop entry.
     *
     * @param baseCount nonnegative count before Fortune
     * @param fortuneLevel nonnegative effective Fortune level
     * @return nonnegative final count
     */
    int apply(int baseCount, int fortuneLevel);
}
