package dev.auto.blockengine.runtime;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.display.DisplayAudience;
import dev.auto.blockengine.api.display.DisplayPersistence;
import dev.auto.blockengine.api.display.DisplaySpec;
import dev.auto.blockengine.api.event.BlockEngineChunkSaveEvent;
import dev.auto.blockengine.api.event.BlockEngineChunkSavedEvent;
import dev.auto.blockengine.entity.ManagedDisplayManager;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.placement.BlockOrientation;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.visibility.VisibilityConfig;
import dev.auto.blockengine.visibility.VisibilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class ChunkEngine {
    static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;
    private static final NamespacedKey CHUNK_DATA_KEY = new NamespacedKey("blockengine", "chunk_data");
    private static final Map<Key, LoadedChunk> chunks = new HashMap<>();
    private static final Set<Key> dirtyChunks = new HashSet<>();
    private static final Map<Key, Data> failedSaves = new HashMap<>();
    private static final Set<Key> saving = new HashSet<>();
    private static final Set<BlockLocationKey> pendingExposure = new HashSet<>();
    private static final List<BlockFace> SIDES = List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
    private static final List<BlockFace> NEIGHBORS = List.of(
            BlockFace.SELF, BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    );
    private static final Map<BlockLocationKey, Block> changedBlocks = new HashMap<>();
    private static final List<Runnable> afterFlush = new ArrayList<>();
    private static boolean flushScheduled;
    private static boolean flushing;

    private ChunkEngine() {
    }

    public static void load(@NotNull Chunk chunk, @NotNull VisibilityConfig config) {
        Key key = Key.from(chunk);
        if (chunks.containsKey(key)) {
            return;
        }

        long started = System.nanoTime();
        Data data = failedSaves.get(key);
        if (data == null) {
            try {
                data = Data.load(chunk, CHUNK_DATA_KEY);
            } catch (RuntimeException exception) {
                Main.getInstance().getLogger().log(Level.SEVERE, "Cannot load BlockEngine chunk " + key
                        + ". Its stored data has been preserved.", exception);
                return;
            }
        } else {
            dirtyChunks.add(key);
            scheduleFlush();
        }
        LoadedChunk loaded = new LoadedChunk(chunk, data);
        chunks.put(key, loaded);
        index(loaded, config);
        queueNeighborEdges(key);
        PerformanceMetrics.record(PerformanceMetrics.CHUNK_LOAD, System.nanoTime() - started, data.blocks().size(), 0);
    }

    private static void index(@NotNull LoadedChunk loaded, @NotNull VisibilityConfig config) {
        Key key = loaded.key();
        World world = loaded.chunk.getWorld();
        for (StoredBlock block : loaded.data.blocks()) {
            int worldX = (key.x() << 4) + block.localX();
            int worldZ = (key.z() << 4) + block.localZ();
            BlockLocationKey location = new BlockLocationKey(key.worldId(), worldX, block.y(), worldZ);
            boolean exposed = isExposed(world, worldX, block.y(), worldZ, config);
            loaded.add(new RuntimeBlockView(key, location, block.fallbackBlock(), block, exposed));
        }

        BlockTicker.getInstance().loadChunk(key, loaded);
        ManagedDisplayManager.getInstance().loadChunk(key, loaded.data);
    }

    public static void unload(@NotNull Chunk chunk) {
        Key key = Key.from(chunk);
        LoadedChunk loaded = chunks.get(key);
        if (loaded == null) {
            return;
        }

        boolean needsSave = dirtyChunks.remove(key);
        try {
            if ((needsSave || failedSaves.containsKey(key)) && !saving.contains(key)) {
                save(loaded);
            }
            // Save listeners may stage one last edit, or unload the chunk during its own save.
            if (dirtyChunks.remove(key) || saving.contains(key)) {
                Data.save(chunk, CHUNK_DATA_KEY, loaded.data);
                failedSaves.remove(key);
            }
        } catch (RuntimeException exception) {
            // Keep only data when the chunk leaves memory. Retry when it next loads.
            failedSave(loaded, exception);
        }
        chunks.remove(key);
        changedBlocks.keySet().removeIf(key::contains);
        BlockTicker.getInstance().unloadChunk(key);
        ManagedDisplayManager.getInstance().unloadChunk(key);
        queueNeighborEdges(key);
    }

    public static @Nullable LoadedChunk get(@NotNull Key key) {
        return chunks.get(key);
    }

    public static @Nullable RuntimeBlockView getBlock(@NotNull BlockLocationKey location) {
        Key key = new Key(location.worldId(), location.x() >> 4, location.z() >> 4);
        LoadedChunk chunk = chunks.get(key);
        if (chunk == null) {
            return null;
        }

        int localX = location.x() & 15;
        int localZ = location.z() & 15;
        StoredBlock stored = chunk.data.blockAt(localX, location.y(), localZ);
        if (stored == null) {
            return null;
        }

        RuntimeBlockView indexed = chunk.block(localX, location.y(), localZ);
        if (indexed != null && indexed.storedBlock() == stored) {
            return indexed;
        }

        boolean exposed = isExposed(chunk.chunk.getWorld(), location.x(), location.y(), location.z(),
                VisibilityManager.getInstance().config());
        return new RuntimeBlockView(key, location, stored.fallbackBlock(), stored, exposed);
    }

    /** Reads persisted data if Paper has loaded the chunk but has not fired its load event yet. */
    public static @Nullable RuntimeBlockView inspectLoadedBlock(@NotNull Block block) {
        BlockLocationKey location = location(block);
        Key key = new Key(location.worldId(), location.x() >> 4, location.z() >> 4);
        if (chunks.containsKey(key)) {
            return getBlock(location);
        }
        World world = block.getWorld();
        if (!world.isChunkLoaded(key.x(), key.z())) {
            return null;
        }

        Data data = Data.load(world.getChunkAt(key.x(), key.z(), false), CHUNK_DATA_KEY);
        StoredBlock stored = data.blockAt(location.x() & 15, location.y(), location.z() & 15);
        if (stored == null) {
            return null;
        }
        boolean exposed = isExposed(world, location.x(), location.y(), location.z(), VisibilityManager.getInstance().config());
        return new RuntimeBlockView(key, location, stored.fallbackBlock(), stored, exposed);
    }

    public static @NotNull Collection<LoadedChunk> chunks() {
        return Collections.unmodifiableCollection(chunks.values());
    }

    public static @NotNull NamespacedKey dataKey() {
        return CHUNK_DATA_KEY;
    }

    public static @NotNull Data data(@NotNull Chunk chunk) {
        Key key = Key.from(chunk);
        LoadedChunk loaded = chunks.get(key);
        if (loaded == null) {
            load(chunk, VisibilityManager.getInstance().config());
            loaded = chunks.get(key);
            if (loaded == null) {
                throw new IllegalStateException("Cannot edit unreadable BlockEngine chunk " + key);
            }
        }
        return loaded.data;
    }

    public static void changed(@NotNull Block block) {
        changedBlocks.put(location(block), block);
        changed(block.getChunk());
    }

    public static void changed(@NotNull Chunk chunk) {
        data(chunk);
        dirtyChunks.add(Key.from(chunk));
        scheduleFlush();
    }

    public static void afterFlush(@NotNull Runnable runnable) {
        afterFlush.add(runnable);
        scheduleFlush();
    }

    public static void flushNow() {
        if (flushing) {
            return;
        }
        flushing = true;
        try {
            flushBatch();
        } finally {
            flushing = false;
            if (hasWork()) {
                scheduleFlush();
            }
        }
    }

    private static void flushBatch() {
        // Mark the current batch before firing events or physics. New edits must survive this pass.
        Set<Key> batch = Set.copyOf(dirtyChunks);
        List<Block> updates = List.copyOf(changedBlocks.values());
        changedBlocks.clear();
        List<Runnable> callbacks = List.copyOf(afterFlush);
        afterFlush.clear();

        Set<Key> touched = new HashSet<>();
        for (Key key : batch) {
            LoadedChunk loaded = chunks.get(key);
            if (loaded != null && dirtyChunks.remove(key)) {
                try {
                    save(loaded);
                    touched.add(key);
                } catch (RuntimeException exception) {
                    failedSave(loaded, exception);
                }
            }
        }

        for (Block block : updates) {
            if (chunks.containsKey(new Key(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4))) {
                BlockUpdates.update(block);
                queueExposureAround(block);
            }
        }
        refreshExposure(touched);

        if (!touched.isEmpty()) {
            VisibilityManager.getInstance().refreshPlayersNear(touched);
        }
        for (Runnable callback : callbacks) {
            runAfterFlush(callback);
        }
    }

    private static void save(@NotNull LoadedChunk loaded) {
        Key key = loaded.key();
        Chunk chunk = loaded.chunk;
        Data data = loaded.data;
        saving.add(key);
        try {
            long started = System.nanoTime();
            BlockEngineEvents.call(new BlockEngineChunkSaveEvent(chunk, data.blocks().size(), data.displayCount(), data.isEmpty()));
            if (chunks.get(key) != loaded) {
                return;
            }

            // Edits made by the pre-save listener are included in this write.
            dirtyChunks.remove(key);
            Data.save(chunk, CHUNK_DATA_KEY, data);
            failedSaves.remove(key);
            PerformanceMetrics.record(PerformanceMetrics.CHUNK_SAVE, System.nanoTime() - started, data.blocks().size(), 0);
            LoadedChunk indexed = new LoadedChunk(chunk, data);
            chunks.put(key, indexed);
            index(indexed, VisibilityManager.getInstance().config());
            BlockEngineEvents.call(new BlockEngineChunkSavedEvent(chunk, data.blocks().size(), data.displayCount(), data.isEmpty()));
        } finally {
            saving.remove(key);
        }
    }

    private static void failedSave(@NotNull LoadedChunk loaded, @NotNull RuntimeException exception) {
        failedSaves.put(loaded.key(), loaded.data);
        Main.getInstance().getLogger().log(Level.SEVERE, "Failed to save BlockEngine chunk " + loaded.key(), exception);
    }

    public static void saveWorld(@NotNull World world) {
        flushNow();
        // A post-save callback can stage more edits in this world. Include them in Paper's imminent
        // disk save, while leaving them queued for the normal index refresh and notifications.
        Set<Key> remaining = new HashSet<>(dirtyChunks);
        remaining.addAll(failedSaves.keySet());
        for (Key key : remaining) {
            if (key.worldId().equals(world.getUID())) {
                persistLatest(key);
            }
        }
    }

    private static void persistLatest(@NotNull Key key) {
        LoadedChunk loaded = chunks.get(key);
        if (loaded != null) {
            try {
                Data.save(loaded.chunk, CHUNK_DATA_KEY, loaded.data);
                failedSaves.remove(key);
            } catch (RuntimeException exception) {
                failedSave(loaded, exception);
            }
        }
    }

    public static void clear() {
        // A final callback during disable can still edit a chunk after the regular flush.
        Set<Key> remaining = new HashSet<>(dirtyChunks);
        remaining.addAll(failedSaves.keySet());
        remaining.forEach(ChunkEngine::persistLatest);
        chunks.clear();
        dirtyChunks.clear();
        failedSaves.clear();
        saving.clear();
        pendingExposure.clear();
        changedBlocks.clear();
        afterFlush.clear();
        flushScheduled = false;
        BlockTicker.getInstance().clear();
    }

    private static boolean hasWork() {
        return !dirtyChunks.isEmpty() || !changedBlocks.isEmpty() || !afterFlush.isEmpty() || !pendingExposure.isEmpty();
    }

    private static void scheduleFlush() {
        if (flushScheduled || flushing || !Main.getInstance().isEnabled()) {
            return;
        }
        flushScheduled = true;
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            flushScheduled = false;
            flushNow();
        });
    }

    private static void runAfterFlush(@NotNull Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException exception) {
            Main.getInstance().getLogger().warning("BlockEngine mutation callback failed: " + exception.getMessage());
        }
    }

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
    }

    public static void queueExposureAround(@NotNull Block changed) {
        UUID worldId = changed.getWorld().getUID();
        int localX = changed.getX() & 15;
        int localZ = changed.getZ() & 15;
        if (localX > 0 && localX < 15 && localZ > 0 && localZ < 15) {
            LoadedChunk center = chunks.get(new Key(worldId, changed.getX() >> 4, changed.getZ() >> 4));
            if (center == null || !center.hasBlocks()) {
                return;
            }
        }

        for (BlockFace face : NEIGHBORS) {
            int x = changed.getX() + face.getModX();
            int y = changed.getY() + face.getModY();
            int z = changed.getZ() + face.getModZ();
            LoadedChunk chunk = chunks.get(new Key(worldId, x >> 4, z >> 4));
            if (chunk != null && chunk.block(x & 15, y, z & 15) != null) {
                pendingExposure.add(new BlockLocationKey(worldId, x, y, z));
            }
        }
        if (!pendingExposure.isEmpty()) {
            scheduleFlush();
        }
    }

    private static void queueNeighborEdges(@NotNull Key key) {
        for (BlockFace face : SIDES) {
            int dx = face.getModX();
            int dz = face.getModZ();
            LoadedChunk neighbor = chunks.get(new Key(key.worldId(), key.x() + dx, key.z() + dz));
            if (neighbor == null) {
                continue;
            }

            for (RuntimeBlockView view : neighbor.blocks()) {
                int localX = view.storedBlock().localX();
                int localZ = view.storedBlock().localZ();
                if (dx == 1 && localX == 0 || dx == -1 && localX == 15
                        || dz == 1 && localZ == 0 || dz == -1 && localZ == 15) {
                    pendingExposure.add(view.location());
                }
            }
        }
        if (!pendingExposure.isEmpty()) {
            scheduleFlush();
        }
    }

    private static void refreshExposure(@NotNull Set<Key> touched) {
        for (BlockLocationKey location : pendingExposure) {
            Key key = new Key(location.worldId(), location.x() >> 4, location.z() >> 4);
            LoadedChunk loaded = chunks.get(key);
            if (loaded == null) {
                continue;
            }
            RuntimeBlockView view = loaded.block(location.x() & 15, location.y(), location.z() & 15);
            if (view == null) {
                continue;
            }

            boolean exposed = isExposed(loaded.chunk.getWorld(), location.x(), location.y(), location.z(),
                    VisibilityManager.getInstance().config());
            if (view.exposed() != exposed) {
                loaded.add(new RuntimeBlockView(key, location, view.displayMaterial(), view.storedBlock(), exposed));
                touched.add(key);
            }
        }
        pendingExposure.clear();
    }

    private static boolean isExposed(@NotNull World world, int x, int y, int z, @NotNull VisibilityConfig config) {
        return !config.exposureEnabled()
                || isOpen(world, x + 1, y, z, config) || isOpen(world, x - 1, y, z, config)
                || isOpen(world, x, y + 1, z, config) || isOpen(world, x, y - 1, z, config)
                || isOpen(world, x, y, z + 1, config) || isOpen(world, x, y, z - 1, config);
    }

    private static boolean isOpen(@NotNull World world, int x, int y, int z, @NotNull VisibilityConfig config) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return true;
        }
        Block block = world.getBlockAt(x, y, z);
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

    public record Key(@NotNull UUID worldId, int x, int z) {
        public static @NotNull Key from(@NotNull Chunk chunk) {
            return new Key(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }

        public boolean contains(@NotNull BlockLocationKey block) {
            return worldId.equals(block.worldId()) && (block.x() >> 4) == x && (block.z() >> 4) == z;
        }
    }

    public static final class LoadedChunk {
        private final @NotNull Key key;
        private final @NotNull Chunk chunk;
        private final @NotNull Data data;
        private final Map<Long, RuntimeBlockView> byLocalPosition = new LinkedHashMap<>();
        private final Map<Long, RuntimeBlockView> exposedBlocks = new LinkedHashMap<>();

        private LoadedChunk(@NotNull Chunk chunk, @NotNull Data data) {
            this.key = Key.from(chunk);
            this.chunk = chunk;
            this.data = data;
        }

        public @NotNull Key key() {
            return key;
        }

        public boolean hasBlocks() {
            return !data.blocks().isEmpty();
        }

        private void add(@NotNull RuntimeBlockView block) {
            long position = localKey(block.location().x() & 15, block.location().y(), block.location().z() & 15);
            byLocalPosition.put(position, block);
            if (block.exposed()) {
                exposedBlocks.put(position, block);
            } else {
                exposedBlocks.remove(position);
            }
        }

        public @NotNull Collection<RuntimeBlockView> blocks() {
            return Collections.unmodifiableCollection(byLocalPosition.values());
        }

        public @NotNull Collection<RuntimeBlockView> exposedBlocks() {
            return Collections.unmodifiableCollection(exposedBlocks.values());
        }

        public @Nullable RuntimeBlockView block(int localX, int y, int localZ) {
            return byLocalPosition.get(localKey(localX, y, localZ));
        }

        private long localKey(int localX, int y, int localZ) {
            return ((long) localX & 15L) << 36
                    | ((long) localZ & 15L) << 32
                    | ((long) y & 0xffffffffL);
        }
    }

    public static final class Data {
        public static final int VERSION = 7;
        private static final int MIN_VERSION = 2;
        private static final int VERSION_BEFORE_ORIENTATION = 6;
        public static final PersistentDataType<byte[], Data> TYPE = new DataType();

        private final @NotNull Map<Integer, StoredBlock> blocks = new LinkedHashMap<>();
        private final @NotNull Map<UUID, StoredDisplay> displays = new LinkedHashMap<>();

        public boolean isEmpty() {
            return blocks.isEmpty() && displays.isEmpty();
        }

        public @NotNull Collection<StoredBlock> blocks() {
            return Collections.unmodifiableCollection(blocks.values());
        }

        public int displayCount() {
            int count = displays.size();
            for (StoredBlock block : blocks.values()) {
                count += block.displays().size();
            }
            return count;
        }

        public @NotNull Collection<StoredDisplay> displays() {
            return Collections.unmodifiableCollection(displays.values());
        }

        public @Nullable StoredBlock blockAt(int localX, int y, int localZ) {
            return y < -2048 || y > 2047 ? null : blocks.get(packBlockKey(localX, y, localZ));
        }

        public void setBlock(@NotNull StoredBlock block) {
            blocks.put(packBlockKey(block.localX(), block.y(), block.localZ()), block);
        }

        public void setBlock(
                int localX,
                int y,
                int localZ,
                @NotNull BlockData data,
                @NotNull dev.auto.blockengine.api.blocks.BlockDefinition definition,
                byte @Nullable [] payload
        ) {
            setBlock(StoredBlock.from(localX, y, localZ, data, definition, payload));
        }

        public void removeBlock(int localX, int y, int localZ) {
            blocks.remove(packBlockKey(localX, y, localZ));
        }

        public void setDisplay(@NotNull StoredDisplay display) {
            displays.put(display.id(), display);
        }

        public void removeDisplay(@NotNull UUID id) {
            displays.remove(id);
        }

        public void setBlockDisplay(int localX, int y, int localZ, @NotNull StoredDisplay display) {
            StoredBlock block = blockAt(localX, y, localZ);
            if (block != null) {
                setBlock(block.withDisplay(display));
            }
        }

        public void removeBlockDisplay(int localX, int y, int localZ, @NotNull UUID id) {
            StoredBlock block = blockAt(localX, y, localZ);
            if (block != null) {
                setBlock(block.withoutDisplay(id));
            }
        }

        public static @NotNull Data load(@NotNull Chunk chunk, @NotNull NamespacedKey key) {
            Objects.requireNonNull(chunk, "chunk");
            Objects.requireNonNull(key, "key");

            PersistentDataContainer pdc = chunk.getPersistentDataContainer();
            Data data = pdc.get(key, TYPE);
            return data == null ? new Data() : data;
        }

        public static void save(@NotNull Chunk chunk, @NotNull NamespacedKey key, @NotNull Data data) {
            Objects.requireNonNull(chunk, "chunk");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(data, "data");

            PersistentDataContainer pdc = chunk.getPersistentDataContainer();
            if (data.isEmpty()) {
                pdc.remove(key);
                return;
            }
            pdc.set(key, TYPE, data);
        }
    }

    public record StoredBlock(
            int localX,
            int y,
            int localZ,
            @NotNull Material fallbackBlock,
            float hardness,
            float miningSpeed,
            boolean unbreakable,
            boolean dropsItem,
            boolean dropInCreative,
            @NotNull SimpleBlockData data,
            byte @NotNull [] payload,
            @NotNull List<StoredDisplay> displays,
            @NotNull BlockOrientation orientation
    ) {
        public StoredBlock(
                int localX, int y, int localZ, @NotNull Material fallbackBlock, float hardness, float miningSpeed,
                boolean unbreakable, boolean dropsItem, boolean dropInCreative, @NotNull SimpleBlockData data,
                byte @Nullable [] payload, @NotNull List<StoredDisplay> displays
        ) {
            this(localX, y, localZ, fallbackBlock, hardness, miningSpeed, unbreakable,
                    dropsItem, dropInCreative, data, payload, displays, BlockOrientation.IDENTITY);
        }

        public StoredBlock {
            validateLocal(localX, y, localZ);
            Objects.requireNonNull(fallbackBlock, "fallbackBlock");
            Objects.requireNonNull(orientation, "orientation");
            Objects.requireNonNull(data, "data");
            if (payload != null && payload.length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Adapter payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
            }
            payload = payload == null ? new byte[0] : payload.clone();
            displays = List.copyOf(displays);
        }

        public @NotNull String blockId() {
            return data.blockId();
        }

        public @NotNull String stateId() {
            return data.stateId();
        }

        @Override
        public byte @NotNull [] payload() {
            return payload.clone();
        }

        boolean matchesPayload(byte @Nullable [] candidate) {
            return candidate == null ? payload.length == 0 : Arrays.equals(payload, candidate);
        }

        public int payloadBytes() {
            return payload.length;
        }

        public @NotNull StoredBlock withOrientation(@NotNull BlockOrientation next) {
            return orientation == next ? this : new StoredBlock(localX, y, localZ, fallbackBlock, hardness, miningSpeed,
                    unbreakable, dropsItem, dropInCreative, data, payload, displays, next);
        }

        public @NotNull StoredBlock withDisplay(@NotNull StoredDisplay display) {
            List<StoredDisplay> updated = new ArrayList<>();
            boolean replaced = false;
            for (StoredDisplay existing : displays) {
                if (existing.id().equals(display.id()) || Objects.equals(existing.ownerKey(), display.ownerKey())) {
                    updated.add(display);
                    replaced = true;
                } else {
                    updated.add(existing);
                }
            }
            if (!replaced) {
                updated.add(display);
            }
            return new StoredBlock(localX, y, localZ, fallbackBlock, hardness, miningSpeed, unbreakable,
                    dropsItem, dropInCreative, data, payload, updated, orientation);
        }

        public @NotNull StoredBlock withoutDisplay(@NotNull UUID id) {
            return new StoredBlock(localX, y, localZ, fallbackBlock, hardness, miningSpeed, unbreakable,
                    dropsItem, dropInCreative, data, payload, displays.stream()
                    .filter(display -> !display.id().equals(id))
                    .toList(), orientation);
        }

        public static @NotNull StoredBlock from(
                int localX,
                int y,
                int localZ,
                @NotNull BlockData source,
                @NotNull dev.auto.blockengine.api.blocks.BlockDefinition definition,
                byte @Nullable [] payload
        ) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(definition, "definition");

            return from(localX, y, localZ, source, definition, payload, List.of(), BlockOrientation.IDENTITY);
        }

        public static @NotNull StoredBlock from(
                int localX, int y, int localZ, @NotNull BlockData source,
                @NotNull dev.auto.blockengine.api.blocks.BlockDefinition definition,
                byte @Nullable [] payload, @NotNull List<StoredDisplay> displays,
                @NotNull BlockOrientation orientation
        ) {
            dev.auto.blockengine.api.blocks.BlockDefinition.State state = definition.state(source.stateId());
            return new StoredBlock(
                    localX,
                    y,
                    localZ,
                    definition.defaultBlock(),
                    state.hardness(),
                    state.miningSpeed(),
                    state.unbreakable(),
                    state.dropsItem(),
                    state.dropInCreative(),
                    SimpleBlockData.copyOf(source),
                    payload,
                    displays,
                    orientation
            );
        }

        public @NotNull StoredBlock refreshSnapshot(@NotNull dev.auto.blockengine.api.blocks.BlockDefinition definition) {
            return from(localX, y, localZ, data, definition, payload, displays, orientation);
        }

        public @Nullable SimpleBlockData loadData(@NotNull dev.auto.blockengine.types.BlockDefinition definition) {
            Objects.requireNonNull(definition, "definition");

            SimpleBlockData copy = SimpleBlockData.copyOf(data);
            byte[] payloadCopy = payload();
            try {
                definition.adapter().load(copy, payloadCopy);
                return copy;
            } catch (Throwable error) {
                if (definition.adapter().recoverPayload(copy, payloadCopy, error)) {
                    return copy;
                }
                return null;
            }
        }
    }

    public record StoredDisplay(
            @NotNull UUID id,
            @NotNull DisplayPersistence persistence,
            @Nullable BlockLocationKey owner,
            @Nullable String ownerKey,
            @NotNull DisplaySpec spec
    ) {
        public StoredDisplay {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(persistence, "persistence");
            Objects.requireNonNull(spec, "spec");
        }

        public static @NotNull StoredDisplay from(@NotNull ManagedDisplayManager.ManagedDisplay display) {
            return new StoredDisplay(display.id(), display.persistence(), display.owner(), display.ownerKey(), display.spec());
        }

        public @NotNull StoredDisplay withOwner(@Nullable BlockLocationKey owner) {
            return new StoredDisplay(id, persistence, owner, ownerKey, spec);
        }
    }

    public static final class SimpleBlockData implements BlockData {
        private @NotNull String blockId;
        private @NotNull String stateId;
        private final @NotNull Map<String, String> stringData = new LinkedHashMap<>();
        private final @NotNull Map<String, Integer> intData = new LinkedHashMap<>();
        private final @NotNull Map<String, Boolean> booleanData = new LinkedHashMap<>();

        public SimpleBlockData(@NotNull String blockId, @NotNull String stateId) {
            this.blockId = Objects.requireNonNull(blockId, "blockId");
            this.stateId = Objects.requireNonNull(stateId, "stateId");
        }

        public static @NotNull SimpleBlockData copyOf(@NotNull BlockData source) {
            SimpleBlockData copy = new SimpleBlockData(source.blockId(), source.stateId());
            copy.stringData.putAll(source.stringData());
            copy.intData.putAll(source.intData());
            copy.booleanData.putAll(source.booleanData());
            return copy;
        }

        public boolean equalsData(@NotNull BlockData other) {
            return blockId.equals(other.blockId())
                    && stateId.equals(other.stateId())
                    && stringData.equals(other.stringData())
                    && intData.equals(other.intData())
                    && booleanData.equals(other.booleanData());
        }

        @Override
        public @NotNull String blockId() {
            return blockId;
        }

        @Override
        public void blockId(@NotNull String blockId) {
            this.blockId = Objects.requireNonNull(blockId, "blockId");
        }

        @Override
        public @NotNull String stateId() {
            return stateId;
        }

        @Override
        public void stateId(@NotNull String stateId) {
            this.stateId = Objects.requireNonNull(stateId, "stateId");
        }

        @Override
        public @NotNull Map<String, String> stringData() {
            return stringData;
        }

        @Override
        public @NotNull Map<String, Integer> intData() {
            return intData;
        }

        @Override
        public @NotNull Map<String, Boolean> booleanData() {
            return booleanData;
        }
    }

    private static int packBlockKey(int localX, int y, int localZ) {
        validateLocal(localX, y, localZ);
        return (localX & 0xF) << 24 | ((y + 2048) & 0xFFF) << 12 | (localZ & 0xF) << 8;
    }

    private static void validateLocal(int localX, int y, int localZ) {
        if (localX < 0 || localX > 15) {
            throw new IllegalArgumentException("localX must be 0-15: " + localX);
        }
        if (localZ < 0 || localZ > 15) {
            throw new IllegalArgumentException("localZ must be 0-15: " + localZ);
        }
        if (y < -2048 || y > 2047) {
            throw new IllegalArgumentException("y is out of supported range: " + y);
        }
    }

    private static final class DataType implements PersistentDataType<byte[], Data> {
        @Override
        public @NotNull Class<byte[]> getPrimitiveType() {
            return byte[].class;
        }

        @Override
        public @NotNull Class<Data> getComplexType() {
            return Data.class;
        }

        @Override
        public byte @NotNull [] toPrimitive(@NotNull Data complex, @NotNull PersistentDataAdapterContext context) {
            Objects.requireNonNull(complex, "complex");
            Objects.requireNonNull(context, "context");

            long started = System.nanoTime();
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);

                boolean rotated = complex.blocks.values().stream().anyMatch(block -> block.orientation() != BlockOrientation.IDENTITY);
                int version = rotated ? Data.VERSION : Data.VERSION_BEFORE_ORIENTATION;
                out.writeInt(version);
                out.writeInt(complex.blocks.size());
                for (StoredBlock block : complex.blocks.values()) {
                    writeBlock(out, block, version);
                }
                out.writeInt(complex.displays.size());
                for (StoredDisplay display : complex.displays.values()) {
                    writeDisplay(out, display);
                }

                out.flush();
                byte[] primitive = bytes.toByteArray();
                PerformanceMetrics.record(PerformanceMetrics.CHUNK_ENCODE, System.nanoTime() - started,
                        complex.blocks.size(), primitive.length);
                return primitive;
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to encode BlockEngine chunk data.", exception);
            }
        }

        @Override
        public @NotNull Data fromPrimitive(byte @NotNull [] primitive, @NotNull PersistentDataAdapterContext context) {
            Objects.requireNonNull(primitive, "primitive");
            Objects.requireNonNull(context, "context");

            long started = System.nanoTime();
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(primitive));
                int version = in.readInt();
                if (version < Data.MIN_VERSION || version > Data.VERSION) {
                    throw new IllegalStateException("Unsupported BlockEngine chunk data version: " + version);
                }

                Data data = new Data();
                int blockCount = readCount(in, 1);
                for (int i = 0; i < blockCount; i++) {
                    StoredBlock block = readBlock(in, version);
                    data.setBlock(block);
                }
                if (version >= 6) {
                    int displayCount = readCount(in, 16);
                    for (int i = 0; i < displayCount; i++) {
                        data.setDisplay(readDisplay(in));
                    }
                }
                if (in.available() != 0) {
                    throw new IOException("Unexpected trailing bytes in BlockEngine chunk data");
                }
                PerformanceMetrics.record(PerformanceMetrics.CHUNK_DECODE, System.nanoTime() - started,
                        data.blocks.size(), primitive.length);
                return data;
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to decode BlockEngine chunk data.", exception);
            }
        }

        // This stream is always backed by the chunk's in-memory byte array.
        private static int readCount(DataInputStream in, int minimumBytesPerEntry) throws IOException {
            int count = in.readInt();
            if (count < 0 || count > in.available() / minimumBytesPerEntry) {
                throw new IOException("Invalid entry count or byte length in chunk data: " + count);
            }
            return count;
        }

        private static void writeBlock(@NotNull DataOutputStream out, @NotNull StoredBlock block, int version) throws IOException {
            long started = System.nanoTime();
            out.writeByte(block.localX());
            out.writeInt(block.y());
            out.writeByte(block.localZ());
            out.writeUTF(block.fallbackBlock().name());
            out.writeFloat(block.hardness());
            out.writeFloat(block.miningSpeed());
            out.writeBoolean(block.unbreakable());
            out.writeBoolean(block.dropsItem());
            out.writeBoolean(block.dropInCreative());
            if (version > Data.VERSION_BEFORE_ORIENTATION) {
                out.writeUTF(block.orientation().name());
            }
            writeBlockData(out, block.data());

            byte[] payload = block.payload();
            out.writeInt(payload.length);
            out.write(payload);

            out.writeInt(block.displays().size());
            for (StoredDisplay display : block.displays()) {
                writeDisplay(out, display);
            }
            PerformanceMetrics.record(PerformanceMetrics.BLOCK_WRITE, System.nanoTime() - started, 1, payload.length);
        }

        private static @NotNull StoredBlock readBlock(@NotNull DataInputStream in, int version) throws IOException {
            long started = System.nanoTime();
            int localX = in.readUnsignedByte();
            int y = in.readInt();
            int localZ = in.readUnsignedByte();
            Material fallbackBlock = readMaterial(in.readUTF());
            float hardness = in.readFloat();
            float miningSpeed = in.readFloat();
            if (version < 5) {
                in.readBoolean();
            }
            boolean unbreakable = version >= 3 && in.readBoolean();
            boolean dropsItem = version < 3 || in.readBoolean();
            boolean dropInCreative = version >= 4 && in.readBoolean();
            BlockOrientation orientation = version > Data.VERSION_BEFORE_ORIENTATION
                    ? BlockOrientation.valueOf(in.readUTF())
                    : BlockOrientation.IDENTITY;
            SimpleBlockData data = readBlockData(in);
            int payloadLength = in.readInt();
            if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES || payloadLength > in.available()) {
                throw new IOException("Invalid adapter payload length: " + payloadLength);
            }
            byte[] payload = in.readNBytes(payloadLength);
            if (payload.length != payloadLength) {
                throw new IOException("Truncated adapter payload");
            }
            List<StoredDisplay> displays = new ArrayList<>();
            if (version >= 6) {
                int displayCount = readCount(in, 16);
                for (int i = 0; i < displayCount; i++) {
                    displays.add(readDisplay(in));
                }
            }

            StoredBlock block = new StoredBlock(localX, y, localZ, fallbackBlock, hardness, miningSpeed,
                    unbreakable, dropsItem, dropInCreative, data, payload, displays, orientation);
            PerformanceMetrics.record(PerformanceMetrics.BLOCK_READ, System.nanoTime() - started, 1, payload.length);
            return block;
        }

        private static void writeDisplay(@NotNull DataOutputStream out, @NotNull StoredDisplay display) throws IOException {
            out.writeLong(display.id().getMostSignificantBits());
            out.writeLong(display.id().getLeastSignificantBits());
            out.writeUTF(display.persistence().name());
            out.writeBoolean(display.owner() != null);
            if (display.owner() != null) {
                writeLocationKey(out, display.owner());
            }
            out.writeBoolean(display.ownerKey() != null);
            if (display.ownerKey() != null) {
                out.writeUTF(display.ownerKey());
            }
            writeSpec(out, display.spec());
        }

        private static @NotNull StoredDisplay readDisplay(@NotNull DataInputStream in) throws IOException {
            UUID id = new UUID(in.readLong(), in.readLong());
            DisplayPersistence persistence = DisplayPersistence.valueOf(in.readUTF());
            BlockLocationKey owner = in.readBoolean() ? readLocationKey(in) : null;
            String ownerKey = in.readBoolean() ? in.readUTF() : null;
            return new StoredDisplay(id, persistence, owner, ownerKey, readSpec(in));
        }

        private static void writeSpec(@NotNull DataOutputStream out, @NotNull DisplaySpec spec) throws IOException {
            out.writeLong(spec.worldId().getMostSignificantBits());
            out.writeLong(spec.worldId().getLeastSignificantBits());
            out.writeDouble(spec.x());
            out.writeDouble(spec.y());
            out.writeDouble(spec.z());
            out.writeFloat(spec.yaw());
            out.writeFloat(spec.pitch());
            writeItemStack(out, spec.itemStack());
            out.writeBoolean(spec.itemModel() != null);
            if (spec.itemModel() != null) {
                out.writeUTF(spec.itemModel().toString());
            }
            out.writeByte(spec.displayContext());
            out.writeInt(spec.brightness());
            out.writeFloat(spec.scaleX());
            out.writeFloat(spec.scaleY());
            out.writeFloat(spec.scaleZ());
            out.writeFloat(spec.translationX());
            out.writeFloat(spec.translationY());
            out.writeFloat(spec.translationZ());
            out.writeFloat(spec.viewRange());
            out.writeFloat(spec.leftRotationX());
            out.writeFloat(spec.leftRotationY());
            out.writeFloat(spec.leftRotationZ());
            out.writeFloat(spec.leftRotationW());
            out.writeFloat(spec.rightRotationX());
            out.writeFloat(spec.rightRotationY());
            out.writeFloat(spec.rightRotationZ());
            out.writeFloat(spec.rightRotationW());
            out.writeByte(spec.billboard());
            out.writeInt(spec.transformationInterpolationDelay());
            out.writeInt(spec.transformationInterpolationDuration());
            out.writeInt(spec.posRotInterpolationDuration());
            out.writeFloat(spec.shadowRadius());
            out.writeFloat(spec.shadowStrength());
            out.writeFloat(spec.width());
            out.writeFloat(spec.height());
            out.writeBoolean(spec.glowing());
            out.writeBoolean(spec.invisible());
            out.writeInt(spec.glowColorOverride());
            out.writeUTF(spec.audience().mode().name());
            out.writeInt(spec.audience().players().size());
            for (UUID playerId : spec.audience().players()) {
                out.writeLong(playerId.getMostSignificantBits());
                out.writeLong(playerId.getLeastSignificantBits());
            }
        }

        private static @NotNull DisplaySpec readSpec(@NotNull DataInputStream in) throws IOException {
            UUID worldId = new UUID(in.readLong(), in.readLong());
            double x = in.readDouble();
            double y = in.readDouble();
            double z = in.readDouble();
            float yaw = in.readFloat();
            float pitch = in.readFloat();
            DisplaySpec.Builder builder = DisplaySpec.builder(worldId, x, y, z, yaw, pitch);
            builder.itemStack(readItemStack(in));
            if (in.readBoolean()) {
                builder.itemModel(NamespacedKey.fromString(in.readUTF()));
            }
            builder.displayContext(in.readByte())
                    .brightness(in.readInt())
                    .scale(in.readFloat(), in.readFloat(), in.readFloat())
                    .translation(in.readFloat(), in.readFloat(), in.readFloat())
                    .viewRange(in.readFloat())
                    .leftRotation(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat())
                    .rightRotation(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat())
                    .billboard(in.readByte())
                    .transformationInterpolationDelay(in.readInt())
                    .transformationInterpolationDuration(in.readInt())
                    .posRotInterpolationDuration(in.readInt())
                    .shadowRadius(in.readFloat())
                    .shadowStrength(in.readFloat())
                    .dimensions(in.readFloat(), in.readFloat())
                    .glowing(in.readBoolean())
                    .invisible(in.readBoolean())
                    .glowColorOverride(in.readInt());
            DisplayAudience.Mode mode = DisplayAudience.Mode.valueOf(in.readUTF());
            int audienceSize = readCount(in, 16);
            Set<UUID> audiencePlayers = new HashSet<>();
            for (int i = 0; i < audienceSize; i++) {
                audiencePlayers.add(new UUID(in.readLong(), in.readLong()));
            }
            builder.audience(switch (mode) {
                case EVERYONE -> DisplayAudience.everyone();
                case INCLUDE -> DisplayAudience.only(audiencePlayers);
                case EXCLUDE -> DisplayAudience.except(audiencePlayers);
            });
            return builder.build();
        }

        private static void writeLocationKey(@NotNull DataOutputStream out, @NotNull BlockLocationKey key) throws IOException {
            out.writeLong(key.worldId().getMostSignificantBits());
            out.writeLong(key.worldId().getLeastSignificantBits());
            out.writeInt(key.x());
            out.writeInt(key.y());
            out.writeInt(key.z());
        }

        private static @NotNull BlockLocationKey readLocationKey(@NotNull DataInputStream in) throws IOException {
            return new BlockLocationKey(new UUID(in.readLong(), in.readLong()), in.readInt(), in.readInt(), in.readInt());
        }

        private static void writeItemStack(@NotNull DataOutputStream out, @NotNull ItemStack stack) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream objectOut = new BukkitObjectOutputStream(bytes)) {
                objectOut.writeObject(stack);
            }
            byte[] encoded = bytes.toByteArray();
            out.writeInt(encoded.length);
            out.write(encoded);
        }

        private static @NotNull ItemStack readItemStack(@NotNull DataInputStream in) throws IOException {
            byte[] encoded = in.readNBytes(readCount(in, 1));
            try (BukkitObjectInputStream objectIn = new BukkitObjectInputStream(new ByteArrayInputStream(encoded))) {
                Object object = objectIn.readObject();
                return object instanceof ItemStack stack ? stack : new ItemStack(Material.AIR);
            } catch (ClassNotFoundException exception) {
                throw new IOException("Failed to decode display item stack.", exception);
            }
        }

        private static @NotNull Material readMaterial(@NotNull String name) {
            Material material = Material.matchMaterial(name);
            return material == null ? Material.STONE : material;
        }

        private static void writeBlockData(@NotNull DataOutputStream out, @NotNull SimpleBlockData data) throws IOException {
            out.writeUTF(data.blockId());
            out.writeUTF(data.stateId());
            writeStringMap(out, data.stringData());
            writeIntMap(out, data.intData());
            writeBooleanMap(out, data.booleanData());
        }

        private static @NotNull SimpleBlockData readBlockData(@NotNull DataInputStream in) throws IOException {
            SimpleBlockData data = new SimpleBlockData(in.readUTF(), in.readUTF());
            data.stringData().putAll(readStringMap(in));
            data.intData().putAll(readIntMap(in));
            data.booleanData().putAll(readBooleanMap(in));
            return data;
        }

        private static void writeStringMap(@NotNull DataOutputStream out, @NotNull Map<String, String> map) throws IOException {
            out.writeInt(map.size());
            for (Map.Entry<String, String> entry : map.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeUTF(entry.getValue());
            }
        }

        private static @NotNull Map<String, String> readStringMap(@NotNull DataInputStream in) throws IOException {
            int size = readCount(in, 4);
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                map.put(in.readUTF(), in.readUTF());
            }
            return map;
        }

        private static void writeIntMap(@NotNull DataOutputStream out, @NotNull Map<String, Integer> map) throws IOException {
            out.writeInt(map.size());
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeInt(entry.getValue());
            }
        }

        private static @NotNull Map<String, Integer> readIntMap(@NotNull DataInputStream in) throws IOException {
            int size = readCount(in, 6);
            Map<String, Integer> map = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                map.put(in.readUTF(), in.readInt());
            }
            return map;
        }

        private static void writeBooleanMap(@NotNull DataOutputStream out, @NotNull Map<String, Boolean> map) throws IOException {
            out.writeInt(map.size());
            for (Map.Entry<String, Boolean> entry : map.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeBoolean(entry.getValue());
            }
        }

        private static @NotNull Map<String, Boolean> readBooleanMap(@NotNull DataInputStream in) throws IOException {
            int size = readCount(in, 3);
            Map<String, Boolean> map = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                map.put(in.readUTF(), in.readBoolean());
            }
            return map;
        }
    }
}
