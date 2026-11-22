package dev.auto.blockengine.api.drops;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Reusable drop entries with independent chances, counts, and Fortune rules.
 *
 * <p>Each successful entry computes its base count, then applies its Fortune
 * rule. Zero final counts are omitted. Item suppliers run only when an entry
 * produces a positive count, and returned items are defensively copied.</p>
 */
public final class DropTable {
    private final @NotNull List<Entry> entries;
    private final boolean silkTouchSelf;

    private DropTable(@NotNull List<Entry> entries, boolean silkTouchSelf) {
        this.entries = List.copyOf(entries);
        this.silkTouchSelf = silkTouchSelf;
    }

    /** @return a builder for a new drop table */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Resolves all entries once for the supplied removal context.
     *
     * <p>When configured, Silk Touch returns one self item before evaluating any
     * normal entry, including its supplier, chance, count, or Fortune rule.</p>
     *
     * @param context inputs captured for this removal
     * @return immutable list of independently resolved drops
     * @throws IllegalArgumentException if a callback produces a negative count
     * @throws ArithmeticException if a built-in count calculation overflows
     */
    public @NotNull List<BlockDrop> roll(@NotNull BlockDropContext context) {
        Objects.requireNonNull(context, "context");
        if (silkTouchSelf && context.silkTouch()) {
            return List.of(context.selfDrop());
        }

        List<BlockDrop> drops = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.chance == 0 || (entry.chance < 1
                    && ThreadLocalRandom.current().nextDouble() >= entry.chance)) {
                continue;
            }

            int baseCount = requireCount(entry.count.applyAsInt(context));
            int finalCount = requireCount(entry.fortune.apply(baseCount, context.fortuneLevel()));
            if (finalCount == 0) {
                continue;
            }

            ItemStack item = Objects.requireNonNull(entry.item.get(), "Drop item supplier returned null");
            drops.add(new BlockDrop(item, finalCount));
        }

        return List.copyOf(drops);
    }

    private static int requireCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Drop count cannot be negative: " + count);
        }
        return count;
    }

    private record Entry(
            @NotNull Supplier<ItemStack> item,
            @NotNull ToIntFunction<BlockDropContext> count,
            double chance,
            @NotNull FortuneRule fortune
    ) {
    }

    /** Builds an immutable table of independent drop entries. */
    public static final class Builder {
        private final @NotNull List<Entry> entries = new ArrayList<>();
        private boolean silkTouchSelf;

        private Builder() {
        }

        /**
         * Adds one independently rolled item entry.
         *
         * @param item supplies the full item template when a positive count is rolled
         * @param configure configures count, chance, and Fortune for this entry
         * @return this builder
         */
        public @NotNull Builder item(
                @NotNull Supplier<ItemStack> item,
                @NotNull Consumer<EntryBuilder> configure
        ) {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(configure, "configure");

            EntryBuilder entry = new EntryBuilder();
            configure.accept(entry);
            entries.add(new Entry(item, entry.count, entry.chance, entry.fortune));
            return this;
        }

        /**
         * Makes Silk Touch return one self item instead of normal table entries.
         *
         * @return this builder
         */
        public @NotNull Builder silkTouchSelf() {
            silkTouchSelf = true;
            return this;
        }

        /** @return immutable table using the entries configured so far */
        public @NotNull DropTable build() {
            return new DropTable(entries, silkTouchSelf);
        }
    }

    /** Configures one entry; defaults are count 1, chance 1, and no Fortune. */
    public static final class EntryBuilder {
        private @NotNull ToIntFunction<BlockDropContext> count = context -> 1;
        private double chance = 1;
        private @NotNull FortuneRule fortune = Fortune.NONE;

        private EntryBuilder() {
        }

        /**
         * Sets a constant base count before Fortune.
         *
         * @param count nonnegative base count
         * @return this builder
         */
        public @NotNull EntryBuilder count(int count) {
            requireCount(count);
            this.count = context -> count;
            return this;
        }

        /**
         * Sets a uniformly sampled base count with inclusive bounds.
         *
         * @param min nonnegative minimum count
         * @param max maximum count, at least the minimum
         * @return this builder
         */
        public @NotNull EntryBuilder count(int min, int max) {
            requireCount(min);
            if (max < min) {
                throw new IllegalArgumentException("Maximum count must be at least the minimum count.");
            }

            this.count = context -> (int) ThreadLocalRandom.current().nextLong(min, (long) max + 1);
            return this;
        }

        /**
         * Sets a dynamic base count evaluated once when this entry's chance succeeds.
         *
         * @param count callback returning a nonnegative count from the removal context
         * @return this builder
         */
        public @NotNull EntryBuilder count(@NotNull ToIntFunction<BlockDropContext> count) {
            this.count = Objects.requireNonNull(count, "count");
            return this;
        }

        /**
         * Sets this entry's independent probability of being evaluated.
         *
         * @param chance finite probability from zero through one, inclusive
         * @return this builder
         */
        public @NotNull EntryBuilder chance(double chance) {
            if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
                throw new IllegalArgumentException("Drop chance must be a finite value between zero and one.");
            }

            this.chance = chance;
            return this;
        }

        /**
         * Sets the rule applied after computing this entry's base count.
         *
         * @param fortune built-in preset or custom count rule
         * @return this builder
         */
        public @NotNull EntryBuilder fortune(@NotNull FortuneRule fortune) {
            this.fortune = Objects.requireNonNull(fortune, "fortune");
            return this;
        }
    }
}
