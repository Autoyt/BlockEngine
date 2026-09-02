package dev.auto.blockengine.creative;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.chat.BlockEngineChat;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class CreativeInventoryListeners implements Listener {
    public CreativeInventoryListeners() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreative(@NotNull InventoryCreativeEvent event) {
        ItemStack converted = CreativeInventoryManager.convertCreativeStack(event.getCursor());
        if (converted != event.getCursor()) {
            event.setCursor(converted);
            placeCreativeItem(event, converted);
        }
        convertCurrentItem(event);
        if (event.getWhoClicked() instanceof Player player) {
            scanSoon(player);
        }
    }

    @EventHandler
    public void onClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack cursor = CreativeInventoryManager.convertCreativeStack(event.getCursor());
        if (cursor != event.getCursor()) {
            event.setCursor(cursor);
        }
        convertCurrentItem(event);
        scanSoon(player);
    }

    @EventHandler
    public void onDrag(@NotNull InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scanSoon(player);
        }
    }

    @EventHandler
    public void onDrop(@NotNull PlayerDropItemEvent event) {
        ItemStack converted = CreativeInventoryManager.convertCreativeStack(event.getItemDrop().getItemStack());
        if (converted != event.getItemDrop().getItemStack()) {
            event.getItemDrop().setItemStack(converted);
        }
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        int entries = CreativeInventoryManager.pendingRestartEntries();
        if (entries <= 0 || !event.getPlayer().hasPermission("blockengine.debug")) {
            return;
        }
        BlockEngineChat.send(event.getPlayer(), BlockEngineChat.status("creative", false)
                .append(Component.space())
                .append(BlockEngineChat.value(entries))
                .append(Component.text(entries == 1
                        ? " new creative enchantment entry was discovered. Restart the server to show it in the creative inventory."
                        : " new creative enchantment entries were discovered. Restart the server to show them in the creative inventory.",
                        BlockEngineChat.WHITE)));
    }

    private static void scanSoon(@NotNull Player player) {
        Main.getInstance().getServer().getScheduler().runTask(Main.getInstance(), () -> scan(player));
        Main.getInstance().getServer().getScheduler().runTaskLater(Main.getInstance(), () -> scan(player), 1L);
    }

    private static void scan(@NotNull Player player) {
        ItemStack cursor = CreativeInventoryManager.convertCreativeStack(player.getItemOnCursor());
        if (cursor != player.getItemOnCursor()) {
            player.setItemOnCursor(cursor);
        }

        InventoryView view = player.getOpenInventory();
        scan(view.getTopInventory());
        scan(view.getBottomInventory());
    }

    private static void scan(@NotNull Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            ItemStack converted = CreativeInventoryManager.convertCreativeStack(current);
            if (converted != current) {
                inventory.setItem(slot, converted);
            }
        }
    }

    private static void convertCurrentItem(@NotNull InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack converted = CreativeInventoryManager.convertCreativeStack(current);
        if (converted != current) {
            event.setCurrentItem(converted);
        }
    }

    private static void placeCreativeItem(@NotNull InventoryCreativeEvent event, @NotNull ItemStack converted) {
        Inventory clicked = event.getClickedInventory();
        int slot = event.getSlot();
        if (clicked != null && slot >= 0 && slot < clicked.getSize()) {
            clicked.setItem(slot, converted);
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < event.getView().countSlots()) {
            event.setCurrentItem(converted);
            event.getView().setItem(rawSlot, converted);
        }

        if (event.getWhoClicked() instanceof Player player) {
            player.updateInventory();
        }
    }
}
