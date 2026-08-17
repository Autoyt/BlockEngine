package dev.auto.blockengine.runtime;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.pdc.BlockEngineChunkData;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.types.ChunkKey;
import dev.auto.blockengine.visibility.VisibilityService;
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

public final class BlockEngineMutationBatcher {
    private static final Map<ChunkKey, ChunkEdit> chunks = new HashMap<>();
    private static final Map<BlockLocationKey, Block> changedBlocks = new HashMap<>();
    private static final List<Runnable> afterFlush = new ArrayList<>();
    private static boolean scheduled;

    private BlockEngineMutationBatcher() {
    }

    public static @NotNull BlockEngineChunkData data(@NotNull Chunk chunk) {
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
            BlockEngineChunkData.save(edit.chunk(), BlockEngineChunkRuntime.chunkDataKey(), edit.data());
            BlockEngineChunkRuntime.loadChunk(edit.chunk(), VisibilityService.config());
            touched.add(entry.getKey());
        }

        for (Block block : changedBlocks.values()) {
            BlockEngineBlockUpdates.update(block);
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
                Main.getInstance().getLogger().warning("BlockEngine mutation callback failed: " + exception.getMessage());
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
                BlockEngineChunkData.load(chunk, BlockEngineChunkRuntime.chunkDataKey())
        ));
    }

    private static void schedule() {
        if (scheduled) {
            return;
        }
        scheduled = true;
        Bukkit.getScheduler().runTask(Main.getInstance(), BlockEngineMutationBatcher::flushNow);
    }

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
    }

    private record ChunkEdit(@NotNull Chunk chunk, @NotNull BlockEngineChunkData data) {
    }
}
