package dev.auto.blockengine.integrity;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.event.BlockEngineBlockRemovedEvent;
import dev.auto.blockengine.api.event.BlockEngineModificationEvent;
import dev.auto.blockengine.entity.ManagedDisplayManager;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.PerformanceMetrics;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.visibility.VisibilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public final class BlockIntegrityManager {
    private static final BlockIntegrityManager instance = new BlockIntegrityManager();
    private final @NotNull Queue<ChunkEngine.Key> queuedChunks = new ArrayDeque<>();
    private final @NotNull Set<ChunkEngine.Key> queuedKeys = new HashSet<>();
    private final @NotNull Queue<BlockLocationKey> queuedBlocks = new ArrayDeque<>();
    private final @NotNull Set<BlockLocationKey> queuedBlockKeys = new HashSet<>();
    private @Nullable IntegrityConfig config;
    private @Nullable BukkitTask task;

    private BlockIntegrityManager() {
    }

    public static @NotNull BlockIntegrityManager getInstance() {
        return instance;
    }

    public void register(@NotNull Main plugin) {
        config = IntegrityConfig.load(plugin);
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::processQueue, 1L, 1L);
    }

    public @NotNull IntegrityConfig config() {
        if (config == null) {
            config = IntegrityConfig.load(Main.getInstance());
        }
        return config;
    }

    public void reloadConfig() {
        config = IntegrityConfig.load(Main.getInstance());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        queuedChunks.clear();
        queuedKeys.clear();
        queuedBlocks.clear();
        queuedBlockKeys.clear();
    }

    public void enqueue(@NotNull Chunk chunk) {
        if (!config().reconcileOnChunkLoad()) {
            return;
        }
        ChunkEngine.Key key = ChunkEngine.Key.from(chunk);
        if (queuedKeys.add(key)) {
            queuedChunks.add(key);
        }
    }

    public void enqueueAllLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                enqueue(chunk);
            }
        }
    }

    public boolean verifyInteraction(@NotNull Block block) {
        return config().reconcileOnInteraction() && reconcileBlock(block, BlockEngineModificationEvent.Action.RECONCILE_STALE_BLOCK);
    }

    public void verifyNextTick(@NotNull Block block) {
        if (!config().postTickVerification()) {
            return;
        }
        BlockLocationKey key = location(block);
        if (queuedBlockKeys.add(key)) {
            queuedBlocks.add(key);
        }
    }

    public int reconcileChunk(@NotNull Chunk chunk) {
        ChunkEngine.LoadedChunk loaded = ChunkEngine.get(ChunkEngine.Key.from(chunk));
        if (loaded == null) {
            return 0;
        }

        int removed = 0;
        for (RuntimeBlockView view : loaded.blocks()) {
            Block block = chunk.getWorld().getBlockAt(view.location().x(), view.location().y(), view.location().z());
            if (reconcileBlock(block, BlockEngineModificationEvent.Action.RECONCILE_STALE_BLOCK)) {
                removed++;
            }
        }
        return removed;
    }

    public boolean reconcileBlock(@NotNull Block block, @NotNull BlockEngineModificationEvent.Action action) {
        RuntimeBlockView customBlock = ChunkEngine.getBlock(location(block));
        if (customBlock == null || block.getType() == Main.getBackingBlock()) {
            return false;
        }
        clearRecord(block, customBlock, action);
        return true;
    }

    public void clearRecord(@NotNull Block block, @NotNull RuntimeBlockView customBlock, @NotNull BlockEngineModificationEvent.Action action) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(customBlock, "customBlock");
        ChunkEngine.Data data = ChunkEngine.data(block.getChunk());
        data.removeBlock(block.getX() & 15, block.getY(), block.getZ() & 15);
        ManagedDisplayManager.getInstance().removeBlockAttached(location(block));
        ChunkEngine.changed(block.getChunk());
        VisibilityManager.getInstance().refreshPlayersNear(ChunkEngine.Key.from(block.getChunk()));
        BlockEngineEvents.call(new BlockEngineBlockRemovedEvent(
                block,
                customBlock.storedBlock().blockId(),
                customBlock.storedBlock().stateId(),
                reason(action),
                false
        ));
        callEvent(action, block, customBlock.storedBlock().blockId(), customBlock.storedBlock().stateId(), null, null);
    }

    private @NotNull BlockEngineBlockRemovedEvent.Reason reason(@NotNull BlockEngineModificationEvent.Action action) {
        return switch (action) {
            case CLEAR_CUSTOM_BLOCK -> BlockEngineBlockRemovedEvent.Reason.API_CLEAR;
            case RECONCILE_STALE_BLOCK -> BlockEngineBlockRemovedEvent.Reason.RECONCILE_STALE;
            case REMOVE_CUSTOM_BLOCK -> BlockEngineBlockRemovedEvent.Reason.PLUGIN_REQUEST;
            case SET_CUSTOM_BLOCK -> BlockEngineBlockRemovedEvent.Reason.PLUGIN_REQUEST;
        };
    }

    public void callEvent(
            @NotNull BlockEngineModificationEvent.Action action,
            @NotNull Block block,
            @Nullable String previousBlockId,
            @Nullable String previousStateId,
            @Nullable String newBlockId,
            @Nullable String newStateId
    ) {
        BlockEngineEvents.modification(
                action,
                block,
                previousBlockId,
                previousStateId,
                newBlockId,
                newStateId
        );
    }

    private void processQueue() {
        long started = System.nanoTime();
        int processed = 0;
        while (processed < 128) {
            BlockLocationKey key = queuedBlocks.poll();
            if (key == null) {
                break;
            }
            queuedBlockKeys.remove(key);
            World world = Bukkit.getWorld(key.worldId());
            if (world != null && world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                reconcileBlock(world.getBlockAt(key.x(), key.y(), key.z()), BlockEngineModificationEvent.Action.RECONCILE_STALE_BLOCK);
            }
            processed++;
        }
        IntegrityConfig loadedConfig = config();
        int chunks = 0;
        for (int i = 0; i < loadedConfig.chunksPerTick(); i++) {
            ChunkEngine.Key key = queuedChunks.poll();
            if (key == null) {
                break;
            }
            queuedKeys.remove(key);
            World world = Bukkit.getWorld(key.worldId());
            if (world == null || !world.isChunkLoaded(key.x(), key.z())) {
                continue;
            }
            reconcileChunk(world.getChunkAt(key.x(), key.z()));
            chunks++;
        }
        PerformanceMetrics.record(PerformanceMetrics.INTEGRITY, System.nanoTime() - started, chunks, processed);
    }

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
