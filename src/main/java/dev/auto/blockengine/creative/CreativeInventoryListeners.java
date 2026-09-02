package dev.auto.blockengine.creative;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.chat.BlockEngineChat;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class CreativeInventoryListeners implements Listener {
    public CreativeInventoryListeners() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    @EventHandler
    public void onCreative(@NotNull InventoryCreativeEvent event) {
        ItemStack converted = CreativeInventoryManager.convertCreativeStack(event.getCursor());
        if (converted != null) {
            event.setCursor(converted);
        }
        if (event.getWhoClicked() instanceof Player player) {
            scanNextTick(player);
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
        scanNextTick(player);
    }

    @EventHandler
    public void onDrag(@NotNull InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scanNextTick(player);
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

    private static void scanNextTick(@NotNull Player player) {
        Main.getInstance().getServer().getScheduler().runTask(Main.getInstance(), () -> scan(player));
    }

    private static void scan(@NotNull Player player) {
        ItemStack cursor = CreativeInventoryManager.convertCreativeStack(player.getItemOnCursor());
        if (cursor != player.getItemOnCursor()) {
            player.setItemOnCursor(cursor);
        }

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            ItemStack converted = CreativeInventoryManager.convertCreativeStack(current);
            if (converted != current) {
                player.getInventory().setItem(slot, converted);
            }
        }
    }
}
