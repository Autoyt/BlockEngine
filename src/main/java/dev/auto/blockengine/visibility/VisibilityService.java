package dev.auto.blockengine.visibility;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.entity.BlockEngineBlockOrchestrator;
import dev.auto.blockengine.entity.VirtualItemDisplay;
import dev.auto.blockengine.items.BlockEngineDisplayItemManager;
import dev.auto.blockengine.runtime.LoadedBlockEngineChunk;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.runtime.BlockEngineChunkRuntime;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.types.ChunkKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VisibilityService {
    private static final Map<UUID, PlayerVisibility> players = new HashMap<>();
    private static VisibilityConfig config;

    private VisibilityService() {
    }

    public static void register(@NotNull Main plugin) {
        plugin.saveDefaultConfig();
        config = VisibilityConfig.load(plugin);
    }

    public static @NotNull VisibilityConfig config() {
        if (config == null) {
            config = VisibilityConfig.load(Main.getInstance());
        }
        return config;
    }

    public static void reloadConfig() {
        config = VisibilityConfig.load(Main.getInstance());
    }

    public static void handleMove(@NotNull PlayerMoveEvent event) {
        if (!config().enabled() || event.getTo() == null) {
            return;
        }
        if (sameChunk(event.getFrom(), event.getTo())) {
            return;
        }
        recalculateOnceThisTick(event.getPlayer());
    }

    public static void recalculateOnceThisTick(@NotNull Player player) {
        int tick = Bukkit.getCurrentTick();
        PlayerVisibility state = state(player);
        if (state.lastRecalcTick() == tick) {
            return;
        }
        state.lastRecalcTick(tick);
        recalculate(player, state, false);
    }

    public static void forceRecalculate(@NotNull Player player) {
        recalculate(player, state(player), true);
    }

    public static void refreshPlayersNear(@NotNull ChunkKey chunkKey) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isChunkInPlayerRadius(player, chunkKey)) {
                forceRecalculate(player);
            }
        }
    }

    public static void refreshPlayersNear(@NotNull Iterable<ChunkKey> chunkKeys) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ChunkKey chunkKey : chunkKeys) {
                if (isChunkInPlayerRadius(player, chunkKey)) {
                    forceRecalculate(player);
                    break;
                }
            }
        }
    }

    public static void removeChunkDisplays(@NotNull ChunkKey chunkKey) {
        for (Map.Entry<UUID, PlayerVisibility> entry : players.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }

            PlayerVisibility state = entry.getValue();
            Iterator<Map.Entry<BlockLocationKey, VirtualItemDisplay>> iterator = state.active().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockLocationKey, VirtualItemDisplay> active = iterator.next();
                if (!chunkKey.contains(active.getKey())) {
                    continue;
                }

                active.getValue().destroy(player);
                recycleOrRelease(state, active.getValue());
                iterator.remove();
            }
        }
    }

    public static void cleanup(@NotNull Player player) {
        PlayerVisibility state = players.remove(player.getUniqueId());
        if (state == null) {
            return;
        }

        for (VirtualItemDisplay display : state.active().values()) {
            display.destroy(player);
            BlockEngineBlockOrchestrator.freeId(display.getId());
        }
        for (VirtualItemDisplay display : state.pool()) {
            BlockEngineBlockOrchestrator.freeId(display.getId());
        }
    }

    private static void recalculate(@NotNull Player player, @NotNull PlayerVisibility state, boolean force) {
        VisibilityConfig visibilityConfig = config();
        int radius = visibilityConfig.effectiveChunkRadius();
        Chunk chunk = player.getLocation().getChunk();
        ChunkKey center = ChunkKey.from(chunk);

        if (!force && center.equals(state.centerChunk()) && radius == state.radius()) {
            return;
        }

        Map<BlockLocationKey, RuntimeBlockView> desired = collectDesired(player.getWorld(), center, radius, visibilityConfig);
        reconcile(player, state, desired);
        state.centerChunk(center);
        state.radius(radius);
    }

    private static @NotNull Map<BlockLocationKey, RuntimeBlockView> collectDesired(
            @NotNull World world,
            @NotNull ChunkKey center,
            int radius,
            @NotNull VisibilityConfig visibilityConfig
    ) {
        Map<BlockLocationKey, RuntimeBlockView> desired = new HashMap<>();
        int radiusSquared = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSquared) {
                    continue;
                }

                int chunkX = center.x() + dx;
                int chunkZ = center.z() + dz;
                if (visibilityConfig.loadedChunksOnly() && !world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                LoadedBlockEngineChunk loadedChunk = BlockEngineChunkRuntime.get(new ChunkKey(center.worldId(), chunkX, chunkZ));
                if (loadedChunk == null) {
                    continue;
                }

                for (RuntimeBlockView block : loadedChunk.exposedBlocks()) {
                    desired.put(block.location(), block);
                }
            }
        }

        return desired;
    }

    private static void reconcile(
            @NotNull Player player,
            @NotNull PlayerVisibility state,
            @NotNull Map<BlockLocationKey, RuntimeBlockView> desired
    ) {
        Iterator<Map.Entry<BlockLocationKey, VirtualItemDisplay>> iterator = state.active().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockLocationKey, VirtualItemDisplay> entry = iterator.next();
            if (desired.containsKey(entry.getKey())) {
                continue;
            }

            entry.getValue().destroy(player);
            recycleOrRelease(state, entry.getValue());
            iterator.remove();
        }

        for (RuntimeBlockView block : desired.values()) {
            VirtualItemDisplay activeDisplay = state.active().get(block.location());
            if (activeDisplay != null) {
                configure(activeDisplay, player.getWorld(), block);
                activeDisplay.updateMetadata(player);
                continue;
            }

            VirtualItemDisplay display = takeDisplay(state);
            configure(display, player.getWorld(), block);
            display.spawn(player);
            state.active().put(block.location(), display);
        }
    }

    private static @NotNull VirtualItemDisplay takeDisplay(@NotNull PlayerVisibility state) {
        VirtualItemDisplay display = state.pool().pollFirst();
        if (display != null) {
            return display;
        }
        return new VirtualItemDisplay(BlockEngineBlockOrchestrator.nextId());
    }

    private static void recycleOrRelease(@NotNull PlayerVisibility state, @NotNull VirtualItemDisplay display) {
        if (config().recycleDisplays()) {
            state.pool().addLast(display);
            return;
        }
        BlockEngineBlockOrchestrator.freeId(display.getId());
    }

    private static void configure(@NotNull VirtualItemDisplay display, @NotNull World world, @NotNull RuntimeBlockView block) {
        BlockLocationKey location = block.location();
        display.location(new Location(world, location.x() + 0.5, location.y() + 0.5, location.z() + 0.5, 0.0f, 0.0f))
                .itemStack(BlockEngineDisplayItemManager.create(block))
                .scale(2.0f, 2.0f, 2.0f)
                .displayContext(VirtualItemDisplay.DISPLAY_CONTEXT_FIXED);
    }

    private static boolean sameChunk(@NotNull Location from, @NotNull Location to) {
        if (!from.getWorld().equals(to.getWorld())) {
            return false;
        }
        return (from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4);
    }

    private static boolean isChunkInPlayerRadius(@NotNull Player player, @NotNull ChunkKey chunkKey) {
        if (!player.getWorld().getUID().equals(chunkKey.worldId())) {
            return false;
        }

        int radius = config().effectiveChunkRadius();
        Chunk center = player.getLocation().getChunk();
        int dx = chunkKey.x() - center.getX();
        int dz = chunkKey.z() - center.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    private static @NotNull PlayerVisibility state(@NotNull Player player) {
        return players.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerVisibility());
    }
}
