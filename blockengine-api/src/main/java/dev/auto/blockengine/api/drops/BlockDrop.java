package dev.auto.blockengine.api.drops;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * An item template and the total number of that item to drop.
 *
 * <p>The explicit count is independent of the template's stack amount. The engine
 * splits it into legal stacks when spawning items. Item metadata is preserved,
 * and the template is copied on input and output.</p>
 */
public final class BlockDrop {
    private final @NotNull ItemStack item;
    private final int count;

    /**
     * Creates a drop result.
     *
     * @param item full item template, including custom metadata
     * @param count nonnegative total count; zero produces no items
     * @throws IllegalArgumentException if the count is negative
     */
    public BlockDrop(@NotNull ItemStack item, int count) {
        Objects.requireNonNull(item, "item");
        if (count < 0) {
            throw new IllegalArgumentException("Drop count cannot be negative: " + count);
        }

        this.item = item.clone();
        this.count = count;
    }

    /**
     * Returns a copy of the full item template.
     *
     * @return independent item stack
     */
    public @NotNull ItemStack item() {
        return item.clone();
    }

    /**
     * Returns the total count, independent of the template's amount.
     *
     * @return nonnegative number of items
     */
    public int count() {
        return count;
    }
}
