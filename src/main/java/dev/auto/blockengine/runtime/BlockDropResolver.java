package dev.auto.blockengine.runtime;

import dev.auto.blockengine.api.drops.BlockDrop;
import dev.auto.blockengine.api.drops.BlockDropContext;
import dev.auto.blockengine.api.event.BlockEngineBlockRemovedEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resolves and validates all player loot before any world mutation. */
public final class BlockDropResolver {
    // Bound the number of spawned entities even if a callback returns an enormous count.
    private static final int MAX_STACKS = 1024;

    private BlockDropResolver() {
    }

    public static @NotNull List<ItemStack> resolve(
            @NotNull BlockContext context,
            @NotNull ItemStack tool,
            @NotNull ItemStack selfItem
    ) {
        BlockDropContext dropContext = new BlockDropContext(
                context, tool, selfItem, BlockEngineBlockRemovedEvent.Reason.PLAYER_BREAK
        );
        return prepare(context.adapter().getDrops(dropContext));
    }

    public static @NotNull List<ItemStack> prepare(@NotNull List<BlockDrop> drops) {
        Objects.requireNonNull(drops, "drops");
        List<ItemStack> stacks = new ArrayList<>();

        for (BlockDrop drop : drops) {
            Objects.requireNonNull(drop, "drop entry");
            int remaining = drop.count();
            if (remaining == 0) {
                continue;
            }

            ItemStack item = drop.item();
            if (item.getType().isAir()) {
                throw new IllegalArgumentException("A nonzero drop must contain an item, not air");
            }
            int maximum = item.getMaxStackSize();
            if (maximum <= 0) {
                throw new IllegalArgumentException("Drop item has an invalid maximum stack size");
            }

            long requiredStacks = ((long) remaining + maximum - 1) / maximum;
            if (requiredStacks > MAX_STACKS - stacks.size()) {
                throw new IllegalArgumentException("A block break may produce at most " + MAX_STACKS + " item stacks");
            }

            while (remaining > 0) {
                ItemStack stack = item.clone();
                int amount = Math.min(remaining, maximum);
                stack.setAmount(amount);
                stacks.add(stack);
                remaining -= amount;
            }
        }

        return List.copyOf(stacks);
    }

    static @NotNull List<ItemStack> prepareItems(@NotNull List<ItemStack> stacks) {
        return prepare(stacks.stream()
                .map(item -> new BlockDrop(item, item.getAmount()))
                .toList());
    }
}
