package dev.auto.blockengine.runtime;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.pdc.BlockEngineChunkData;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.types.ChunkKey;
import dev.auto.blockengine.visibility.VisibilityConfig;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class BlockEngineChunkRuntime {
    private static final NamespacedKey CHUNK_DATA_KEY = new NamespacedKey(Main.getInstance(), "chunk_data");
    private static final Map<ChunkKey, LoadedBlockEngineChunk> chunks = new HashMap<>();

    private BlockEngineChunkRuntime() {
    }

    public static void loadChunk(@NotNull Chunk chunk, @NotNull VisibilityConfig config) {
        ChunkKey key = ChunkKey.from(chunk);
        BlockEngineChunkData data = BlockEngineChunkData.load(chunk, CHUNK_DATA_KEY);

        LoadedBlockEngineChunk loaded = new LoadedBlockEngineChunk(key);
        World world = chunk.getWorld();
        for (BlockEngineChunkData.StoredBlock block : data.blocks()) {
            int worldX = (chunk.getX() << 4) + block.localX();
            int worldZ = (chunk.getZ() << 4) + block.localZ();
            BlockLocationKey location = new BlockLocationKey(world.getUID(), worldX, block.y(), worldZ);
            boolean exposed = !config.exposureEnabled() || isExposed(world, worldX, block.y(), worldZ, config);
            loaded.add(new RuntimeBlockView(key, location, block.fallbackBlock(), block, exposed));
        }

        chunks.put(key, loaded);
    }

    public static void unloadChunk(@NotNull Chunk chunk) {
        chunks.remove(ChunkKey.from(chunk));
    }

    public static @Nullable LoadedBlockEngineChunk get(@NotNull ChunkKey key) {
        return chunks.get(key);
    }

    public static @Nullable RuntimeBlockView getBlock(@NotNull BlockLocationKey location) {
        LoadedBlockEngineChunk chunk = chunks.get(new ChunkKey(location.worldId(), location.x() >> 4, location.z() >> 4));
        if (chunk == null) {
            return null;
        }

        return chunk.block(location.x() & 15, location.y(), location.z() & 15);
    }

    public static @NotNull Collection<LoadedBlockEngineChunk> chunks() {
        return Collections.unmodifiableCollection(chunks.values());
    }

    public static @NotNull NamespacedKey chunkDataKey() {
        return CHUNK_DATA_KEY;
    }

    private static boolean isExposed(@NotNull World world, int x, int y, int z, @NotNull VisibilityConfig config) {
        return isOpen(world.getBlockAt(x + 1, y, z), config)
                || isOpen(world.getBlockAt(x - 1, y, z), config)
                || isOpen(world.getBlockAt(x, y + 1, z), config)
                || isOpen(world.getBlockAt(x, y - 1, z), config)
                || isOpen(world.getBlockAt(x, y, z + 1), config)
                || isOpen(world.getBlockAt(x, y, z - 1), config);
    }

    private static boolean isOpen(@NotNull Block block, @NotNull VisibilityConfig config) {
        Material material = block.getType();
        if (material.isAir()) {
            return true;
        }
        if (config.treatLiquidAsExposed() && block.isLiquid()) {
            return true;
        }
        if (config.treatPassableAsExposed() && block.isPassable()) {
            return true;
        }
        return config.treatNonSolidAsExposed() && !material.isSolid();
    }
}
