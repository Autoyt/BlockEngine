package dev.auto.blockengine.placement;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementInventoryTest {
    private final Map<Integer, ItemStack> slots = new HashMap<>();
    private final PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
            PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getHeldItemSlot" -> 5;
                case "getItem" -> slots.get((Integer) args[0]);
                case "setItem" -> slots.put((Integer) args[0], (ItemStack) args[1]);
                default -> throw new UnsupportedOperationException(method.getName());
            });

    @Test
    void offhandConsumesOneFromItsOwnSlotIncludingTheLastItem() {
        ItemStack held = new Stack("ore", 2);
        ItemStack tool = new Stack("tool", 1);
        int slot = PlacementInventory.slot(inventory, EquipmentSlot.OFF_HAND);
        slots.put(40, held);
        slots.put(5, tool);

        assertTrue(PlacementInventory.consumeOne(inventory, slot, held));
        assertEquals(1, slots.get(40).getAmount());
        assertTrue(PlacementInventory.consumeOne(inventory, slot, held));
        assertNull(slots.get(40));
        assertSame(tool, slots.get(5));
    }

    @Test
    void aChangedOrEmptySlotCannotPayForPlacement() {
        int slot = PlacementInventory.slot(inventory, EquipmentSlot.HAND);
        assertEquals(5, slot);

        ItemStack expected = new Stack("ore", 1);
        assertFalse(PlacementInventory.consumeOne(inventory, slot, expected));
        ItemStack replacement = new Stack("tool", 1);
        slots.put(slot, replacement);
        assertFalse(PlacementInventory.consumeOne(inventory, slot, expected));
        assertSame(replacement, slots.get(slot));
    }

    private static final class Stack extends ItemStack {
        private final String id;
        private final int count;

        Stack(String id, int count) {
            this.id = id;
            this.count = count;
        }

        @Override
        public boolean isEmpty() {
            return count == 0;
        }

        @Override
        public int getAmount() {
            return count;
        }

        @Override
        public boolean isSimilar(ItemStack other) {
            return other instanceof Stack stack && id.equals(stack.id);
        }

        @Override
        public ItemStack asQuantity(int amount) {
            return new Stack(id, amount);
        }
    }
}
