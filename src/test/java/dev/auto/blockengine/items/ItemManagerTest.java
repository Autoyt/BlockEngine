package dev.auto.blockengine.items;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemManagerTest {
    @Test
    void keepsHierarchicalBlockAndStateIdentitiesDistinct() {
        String blockPath = ItemManager.customItemId("example", "machine/active", "north", false);
        String statePath = ItemManager.customItemId("example", "machine", "active/north", false);
        String literalEscape = ItemManager.customItemId("example", "machine.sactive", "north", false);
        String placeholder = ItemManager.customItemId("example", "machine/active", "north", true);

        assertNotEquals(blockPath, statePath);
        assertNotEquals(blockPath, literalEscape);
        assertNotEquals(blockPath, placeholder);
        assertNotNull(NamespacedKey.fromString(blockPath));
        assertNotNull(NamespacedKey.fromString(statePath));
        assertNotNull(NamespacedKey.fromString(literalEscape));
    }

    @Test
    void initializesWithoutTheOptionalAutoLibApiOrPlugin() throws Exception {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.auto.autolib.items.CustomItems"));
        assertNotNull(Class.forName(ItemManager.class.getName()));
        assertFalse(ItemManager.isUsingSharedItems());

        ItemManager.clearRegistrations();
        ItemManager.shutdown();
        assertFalse(ItemManager.isUsingSharedItems());
    }
}
