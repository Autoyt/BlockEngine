package dev.auto.turtle.entity;

import dev.auto.turtle.types.BlockLocationKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class TurtleBlockOrchestrator {
    private static final float CUT_OFF_SPEED = 0.25f;

    private static boolean isEnabled = false;
    private static final int STARTING_ENTITY_ID = 2_000_000;
    private static final Set<Integer> reservedIds = new HashSet<>();

    public static synchronized int nextId() {
        int id = STARTING_ENTITY_ID;

        while (reservedIds.contains(id)) {
            id++;
        }

        reservedIds.add(id);
        return id;
    }

    public static synchronized void freeId(int id) {
        if (id >= STARTING_ENTITY_ID) {
            reservedIds.remove(id);
        }
    }

    private static final int UPDATE_VISIBILITY_INTERVAL = 4;
    private static BukkitTask visibilityUpdateTask;

    public static void register() {
        if (isEnabled) {
            return;
        }
        isEnabled = true;

        // TODO implement rolling visibility updates.

    }

    // Runtime source of truth: one virtual display per block location
    private static final Map<BlockLocationKey, VirtualItemDisplay> worldEntities = new HashMap<>();

    public static void addEntity(BlockLocationKey key, Material material) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) {
            return;
        }

        VirtualItemDisplay existing = worldEntities.get(key);
        if (existing != null) {
            existing.destroyForAll();
            freeId(existing.getId());
        }

        ItemStack item = new ItemStack(material);
        VirtualItemDisplay display = new VirtualItemDisplay(nextId())
                .location(new Location(world, key.x() + 0.5, key.y() + 0.5, key.z() + 0.5, 0.0f, 0.0f))
                .itemStack(item)
                .displayContext(VirtualItemDisplay.DISPLAY_CONTEXT_FIXED);

        worldEntities.put(key, display);
        display.spawnForAll();
    }

    public static VirtualItemDisplay getEntity(BlockLocationKey key) {
        return worldEntities.get(key);
    }

    public static Collection<VirtualItemDisplay> getEntities() {
        return Collections.unmodifiableCollection(worldEntities.values());
    }

    public static void removeEntity(BlockLocationKey key) {
        VirtualItemDisplay display = worldEntities.remove(key);
        if (display == null) {
            return;
        }

        display.destroyForAll();
        freeId(display.getId());
    }

    public static void updateVisibility(Player player) {
        double speed = player.getVelocity().length();
        if (speed > CUT_OFF_SPEED) return;

        for (VirtualItemDisplay display : worldEntities.values()) {
            display.spawn(player);
        }
    }

}
