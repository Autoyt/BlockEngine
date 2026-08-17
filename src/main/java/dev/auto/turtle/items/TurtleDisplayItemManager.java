package dev.auto.turtle.items;

import dev.auto.turtle.registry.BlockRegistry;
import dev.auto.turtle.runtime.RuntimeBlockView;
import dev.auto.turtle.types.BlockDefinition;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public final class TurtleDisplayItemManager {
    private TurtleDisplayItemManager() {
    }

    public static @NotNull ItemStack create(@NotNull RuntimeBlockView block) {
        BlockDefinition definition = BlockRegistry.getBlock(block.storedBlock().blockId());
        if (definition == null) {
            return new ItemStack(block.displayMaterial());
        }
        return create(definition, block.storedBlock().stateId());
    }

    public static @NotNull ItemStack create(@NotNull BlockDefinition definition, @NotNull String stateId) {
        ItemStack stack = new ItemStack(definition.apiDefinition().item().material());
        ItemMeta meta = stack.getItemMeta();
        itemModel(meta, modelKey(definition, stateId));
        stack.setItemMeta(meta);
        return stack;
    }

    public static @NotNull NamespacedKey modelKey(@NotNull BlockDefinition definition, @NotNull String stateId) {
        return new NamespacedKey(definition.name().namespace(), "block/" + definition.name().name() + "/" + stateId);
    }

    static void itemModel(@NotNull ItemMeta meta, @NotNull NamespacedKey key) {
        meta.setItemModel(key);
    }
}
