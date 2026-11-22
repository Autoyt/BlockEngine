package dev.auto.blockengine.placement;

import dev.auto.blockengine.Main;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Level;

/** Paper 26.3's public block predicate view omits block-state and block-entity conditions. */
public final class AdventurePlacement {
    private static final NativePredicate predicate = NativePredicate.load();

    private AdventurePlacement() {
    }

    public static boolean allows(ItemStack item, Block clicked) {
        if (predicate == null || !item.hasData(DataComponentTypes.CAN_PLACE_ON)) {
            return false;
        }
        try {
            Object level = predicate.worldHandle().invoke(clicked.getWorld());
            Object position = predicate.blockPosition().invoke(clicked);
            Object substrate = predicate.blockInWorld().newInstance(level, position, false);
            Object stack = predicate.itemCopy().invoke(null, item);
            return (boolean) predicate.canPlace().invoke(stack, substrate);
        } catch (ReflectiveOperationException failure) {
            Main.getInstance().getLogger().log(Level.WARNING, "Could not check adventure placement predicate", failure);
            return false;
        }
    }

    private record NativePredicate(Method worldHandle, Method blockPosition, Constructor<?> blockInWorld,
                                   Method itemCopy, Method canPlace) {
        static NativePredicate load() {
            try {
                Class<?> blockInWorld = Class.forName("net.minecraft.world.level.block.state.pattern.BlockInWorld");
                Method itemCopy = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
                        .getMethod("asNMSCopy", ItemStack.class);
                return new NativePredicate(
                        Class.forName("org.bukkit.craftbukkit.CraftWorld").getMethod("getHandle"),
                        Class.forName("org.bukkit.craftbukkit.block.CraftBlock").getMethod("getPosition"),
                        blockInWorld.getConstructor(Class.forName("net.minecraft.world.level.LevelReader"),
                                Class.forName("net.minecraft.core.BlockPos"), boolean.class),
                        itemCopy, itemCopy.getReturnType().getMethod("canPlaceOnBlockInAdventureMode", blockInWorld)
                );
            } catch (ReflectiveOperationException failure) {
                Main.getInstance().getLogger().log(Level.WARNING, "Native adventure placement checking is unavailable", failure);
                return null;
            }
        }
    }
}
