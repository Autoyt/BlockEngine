package dev.auto.blockengine.catalog;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.chat.BlockEngineChat;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CatalogListeners implements Listener {
    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private static final @NotNull Component TITLE = Component.text("BlockEngine catalog");

    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static final Map<NamespacedKey, RecipeEntry> recipes = new LinkedHashMap<>();

    public CatalogListeners() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    public static void open(@NotNull Player player) {
        open(player, null);
    }

    public static void open(@NotNull Player player, @Nullable String namespace) {
        registerRecipes();
        List<BlockDefinition> blocks = filteredBlocks(namespace);
        open(player, namespace, blocks);
    }

    private static void open(
            @NotNull Player player,
            @Nullable String namespace,
            @NotNull List<BlockDefinition> blocks
    ) {
        if (blocks.isEmpty()) {
            BlockEngineChat.warn(player, namespace == null
                    ? "No BlockEngine full blocks are registered."
                    : "No BlockEngine full blocks are registered for namespace '" + namespace + "'.");
            return;
        }

        Collection<NamespacedKey> keys = recipeKeys(blocks);
        player.discoverRecipes(keys);
        BlockDefinition first = blocks.getFirst();
        CatalogHolder holder = new CatalogHolder(namespace, keys);
        sessions.put(player.getUniqueId(), new Session(first, keys, holder));

        InventoryView view = MenuType.STONECUTTER.create(player, TITLE);
        player.openInventory(view);
        holder.inventory(view.getTopInventory());
        view.getTopInventory().setItem(INPUT_SLOT, inputItem());
        view.getTopInventory().setItem(OUTPUT_SLOT, output(first));
    }

    public static void cleanup() {
        for (UUID playerId : sessions.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.undiscoverRecipes(sessions.get(playerId).recipes());
            }
        }
        sessions.clear();
        clearRecipes();
    }

    @EventHandler
    public void onSelect(PlayerStonecutterRecipeSelectEvent event) {
        NamespacedKey key = event.getStonecuttingRecipe().getKey();
        RecipeEntry entry = recipes.get(key);
        if (entry == null) {
            return;
        }

        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !isCatalog(event.getPlayer().getOpenInventory())) {
            event.setCancelled(true);
            return;
        }

        if (!session.holder().recipes().contains(key)) {
            event.setCancelled(true);
            return;
        }
        session.selected(entry.block());
        event.getStonecutterInventory().setInputItem(inputItem());
        event.getStonecutterInventory().setResult(output(entry.block()));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Session session = sessions.get(player.getUniqueId());
        if (session == null || !isCatalog(event.getView())) {
            blockRealStonecutterLeak(event);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot == INPUT_SLOT) {
            event.setCancelled(true);
            event.getView().getTopInventory().setItem(INPUT_SLOT, inputItem());
            return;
        }

        if (rawSlot == OUTPUT_SLOT) {
            event.setCancelled(true);
            give(player, session.selected());
            event.getView().getTopInventory().setItem(INPUT_SLOT, inputItem());
            event.getView().getTopInventory().setItem(OUTPUT_SLOT, output(session.selected()));
            return;
        }

        if (rawSlot < event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isCatalog(event.getView())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null && event.getPlayer() instanceof Player player) {
            clearCatalogItems(event.getView());
            player.undiscoverRecipes(session.recipes());
        }
        if (sessions.isEmpty()) {
            clearRecipes();
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            clearCatalogItems(event.getPlayer().getOpenInventory());
            event.getPlayer().undiscoverRecipes(session.recipes());
        }
        if (sessions.isEmpty()) {
            clearRecipes();
        }
    }

    private static @NotNull List<BlockDefinition> filteredBlocks(@Nullable String namespace) {
        return BlockRegistry.getBlocks().stream()
                .filter(block -> block.apiDefinition().catalog())
                .filter(block -> namespace == null || block.name().namespace().equalsIgnoreCase(namespace))
                .sorted(Comparator.comparing(BlockDefinition::id))
                .toList();
    }

    private static void registerRecipes() {
        clearRecipes();
        List<BlockDefinition> blocks = filteredBlocks(null);

        for (BlockDefinition block : blocks) {
            NamespacedKey key = recipeKey(block);
            StonecuttingRecipe recipe = new StonecuttingRecipe(
                    key,
                    ItemManager.create(block),
                    new RecipeChoice.MaterialChoice(Main.getBackingBlock())
            );
            recipe.setGroup("BlockEngine_catalog");
            Bukkit.addRecipe(recipe);
            recipes.put(key, new RecipeEntry(block));
        }
    }

    private static void clearRecipes() {
        for (NamespacedKey key : recipes.keySet()) {
            Bukkit.removeRecipe(key);
        }
        recipes.clear();
    }

    private static void blockRealStonecutterLeak(@NotNull InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.STONECUTTER) {
            return;
        }
        if (event.getRawSlot() == OUTPUT_SLOT && ItemManager.blockId(event.getCurrentItem()) != null) {
            event.setCancelled(true);
        }
    }

    private static void give(@NotNull Player player, @NotNull BlockDefinition block) {
        ItemStack stack = ItemManager.create(block);
        stack.setAmount(64);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        BlockEngineChat.send(player, BlockEngineChat.status("gave", true)
                .append(Component.space())
                .append(BlockEngineChat.value(block == null ? "1x" : "64x"))
                .append(Component.space())
                .append(BlockEngineChat.blockName(block)));
    }

    private static @NotNull ItemStack inputItem() {
        ItemStack item = new ItemStack(Main.getBackingBlock());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("BlockEngine catalog"));
        item.setItemMeta(meta);
        return item;
    }

    private static @NotNull ItemStack output(@NotNull BlockDefinition block) {
        ItemStack item = ItemManager.create(block);
        item.setAmount(64);
        return item;
    }

    private static boolean isCatalog(@NotNull InventoryView view) {
        return view.getType() == InventoryType.STONECUTTER && view.title().equals(TITLE);
    }

    private static void clearCatalogItems(@NotNull InventoryView view) {
        if (!isCatalog(view)) {
            return;
        }
        view.getTopInventory().setItem(INPUT_SLOT, null);
        view.getTopInventory().setItem(OUTPUT_SLOT, null);
    }

    private static @NotNull String safe(@NotNull String id) {
        return id.replace(':', '/').replaceAll("[^a-z0-9._/-]", "_");
    }

    private static @NotNull NamespacedKey recipeKey(@NotNull BlockDefinition block) {
        return new NamespacedKey(Main.getInstance(), "catalog/" + safe(block.id()));
    }

    private static @NotNull Collection<NamespacedKey> recipeKeys(@NotNull List<BlockDefinition> blocks) {
        return blocks.stream().map(CatalogListeners::recipeKey).toList();
    }

    private record RecipeEntry(@NotNull BlockDefinition block) {
    }

    private static final class Session {
        private @Nullable BlockDefinition selected;
        private final @NotNull Collection<NamespacedKey> recipes;
        private final @NotNull CatalogHolder holder;

        private Session(
                @Nullable BlockDefinition selected,
                @NotNull Collection<NamespacedKey> recipes,
                @NotNull CatalogHolder holder
        ) {
            this.selected = selected;
            this.recipes = List.copyOf(recipes);
            this.holder = holder;
        }

        private @Nullable BlockDefinition selected() {
            return selected;
        }

        private @NotNull Collection<NamespacedKey> recipes() {
            return recipes;
        }

        private @NotNull CatalogHolder holder() {
            return holder;
        }

        private void selected(@Nullable BlockDefinition selected) {
            this.selected = selected;
        }
    }

    private static final class CatalogHolder implements InventoryHolder {
        private final @Nullable String namespace;
        private final @NotNull Collection<NamespacedKey> recipes;
        private @Nullable Inventory inventory;

        private CatalogHolder(@Nullable String namespace, @NotNull Collection<NamespacedKey> recipes) {
            this.namespace = namespace;
            this.recipes = List.copyOf(recipes);
        }

        private @Nullable String namespace() {
            return namespace;
        }

        private @NotNull Collection<NamespacedKey> recipes() {
            return recipes;
        }

        private void inventory(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory == null) {
                throw new IllegalStateException("Catalog inventory has not been opened yet.");
            }
            return inventory;
        }
    }
}
