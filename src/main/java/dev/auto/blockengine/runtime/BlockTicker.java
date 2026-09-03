package dev.auto.blockengine.runtime;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockTicker {
    private static final BlockTicker instance = new BlockTicker();

    private final Set<BlockLocationKey> tickingBlocks = new LinkedHashSet<>();
    private final Map<ChunkEngine.Key, Set<BlockLocationKey>> tickingBlocksByChunk = new HashMap<>();
    private @Nullable BukkitTask task;

    private BlockTicker() {
    }

    public static @NotNull BlockTicker getInstance() {
        return instance;
    }

    public void register(@NotNull Main plugin) {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task == null) {
            return;
        }
        task.cancel();
        task = null;
    }

    public void clear() {
        tickingBlocks.clear();
        tickingBlocksByChunk.clear();
    }

    public void loadChunk(@NotNull ChunkEngine.Key key, @NotNull ChunkEngine.LoadedChunk chunk) {
        unloadChunk(key);

        Set<BlockLocationKey> chunkTickingBlocks = new HashSet<>();
        for (RuntimeBlockView view : chunk.blocks()) {
            BlockDefinition definition = BlockRegistry.getBlock(view.storedBlock().blockId());
            if (definition == null || !definition.adapter().ticking()) {
                continue;
            }
            chunkTickingBlocks.add(view.location());
            tickingBlocks.add(view.location());
        }

        if (!chunkTickingBlocks.isEmpty()) {
            tickingBlocksByChunk.put(key, chunkTickingBlocks);
        }
    }

    public void unloadChunk(@NotNull ChunkEngine.Key key) {
        Set<BlockLocationKey> removed = tickingBlocksByChunk.remove(key);
        if (removed != null) {
            tickingBlocks.removeAll(removed);
        }
    }

    private void tick() {
        long started = System.nanoTime();
        int processed = 0;
        int changed = 0;
        for (var iterator = tickingBlocks.iterator(); iterator.hasNext();) {
            BlockLocationKey location = iterator.next();
            processed++;
            RuntimeBlockView view = ChunkEngine.getBlock(location);
            if (view == null) {
                iterator.remove();
                continue;
            }
            BlockDefinition definition = BlockRegistry.getBlock(view.storedBlock().blockId());
            if (definition == null || !definition.adapter().ticking()) {
                iterator.remove();
                continue;
            }
            World world = Bukkit.getWorld(view.location().worldId());
            if (world == null) {
                continue;
            }
            Block block = world.getBlockAt(view.location().x(), view.location().y(), view.location().z());
            BlockContext context = BlockDataManager.getInstance().context(block, view, null);
            if (context == null) {
                continue;
            }
            ChunkEngine.SimpleBlockData before = ChunkEngine.SimpleBlockData.copyOf(context.data());
            try {
                definition.adapter().onTick(context);
            } catch (RuntimeException exception) {
                Main.getInstance().getLogger().warning("BlockEngine tick failed for "
                        + view.storedBlock().blockId() + " at " + view.location() + ": " + exception.getMessage());
                continue;
            }

            RuntimeBlockView current = ChunkEngine.getBlock(view.location());
            if (!before.equalsData(context.data()) && current != null && current.storedBlock().blockId().equals(context.blockId())) {
                BlockDataManager.getInstance().save(block, context);
                changed++;
            }
        }
        PerformanceMetrics.record(PerformanceMetrics.TICKER, System.nanoTime() - started, processed, changed);
    }
}
