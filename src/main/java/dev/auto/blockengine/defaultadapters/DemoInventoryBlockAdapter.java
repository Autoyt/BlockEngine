package dev.auto.blockengine.defaultadapters;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockContext;
import dev.auto.blockengine.api.blocks.BlockCreateContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public final class DemoInventoryBlockAdapter implements BlockAdapter {
    @Override
    public @NotNull String name() {
        return "demo_inventory";
    }

    @Override
    public void define(@NotNull BlockDefinition.Builder builder) {
        builder
                .defaultState("default")
                .item(item -> item
                        .name("<#2f6bff>Demo: Inventory")
                        .lore("<gray>Right-click to open a tiny demo inventory.")
                        .lore("<gray>Also persists an open counter in block data.")
                )
                .state("default", state -> state
                        .hardness(1.0f)
                        .miningSpeed(1.0f)
                        .dropsItem(true)
                        .textures(textures -> textures.all("blockengine_test:block/demo_inventory"))
                        .sounds(sounds -> sounds
                                .mining("minecraft:block.stone.hit")
                                .breakSound("minecraft:block.stone.break")
                                .place("minecraft:block.stone.place")
                        )
                );
    }

    @Override
    public @NotNull BlockData createDefaultData(@NotNull BlockCreateContext context) {
        BlockData data = context.createData();
        data.integer("opens", 0);
        return data;
    }

    @Override
    public boolean onInteract(@NotNull BlockContext context, @NotNull Player player) {
        int opens = context.data().integer("opens") == null ? 0 : context.data().integer("opens");
        context.data().integer("opens", opens + 1);

        Inventory inventory = Bukkit.createInventory(player, 9, "BlockEngine Demo Inventory");
        inventory.setItem(3, item(Material.CHEST, "Open count: " + (opens + 1)));
        inventory.setItem(5, item(Material.BLUE_STAINED_GLASS_PANE, "Demo storage slot"));
        player.openInventory(inventory);
        return true;
    }

    private static @NotNull ItemStack item(@NotNull Material material, @NotNull String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemName(name);
        stack.setItemMeta(meta);
        return stack;
    }
}
