package dev.auto.blockengine.placement;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class PlacementInventory {
    // PlayerInventory indexes armor at 36 through 39 and the offhand at 40.
    private static final int OFFHAND_SLOT = 40;

    private PlacementInventory() {
    }

    public static int slot(PlayerInventory inventory, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND ? OFFHAND_SLOT : inventory.getHeldItemSlot();
    }

    public static boolean consumeOne(Inventory inventory, int slot, ItemStack expected) {
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.isEmpty() || !current.isSimilar(expected)) {
            return false;
        }
        inventory.setItem(slot, current.getAmount() == 1 ? null : current.asQuantity(current.getAmount() - 1));
        return true;
    }
}
