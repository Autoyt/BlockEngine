package dev.auto.blockengine.items;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Keeps optional AutoLib types out of BlockEngine's standalone item path. */
interface SharedCustomItems extends AutoCloseable {
    ItemStack create(String id, Supplier<ItemStack> template, Consumer<ItemMeta> identity);

    void remove(String id);

    void clear();

    @Override
    default void close() {
        clear();
    }
}
