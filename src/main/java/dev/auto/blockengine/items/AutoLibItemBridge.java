package dev.auto.blockengine.items;

import dev.auto.autolib.items.CustomItems;
import dev.auto.blockengine.Main;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Loaded only after the optional AutoLib plugin has been found and enabled. */
final class AutoLibItemBridge implements SharedCustomItems {
    private final Main owner;
    private final Set<String> registrations = new LinkedHashSet<>();

    private AutoLibItemBridge(Main owner) {
        this.owner = owner;
    }

    static @Nullable SharedCustomItems connect(Main plugin) {
        return CustomItems.apiVersion() == 1 ? new AutoLibItemBridge(plugin) : null;
    }

    @Override
    public ItemStack create(String id, Supplier<ItemStack> template, Consumer<ItemMeta> identity) {
        if (!registrations.contains(id) || !CustomItems.contains(id)) {
            CustomItems.register(owner, id, template.get());
            registrations.add(id);
        }

        // Read the current template so runtime cosmetic edits affect future items.
        ItemStack result = CustomItems.create(id);
        ItemMeta original = result.getItemMeta();
        ItemMeta normalized = original.clone();
        identity.accept(normalized);
        if (!normalized.equals(original)) {
            result.setItemMeta(normalized);
        }
        return result;
    }

    @Override
    public void remove(String id) {
        if (registrations.remove(id)) {
            CustomItems.unregister(owner, id);
        }
    }

    @Override
    public void clear() {
        for (String id : Set.copyOf(registrations)) {
            remove(id);
        }
    }
}
