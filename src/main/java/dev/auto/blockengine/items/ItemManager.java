package dev.auto.blockengine.items;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.creative.BlockDisplayNames;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ItemManager {
    private static final NamespacedKey BLOCK_ID_KEY = new NamespacedKey(Main.getInstance(), "block_id");
    private static final NamespacedKey STATE_ID_KEY = new NamespacedKey(Main.getInstance(), "state_id");
    private static final NamespacedKey SUDO_BLOCK_ID_KEY = new NamespacedKey(Main.getInstance(), "sudo_block_id");
    private static final NamespacedKey SUDO_STATE_ID_KEY = new NamespacedKey(Main.getInstance(), "sudo_state_id");
    private static final NamespacedKey WAND_KEY = new NamespacedKey(Main.getInstance(), "block_engine_wand");

    private ItemManager() {
    }

    public static @NotNull ItemStack create(@NotNull BlockDefinition block) {
        return create(block, block.apiDefinition().defaultState());
    }

    public static @NotNull ItemStack create(@NotNull BlockDefinition block, @NotNull String stateId) {
        block.apiDefinition().state(stateId);
        var item = block.apiDefinition().item();
        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(BLOCK_ID_KEY, PersistentDataType.STRING, block.id());
        meta.getPersistentDataContainer().set(STATE_ID_KEY, PersistentDataType.STRING, stateId);
        itemModel(meta, modelKey(
                block,
                stateId
        ));

        meta.displayName(BlockDisplayNames.itemName(item.name(), block));
        if (!item.lore().isEmpty()) {
            meta.lore(item.lore().stream().map(BlockDisplayNames::rich).toList());
        }
        if (item.glint()) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.setMaxStackSize(64);

        stack.setItemMeta(meta);
        return stack;
    }

    public static @NotNull ItemStack createSudo(@NotNull BlockDefinition block) {
        return createSudo(block, block.apiDefinition().defaultState());
    }

    public static @NotNull ItemStack createSudo(@NotNull BlockDefinition block, @Nullable String stateId) {
        String resolvedState = stateId == null || stateId.isBlank() ? block.apiDefinition().defaultState() : stateId;
        block.apiDefinition().state(resolvedState);

        ItemStack stack = new ItemStack(Material.CHEST);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(SUDO_BLOCK_ID_KEY, PersistentDataType.STRING, block.id());
        meta.getPersistentDataContainer().set(SUDO_STATE_ID_KEY, PersistentDataType.STRING, resolvedState);
        itemModel(meta, modelKey(block, resolvedState));
        meta.displayName(Component.text("Sudo: ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(BlockDisplayNames.itemName(block.apiDefinition().item().name(), block)
                        .decoration(TextDecoration.BOLD, false)));
        meta.lore(java.util.List.of(
                Component.text("[!] SUDO BLOCK", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text()
                        .append(Component.text("! ", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.text("Structure-building placeholder only.", NamedTextColor.GRAY))
                        .build(),
                Component.text()
                        .append(Component.text("! ", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.text("Not a real BlockEngine block.", NamedTextColor.GRAY))
                        .build(),
                Component.text()
                        .append(Component.text("! ", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.text("Saved structures convert it during generation.", NamedTextColor.GRAY))
                        .build(),
                Component.text("Block: " + block.id(), NamedTextColor.DARK_GRAY),
                Component.text("State: " + resolvedState, NamedTextColor.DARK_GRAY)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(64);
        stack.setItemMeta(meta);
        return stack;
    }

    public static @NotNull ItemStack createWand() {
        ItemStack stack = new ItemStack(Material.STICK);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.BOOLEAN, true);
        itemModel(meta, wandModelKey());
        meta.displayName(Component.text("Block Engine Wand", NamedTextColor.GOLD, TextDecoration.BOLD));
        meta.lore(java.util.List.of(
                Component.text("Structure building tool", NamedTextColor.YELLOW),
                Component.text("Click sudo blocks to toggle previews.", NamedTextColor.GRAY),
                Component.text("Click custom blocks to convert them to sudo blocks.", NamedTextColor.GRAY)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        stack.setItemMeta(meta);
        return stack;
    }

    public static @Nullable String blockId(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(BLOCK_ID_KEY, PersistentDataType.STRING);
    }

    public static @Nullable String stateId(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(STATE_ID_KEY, PersistentDataType.STRING);
    }

    public static @Nullable String sudoBlockId(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(SUDO_BLOCK_ID_KEY, PersistentDataType.STRING);
    }

    public static @Nullable String sudoStateId(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(SUDO_STATE_ID_KEY, PersistentDataType.STRING);
    }

    public static boolean wand(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        return Boolean.TRUE.equals(stack.getItemMeta().getPersistentDataContainer().get(WAND_KEY, PersistentDataType.BOOLEAN));
    }

    public static @Nullable String namespace(@Nullable ItemStack stack) {
        String blockId = blockId(stack);
        if (blockId == null) {
            return null;
        }
        String[] parts = blockId.split(":", 2);
        return parts.length == 2 ? parts[0] : null;
    }

    public static boolean placeable(@Nullable ItemStack stack) {
        String blockId = blockId(stack);
        if (blockId == null) {
            return false;
        }
        BlockDefinition block = BlockRegistry.getBlock(blockId);
        return block != null && block.apiDefinition().item().placeable();
    }

    public static @NotNull ItemStack display(@NotNull RuntimeBlockView block) {
        BlockDefinition definition = BlockRegistry.getBlock(block.storedBlock().blockId());
        if (definition == null) {
            return new ItemStack(block.displayMaterial());
        }
        return display(definition, block.storedBlock().stateId());
    }

    public static @NotNull ItemStack display(@NotNull BlockDefinition definition, @NotNull String stateId) {
        ItemStack stack = new ItemStack(definition.apiDefinition().item().material());
        ItemMeta meta = stack.getItemMeta();
        itemModel(meta, modelKey(definition, stateId));
        stack.setItemMeta(meta);
        return stack;
    }

    public static @NotNull NamespacedKey modelKey(@NotNull BlockDefinition definition, @NotNull String stateId) {
        return new NamespacedKey(definition.name().namespace(), "block/" + definition.name().name() + "/" + stateId);
    }

    public static @NotNull NamespacedKey wandModelKey() {
        return new NamespacedKey(Main.getInstance(), "block_engine_wand");
    }

    static void itemModel(@NotNull ItemMeta meta, @NotNull NamespacedKey key) {
        meta.setItemModel(key);
    }

}
