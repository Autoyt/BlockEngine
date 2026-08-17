package dev.auto.turtle.runtime;

import dev.auto.turtle.Main;
import dev.auto.turtle.pdc.TurtleChunkData;
import dev.auto.turtle.types.BlockLocationKey;
import dev.auto.turtle.types.ChunkKey;
import dev.auto.turtle.visibility.VisibilityService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TurtleMutationBatcher {
    private static final Map<ChunkKey, ChunkEdit> chunks = new HashMap<>();
    private static final Map<BlockLocationKey, Block> changedBlocks = new HashMap<>();
    private static final List<Runnable> afterFlush = new ArrayList<>();
    private static boolean scheduled;

    private TurtleMutationBatcher() {
    }

    public static @NotNull TurtleChunkData data(@NotNull Chunk chunk) {
        return edit(chunk).data();
    }

    public static void changed(@NotNull Block block) {
        changedBlocks.put(location(block), block);
        edit(block.getChunk());
        schedule();
    }

    public static void changed(@NotNull Chunk chunk) {
        edit(chunk);
        schedule();
    }

    public static void afterFlush(@NotNull Runnable runnable) {
        afterFlush.add(runnable);
        schedule();
    }

    public static void flushNow() {
        if (chunks.isEmpty() && changedBlocks.isEmpty() && afterFlush.isEmpty()) {
            scheduled = false;
            return;
        }

        Set<ChunkKey> touched = new HashSet<>();
        for (Map.Entry<ChunkKey, ChunkEdit> entry : chunks.entrySet()) {
            ChunkEdit edit = entry.getValue();
            TurtleChunkData.save(edit.chunk(), TurtleChunkRuntime.chunkDataKey(), edit.data());
            TurtleChunkRuntime.loadChunk(edit.chunk(), VisibilityService.config());
            touched.add(entry.getKey());
        }

        for (Block block : changedBlocks.values()) {
            TurtleBlockUpdates.update(block);
        }

        if (!touched.isEmpty()) {
            VisibilityService.refreshPlayersNear(touched);
        }

        chunks.clear();
        changedBlocks.clear();
        scheduled = false;

        List<Runnable> callbacks = new ArrayList<>(afterFlush);
        afterFlush.clear();
        for (Runnable callback : callbacks) {
            try {
                callback.run();
            } catch (RuntimeException exception) {
                Main.getInstance().getLogger().warning("Turtle mutation callback failed: " + exception.getMessage());
            }
        }
    }

    public static void clear() {
        chunks.clear();
        changedBlocks.clear();
        afterFlush.clear();
        scheduled = false;
    }

    private static @NotNull ChunkEdit edit(@NotNull Chunk chunk) {
        ChunkKey key = ChunkKey.from(chunk);
        return chunks.computeIfAbsent(key, ignored -> new ChunkEdit(
                chunk,
                TurtleChunkData.load(chunk, TurtleChunkRuntime.chunkDataKey())
        ));
    }

    private static void schedule() {
        if (scheduled) {
            return;
        }
        scheduled = true;
        Bukkit.getScheduler().runTask(Main.getInstance(), TurtleMutationBatcher::flushNow);
    }

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
    }

    private record ChunkEdit(@NotNull Chunk chunk, @NotNull TurtleChunkData data) {
    }
}
