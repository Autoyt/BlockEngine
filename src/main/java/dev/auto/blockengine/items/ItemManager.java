package dev.auto.blockengine.items;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.creative.BlockDisplayNames;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.SwingAnimation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class ItemManager {
    private static final NamespacedKey BLOCK_ID_KEY = new NamespacedKey("blockengine", "block_id");
    private static final NamespacedKey STATE_ID_KEY = new NamespacedKey("blockengine", "state_id");
    private static final NamespacedKey SUDO_BLOCK_ID_KEY = new NamespacedKey("blockengine", "sudo_block_id");
    private static final NamespacedKey SUDO_STATE_ID_KEY = new NamespacedKey("blockengine", "sudo_state_id");
    private static final NamespacedKey WAND_KEY = new NamespacedKey("blockengine", "block_engine_wand");
    private static SharedCustomItems shared;

    private ItemManager() {
    }

    public static void initialize(Main plugin) {
        shutdown();
        if (!plugin.getServer().getPluginManager().isPluginEnabled("AutoLib")) {
            return;
        }

        try {
            shared = AutoLibItemBridge.connect(plugin);
            if (shared != null) {
                plugin.getLogger().info("Using AutoLib for BlockEngine custom items.");
            }
        } catch (LinkageError | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "AutoLib item API is unavailable. Using BlockEngine's standalone items.", exception);
        }
    }

    public static boolean isUsingSharedItems() {
        return shared != null;
    }

    public static void clearRegistrations() {
        if (shared != null) {
            shared.clear();
        }
    }

    public static void shutdown() {
        SharedCustomItems previous = shared;
        shared = null;
        if (previous != null) {
            previous.close();
        }
    }

    public static void register(BlockDefinition block) {
        if (shared == null) {
            return;
        }
        for (String stateId : block.apiDefinition().states().keySet()) {
            create(block, stateId);
            createSudo(block, stateId);
        }
    }

    public static void unregister(BlockDefinition block) {
        if (shared == null) {
            return;
        }
        for (String stateId : block.apiDefinition().states().keySet()) {
            shared.remove(customItemId(block, stateId, false));
            shared.remove(customItemId(block, stateId, true));
        }
    }

    public static @NotNull ItemStack create(@NotNull BlockDefinition block) {
        return create(block, block.apiDefinition().defaultState());
    }

    public static @NotNull ItemStack create(@NotNull BlockDefinition block, @NotNull String stateId) {
        block.apiDefinition().state(stateId);
        ItemStack stack = registered(customItemId(block, stateId, false), () -> build(block, stateId),
                meta -> blockIdentity(meta, block.id(), stateId, false));
        return preparePlacementItem(stack, block);
    }

    private static ItemStack build(BlockDefinition block, String stateId) {
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
        if (item.placeable()) {
            stack.setData(DataComponentTypes.INTERACT_ANIMATION, PlacementAnimations.SILENT);
        }
        return stack;
    }

    public static @NotNull ItemStack createSudo(@NotNull BlockDefinition block, @Nullable String stateId) {
        String resolvedState = stateId == null || stateId.isBlank() ? block.apiDefinition().defaultState() : stateId;
        block.apiDefinition().state(resolvedState);
        return registered(customItemId(block, resolvedState, true), () -> buildSudo(block, resolvedState),
                meta -> blockIdentity(meta, block.id(), resolvedState, true));
    }

    private static ItemStack buildSudo(BlockDefinition block, String resolvedState) {
        ItemStack stack = new ItemStack(Material.CHEST);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(SUDO_BLOCK_ID_KEY, PersistentDataType.STRING, block.id());
        meta.getPersistentDataContainer().set(SUDO_STATE_ID_KEY, PersistentDataType.STRING, resolvedState);
        itemModel(meta, modelKey(block, resolvedState));

        meta.displayName(Component.text("Placeholder: ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(BlockDisplayNames.itemName(block.apiDefinition().item().name(), block)
                        .decoration(TextDecoration.BOLD, false)));

        meta.lore(java.util.List.of(
                Component.text("[!] PLACEHOLDER BLOCK", NamedTextColor.RED, TextDecoration.BOLD),
                BlockDisplayNames.rich("<red><bold>! </bold><gray>Structure-building placeholder only."),
                BlockDisplayNames.rich("<red><bold>! </bold><gray>Not a full BlockEngine block."),
                BlockDisplayNames.rich("<red><bold>! </bold><gray>Saved structures convert it during generation."),
                Component.text("Block: " + block.id(), NamedTextColor.DARK_GRAY),
                Component.text("State: " + resolvedState, NamedTextColor.DARK_GRAY)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(64);
        stack.setItemMeta(meta);
        return stack;
    }

    public static @NotNull ItemStack createWand() {
        return registered("blockengine:wand", ItemManager::buildWand, meta -> {
            clearIdentity(meta.getPersistentDataContainer());
            meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.BOOLEAN, true);
        });
    }

    private static ItemStack buildWand() {
        ItemStack stack = new ItemStack(Material.STICK);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.BOOLEAN, true);
        itemModel(meta, wandModelKey());
        meta.displayName(Component.text("Block Engine Wand", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        meta.lore(java.util.List.of(
                Component.text("Structure authoring tool", NamedTextColor.DARK_PURPLE),
                BlockDisplayNames.rich("<light_purple>Click <gray>full blocks to make placeholders."),
                BlockDisplayNames.rich("<light_purple>Click <gray>placeholders to restore full blocks."),
                BlockDisplayNames.rich("<light_purple>Sneak-click <gray>placeholders to toggle previews.")
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        stack.setItemMeta(meta);
        return stack;
    }

    public static @NotNull String customItemId(BlockDefinition block, String stateId, boolean placeholder) {
        return customItemId(block.name().namespace(), block.name().name(), stateId, placeholder);
    }

    static String customItemId(String namespace, String name, String stateId, boolean placeholder) {
        return "blockengine:" + (placeholder ? "placeholder/" : "block/")
                + namespace + "/" + idSegment(name) + "/" + idSegment(stateId);
    }

    private static String idSegment(String value) {
        // Keep block paths and state paths in separate, reversible key segments.
        return value.replace(".", "..").replace("/", ".s");
    }

    private static ItemStack registered(String id, Supplier<ItemStack> template, Consumer<ItemMeta> identity) {
        return shared == null ? template.get() : shared.create(id, template, identity);
    }

    private static void blockIdentity(ItemMeta meta, String blockId, String stateId, boolean placeholder) {
        PersistentDataContainer data = meta.getPersistentDataContainer();
        clearIdentity(data);
        data.set(placeholder ? SUDO_BLOCK_ID_KEY : BLOCK_ID_KEY, PersistentDataType.STRING, blockId);
        data.set(placeholder ? SUDO_STATE_ID_KEY : STATE_ID_KEY, PersistentDataType.STRING, stateId);
    }

    private static void clearIdentity(PersistentDataContainer data) {
        data.remove(BLOCK_ID_KEY);
        data.remove(STATE_ID_KEY);
        data.remove(SUDO_BLOCK_ID_KEY);
        data.remove(SUDO_STATE_ID_KEY);
        data.remove(WAND_KEY);
    }

    public static @Nullable String blockId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getPersistentDataContainer().get(BLOCK_ID_KEY, PersistentDataType.STRING);
    }

    public static @Nullable ItemStack updatePlacementItem(@Nullable ItemStack stack) {
        String id = blockId(stack);
        if (id == null) {
            return stack;
        }

        BlockDefinition definition = BlockRegistry.getBlock(id);
        return definition == null ? stack : preparePlacementItem(stack, definition);
    }

    public static void updatePlacementItems(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            ItemStack updated = updatePlacementItem(current);
            if (updated != current) {
                inventory.setItem(slot, updated);
            }
        }
    }

    private static ItemStack preparePlacementItem(ItemStack stack, BlockDefinition definition) {
        var item = definition.apiDefinition().item();
        if (!item.placeable()) {
            return stack;
        }

        // Old default knowledge books consume themselves locally before the server sees the click.
        boolean oldCarrier = stack.getType() == Material.KNOWLEDGE_BOOK && item.material() == Material.PAPER;
        if (!oldCarrier && PlacementAnimations.SILENT.equals(stack.getData(DataComponentTypes.INTERACT_ANIMATION))) {
            return stack;
        }

        ItemStack updated = oldCarrier ? stack.withType(Material.PAPER) : stack.clone();
        updated.setData(DataComponentTypes.INTERACT_ANIMATION, PlacementAnimations.SILENT);
        return updated;
    }

    private static final class PlacementAnimations {
        static final SwingAnimation SILENT = SwingAnimation.swingAnimation()
                .type(SwingAnimation.Animation.NONE).duration(0).build();
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

    public static @NotNull NamespacedKey wandFeedbackModelKey(boolean sudoConversion) {
        return new NamespacedKey(Main.getInstance(), sudoConversion ? "wand_feedback_yes" : "wand_feedback_no");
    }

    public static @NotNull ItemStack wandFeedback(boolean sudoConversion) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        itemModel(meta, wandFeedbackModelKey(sudoConversion));
        stack.setItemMeta(meta);
        return stack;
    }

    static void itemModel(@NotNull ItemMeta meta, @NotNull NamespacedKey key) {
        meta.setItemModel(key);
    }

}
