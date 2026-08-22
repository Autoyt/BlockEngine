package dev.auto.blockengine.entity;

import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PacketEntityManager {
    private static final int STARTING_ENTITY_ID = 2_000_000;
    private static final Set<Integer> reservedIds = new HashSet<>();
    private static final Map<BlockLocationKey, VirtualItemDisplay> blockDisplays = new HashMap<>();

    private PacketEntityManager() {
    }

    public static @NotNull VirtualItemDisplay itemDisplay() {
        return new VirtualItemDisplay(reserveId());
    }

    public static synchronized int reserveId() {
        int id = STARTING_ENTITY_ID;
        while (reservedIds.contains(id)) {
            id++;
        }
        reservedIds.add(id);
        return id;
    }

    public static synchronized void releaseId(int id) {
        if (id >= STARTING_ENTITY_ID) {
            reservedIds.remove(id);
        }
    }

    public static void release(@Nullable VirtualItemDisplay display) {
        if (display != null) {
            releaseId(display.getId());
        }
    }

    public static @Nullable VirtualItemDisplay blockDisplay(@NotNull BlockLocationKey key) {
        return blockDisplays.get(key);
    }

    public static @NotNull Collection<VirtualItemDisplay> blockDisplays() {
        return Collections.unmodifiableCollection(blockDisplays.values());
    }

    public static @Nullable VirtualItemDisplay putBlockDisplay(@NotNull BlockLocationKey key, @NotNull VirtualItemDisplay display) {
        VirtualItemDisplay previous = blockDisplays.put(key, display);
        if (previous != null && previous.getId() != display.getId()) {
            previous.destroyForAll();
            release(previous);
        }
        return previous;
    }

    public static @Nullable VirtualItemDisplay spawnBlockItemDisplay(@NotNull BlockLocationKey key, @NotNull Material material) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) {
            return null;
        }

        VirtualItemDisplay display = itemDisplay()
                .location(center(key, world))
                .itemStack(new ItemStack(material))
                .displayContext(VirtualItemDisplay.DISPLAY_CONTEXT_FIXED);
        putBlockDisplay(key, display);
        display.spawnForAll();
        return display;
    }

    public static void removeBlockDisplay(@NotNull BlockLocationKey key) {
        VirtualItemDisplay display = blockDisplays.remove(key);
        if (display == null) {
            return;
        }

        display.destroyForAll();
        release(display);
    }

    public static void spawnVisibleBlockDisplays(@NotNull Player player) {
        for (VirtualItemDisplay display : blockDisplays.values()) {
            display.spawn(player);
        }
    }

    public static void clearBlockDisplays() {
        for (VirtualItemDisplay display : blockDisplays.values()) {
            display.destroyForAll();
            release(display);
        }
        blockDisplays.clear();
    }

    private static @NotNull Location center(@NotNull BlockLocationKey key, @NotNull World world) {
        return new Location(world, key.x() + 0.5, key.y() + 0.5, key.z() + 0.5, 0.0f, 0.0f);
    }
}
