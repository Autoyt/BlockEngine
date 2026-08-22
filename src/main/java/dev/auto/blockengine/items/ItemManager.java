package dev.auto.blockengine.items;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class ItemManager {
    private static final NamespacedKey BLOCK_ID_KEY = new NamespacedKey(Main.getInstance(), "block_id");
    private static final NamespacedKey STATE_ID_KEY = new NamespacedKey(Main.getInstance(), "state_id");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

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

        meta.displayName(name(item.name(), block));
        if (!item.lore().isEmpty()) {
            meta.lore(item.lore().stream().map(ItemManager::rich).toList());
        }
        if (item.glint()) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.setMaxStackSize(64);

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

    static void itemModel(@NotNull ItemMeta meta, @NotNull NamespacedKey key) {
        meta.setItemModel(key);
    }

    private static @NotNull Component name(@Nullable String name, @NotNull BlockDefinition block) {
        if (name != null && !name.isBlank()) {
            return rich(name);
        }
        return Component.text(vanilla(block), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static @NotNull Component rich(@NotNull String text) {
        return MINI.deserialize(text)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static @NotNull String vanilla(@NotNull BlockDefinition block) {
        String path = block.apiDefinition().name();
        int slash = path.lastIndexOf('/');
        String base = slash == -1 ? path : path.substring(slash + 1);
        StringBuilder result = new StringBuilder();
        for (String part : base.split("[_\\-.]+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                result.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.isEmpty() ? block.id() : result.toString();
    }
}
