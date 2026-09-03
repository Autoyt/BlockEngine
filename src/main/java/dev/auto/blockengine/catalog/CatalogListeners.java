package dev.auto.blockengine.catalog;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.chat.BlockEngineChat;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.structure.SudoBlockManager;
import dev.auto.blockengine.types.BlockDefinition;
import io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
    private static final @NotNull Component SUDO_TITLE = Component.text("BlockEngine structure catalog");
    private static final @NotNull NamespacedKey WAND_RECIPE_KEY = new NamespacedKey(Main.getInstance(), "catalog/sudo/_wand");

    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static final Map<NamespacedKey, RecipeEntry> recipes = new LinkedHashMap<>();

    public CatalogListeners() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    public static void open(@NotNull Player player) {
        open(player, null);
    }

    public static void open(@NotNull Player player, @Nullable String namespace) {
        registerRecipes(false);
        List<BlockDefinition> blocks = filteredBlocks(namespace);
        open(player, namespace, blocks, false);
    }

    public static void openSudo(@NotNull Player player) {
        if (!player.hasPermission(SudoBlockManager.PERMISSION)) {
            BlockEngineChat.error(player, "You don't have permission to open the BlockEngine structure catalog.");
            return;
        }
        registerRecipes(true);
        open(player, null, allBlocks(), true);
    }

    private static void open(
            @NotNull Player player,
            @Nullable String namespace,
            @NotNull List<BlockDefinition> blocks,
            boolean sudo
    ) {
        if (blocks.isEmpty() && !sudo) {
            BlockEngineChat.warn(player, namespace == null
                    ? "No BlockEngine full blocks are registered."
                    : "No BlockEngine full blocks are registered for namespace '" + namespace + "'.");
            return;
        }

        Collection<NamespacedKey> keys = recipeKeys(blocks, sudo);
        player.discoverRecipes(keys);
        BlockDefinition first = sudo ? null : blocks.getFirst();
        CatalogHolder holder = new CatalogHolder(namespace, keys, sudo);
        sessions.put(player.getUniqueId(), new Session(first, keys, holder, sudo));

        InventoryView view = MenuType.STONECUTTER.create(player, sudo ? SUDO_TITLE : TITLE);
        player.openInventory(view);
        holder.inventory(view.getTopInventory());
        view.getTopInventory().setItem(INPUT_SLOT, inputItem(sudo));
        view.getTopInventory().setItem(OUTPUT_SLOT, output(first, sudo));
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
        if (session == null || !isCatalog(event.getPlayer().getOpenInventory()) || session.sudo() != entry.sudo()) {
            event.setCancelled(true);
            return;
        }

        if (!session.holder().recipes().contains(key)) {
            event.setCancelled(true);
            return;
        }
        session.selected(entry.block());
        event.getStonecutterInventory().setInputItem(inputItem(session.sudo()));
        event.getStonecutterInventory().setResult(output(entry.block(), session.sudo()));
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
            event.getView().getTopInventory().setItem(INPUT_SLOT, inputItem(session.sudo()));
            return;
        }

        if (rawSlot == OUTPUT_SLOT) {
            event.setCancelled(true);
            give(player, session.selected(), session.sudo());
            event.getView().getTopInventory().setItem(INPUT_SLOT, inputItem(session.sudo()));
            event.getView().getTopInventory().setItem(OUTPUT_SLOT, output(session.selected(), session.sudo()));
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

    private static @NotNull List<BlockDefinition> allBlocks() {
        return BlockRegistry.getBlocks().stream()
                .sorted(Comparator.comparing(BlockDefinition::id))
                .toList();
    }

    private static void registerRecipes(boolean sudo) {
        clearRecipes();
        List<BlockDefinition> blocks = sudo ? allBlocks() : filteredBlocks(null);

        if (sudo) {
            StonecuttingRecipe wandRecipe = new StonecuttingRecipe(
                    WAND_RECIPE_KEY,
                    ItemManager.createWand(),
                    new RecipeChoice.MaterialChoice(Material.CHEST)
            );
            wandRecipe.setGroup("BlockEngine_structure_catalog");
            Bukkit.addRecipe(wandRecipe);
            recipes.put(WAND_RECIPE_KEY, new RecipeEntry(null, sudo));
        }

        for (BlockDefinition block : blocks) {
            NamespacedKey key = recipeKey(block, sudo);
            StonecuttingRecipe recipe = new StonecuttingRecipe(
                    key,
                    sudo ? ItemManager.createSudo(block) : ItemManager.create(block),
                    new RecipeChoice.MaterialChoice(sudo ? Material.CHEST : Main.getBackingBlock())
            );
            recipe.setGroup(sudo ? "BlockEngine_structure_catalog" : "BlockEngine_catalog");
            Bukkit.addRecipe(recipe);
            recipes.put(key, new RecipeEntry(block, sudo));
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

    private static void give(@NotNull Player player, @Nullable BlockDefinition block, boolean sudo) {
        ItemStack stack = block == null ? ItemManager.createWand() : sudo ? ItemManager.createSudo(block) : ItemManager.create(block);
        stack.setAmount(64);
        if (block == null) {
            stack.setAmount(1);
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        BlockEngineChat.send(player, BlockEngineChat.status("gave", true)
                .append(Component.space())
                .append(BlockEngineChat.value(block == null ? "1x" : "64x"))
                .append(Component.space())
                .append(block == null
                        ? Component.text("Block Engine Wand", BlockEngineChat.ORANGE_LIGHT)
                        : sudo
                        ? Component.text("sudo ", BlockEngineChat.WARNING).append(BlockEngineChat.blockName(block))
                        : BlockEngineChat.blockName(block)));
    }

    private static @NotNull ItemStack inputItem(boolean sudo) {
        ItemStack item = new ItemStack(sudo ? Material.CHEST : Main.getBackingBlock());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(sudo ? "Structure Building" : "BlockEngine catalog"));
        item.setItemMeta(meta);
        return item;
    }

    private static @NotNull ItemStack output(@Nullable BlockDefinition block, boolean sudo) {
        ItemStack item = block == null ? ItemManager.createWand() : sudo ? ItemManager.createSudo(block) : ItemManager.create(block);
        item.setAmount(64);
        if (block == null) {
            item.setAmount(1);
        }
        return item;
    }

    private static boolean isCatalog(@NotNull InventoryView view) {
        return view.getType() == InventoryType.STONECUTTER
                && (view.title().equals(TITLE) || view.title().equals(SUDO_TITLE));
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

    private static @NotNull NamespacedKey recipeKey(@NotNull BlockDefinition block, boolean sudo) {
        return new NamespacedKey(Main.getInstance(), sudo ? "catalog/sudo/" + safe(block.id()) : "catalog/" + safe(block.id()));
    }

    private static @NotNull Collection<NamespacedKey> recipeKeys(@NotNull List<BlockDefinition> blocks, boolean sudo) {
        if (!sudo) {
            return blocks.stream().map(block -> recipeKey(block, false)).toList();
        }
        java.util.ArrayList<NamespacedKey> keys = new java.util.ArrayList<>();
        keys.add(WAND_RECIPE_KEY);
        keys.addAll(blocks.stream().map(block -> recipeKey(block, true)).toList());
        return keys;
    }

    private record RecipeEntry(@Nullable BlockDefinition block, boolean sudo) {
    }

    private static final class Session {
        private @Nullable BlockDefinition selected;
        private final @NotNull Collection<NamespacedKey> recipes;
        private final @NotNull CatalogHolder holder;
        private final boolean sudo;

        private Session(
                @Nullable BlockDefinition selected,
                @NotNull Collection<NamespacedKey> recipes,
                @NotNull CatalogHolder holder,
                boolean sudo
        ) {
            this.selected = selected;
            this.recipes = List.copyOf(recipes);
            this.holder = holder;
            this.sudo = sudo;
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

        private boolean sudo() {
            return sudo;
        }

        private void selected(@Nullable BlockDefinition selected) {
            this.selected = selected;
        }
    }

    private static final class CatalogHolder implements InventoryHolder {
        private final @Nullable String namespace;
        private final @NotNull Collection<NamespacedKey> recipes;
        private final boolean sudo;
        private @Nullable Inventory inventory;

        private CatalogHolder(@Nullable String namespace, @NotNull Collection<NamespacedKey> recipes, boolean sudo) {
            this.namespace = namespace;
            this.recipes = List.copyOf(recipes);
            this.sudo = sudo;
        }

        private @Nullable String namespace() {
            return namespace;
        }

        private @NotNull Collection<NamespacedKey> recipes() {
            return recipes;
        }

        private boolean sudo() {
            return sudo;
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
