package dev.auto.turtle.catalog;

import dev.auto.turtle.Main;
import dev.auto.turtle.items.TurtleItemManager;
import dev.auto.turtle.registry.BlockRegistry;
import dev.auto.turtle.types.BlockDefinition;
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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CatelogListeners implements Listener {
    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private static final @NotNull Component TITLE = Component.text("Turtle Catelog");

    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static final Map<NamespacedKey, BlockDefinition> recipes = new LinkedHashMap<>();

    public CatelogListeners() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    public static void open(@NotNull Player player) {
        registerRecipes();
        if (recipes.isEmpty()) {
            player.sendMessage("No Turtle custom blocks are registered.");
            return;
        }

        player.discoverRecipes(recipes.keySet());
        BlockDefinition first = recipes.values().iterator().next();
        sessions.put(player.getUniqueId(), new Session(first));

        InventoryView view = MenuType.STONECUTTER.create(player, TITLE);
        player.openInventory(view);
        view.getTopInventory().setItem(INPUT_SLOT, takeButton());
        view.getTopInventory().setItem(OUTPUT_SLOT, output(first));
    }

    public static void cleanup() {
        for (UUID playerId : sessions.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.undiscoverRecipes(recipes.keySet());
            }
        }
        sessions.clear();
        clearRecipes();
    }

    @EventHandler
    public void onSelect(PlayerStonecutterRecipeSelectEvent event) {
        NamespacedKey key = event.getStonecuttingRecipe().getKey();
        BlockDefinition block = recipes.get(key);
        if (block == null) {
            return;
        }

        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !isCatalog(event.getPlayer().getOpenInventory())) {
            event.setCancelled(true);
            return;
        }

        session.selected(block);
        event.getStonecutterInventory().setInputItem(takeButton());
        event.getStonecutterInventory().setResult(output(block));
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
        if (rawSlot == INPUT_SLOT || rawSlot == OUTPUT_SLOT) {
            event.setCancelled(true);
            give(player, session.selected());
            event.getView().getTopInventory().setItem(INPUT_SLOT, takeButton());
            event.getView().getTopInventory().setItem(OUTPUT_SLOT, output(session.selected()));
            return;
        }

        if (rawSlot < event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null && event.getPlayer() instanceof Player player) {
            player.undiscoverRecipes(recipes.keySet());
        }
        if (sessions.isEmpty()) {
            clearRecipes();
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            event.getPlayer().undiscoverRecipes(recipes.keySet());
        }
        if (sessions.isEmpty()) {
            clearRecipes();
        }
    }

    private static void registerRecipes() {
        clearRecipes();
        List<BlockDefinition> blocks = BlockRegistry.getBlocks().stream()
                .sorted(Comparator.comparing(BlockDefinition::id))
                .toList();

        for (BlockDefinition block : blocks) {
            NamespacedKey key = new NamespacedKey(Main.getInstance(), "catelog/" + safe(block.id()));
            StonecuttingRecipe recipe = new StonecuttingRecipe(
                    key,
                    TurtleItemManager.create(block),
                    new RecipeChoice.MaterialChoice(Material.LIME_STAINED_GLASS_PANE)
            );
            recipe.setGroup("turtle_catelog");
            Bukkit.addRecipe(recipe);
            recipes.put(key, block);
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
        if (event.getRawSlot() == OUTPUT_SLOT && TurtleItemManager.blockId(event.getCurrentItem()) != null) {
            event.setCancelled(true);
        }
    }

    private static void give(@NotNull Player player, @NotNull BlockDefinition block) {
        ItemStack stack = TurtleItemManager.create(block);
        stack.setAmount(64);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        player.sendMessage("Gave 64x " + block.id() + ".");
    }

    private static @NotNull ItemStack takeButton() {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Take 64"));
        item.setItemMeta(meta);
        return item;
    }

    private static @NotNull ItemStack output(@NotNull BlockDefinition block) {
        ItemStack item = TurtleItemManager.create(block);
        item.setAmount(64);
        return item;
    }

    private static boolean isCatalog(@NotNull InventoryView view) {
        return view.getType() == InventoryType.STONECUTTER && view.title().equals(TITLE);
    }

    private static @NotNull String safe(@NotNull String id) {
        return id.replace(':', '/').replaceAll("[^a-z0-9._/-]", "_");
    }

    private static final class Session {
        private @NotNull BlockDefinition selected;

        private Session(@NotNull BlockDefinition selected) {
            this.selected = selected;
        }

        private @NotNull BlockDefinition selected() {
            return selected;
        }

        private void selected(@NotNull BlockDefinition selected) {
            this.selected = selected;
        }
    }
}
