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
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.visibility.VisibilityConfig;
import dev.auto.blockengine.visibility.VisibilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
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

public final class ChunkEngine {
    private static final NamespacedKey CHUNK_DATA_KEY = new NamespacedKey(Main.getInstance(), "chunk_data");
    private static final Map<Key, LoadedChunk> chunks = new HashMap<>();
    private static final Map<Key, ChunkEdit> pendingChunks = new HashMap<>();
    private static final Map<BlockLocationKey, Block> changedBlocks = new HashMap<>();
    private static final List<Runnable> afterFlush = new ArrayList<>();
    private static boolean flushScheduled;

    private ChunkEngine() {
    }

    public static void load(@NotNull Chunk chunk, @NotNull VisibilityConfig config) {
        long started = System.nanoTime();
        Key key = Key.from(chunk);
        Data data = Data.load(chunk, CHUNK_DATA_KEY);

        LoadedChunk loaded = new LoadedChunk(key);
        World world = chunk.getWorld();
        for (StoredBlock block : data.blocks()) {
            int worldX = (chunk.getX() << 4) + block.localX();
            int worldZ = (chunk.getZ() << 4) + block.localZ();
            BlockLocationKey location = new BlockLocationKey(world.getUID(), worldX, block.y(), worldZ);
            boolean exposed = !config.exposureEnabled() || isExposed(world, worldX, block.y(), worldZ, config);
            loaded.add(new RuntimeBlockView(key, location, block.fallbackBlock(), block, exposed));
        }

        chunks.put(key, loaded);
        ManagedDisplayManager.getInstance().loadChunk(key, data);
        PerformanceMetrics.record(PerformanceMetrics.CHUNK_LOAD, System.nanoTime() - started, data.blocks().size(), 0);
    }

    public static void unload(@NotNull Chunk chunk) {
        Key key = Key.from(chunk);
        chunks.remove(key);
        ManagedDisplayManager.getInstance().unloadChunk(key);
    }

    public static @Nullable LoadedChunk get(@NotNull Key key) {
        return chunks.get(key);
    }

    public static @Nullable RuntimeBlockView getBlock(@NotNull BlockLocationKey location) {
        LoadedChunk chunk = chunks.get(new Key(location.worldId(), location.x() >> 4, location.z() >> 4));
        if (chunk == null) {
            return null;
        }

        return chunk.block(location.x() & 15, location.y(), location.z() & 15);
    }

    public static @NotNull Collection<LoadedChunk> chunks() {
        return Collections.unmodifiableCollection(chunks.values());
    }

    public static @NotNull NamespacedKey dataKey() {
        return CHUNK_DATA_KEY;
    }

    public static @NotNull Data data(@NotNull Chunk chunk) {
        return edit(chunk).data();
    }

    public static void changed(@NotNull Block block) {
        changedBlocks.put(location(block), block);
        edit(block.getChunk());
        scheduleFlush();
    }

    public static void changed(@NotNull Chunk chunk) {
        edit(chunk);
        scheduleFlush();
    }

    public static void afterFlush(@NotNull Runnable runnable) {
        afterFlush.add(runnable);
        scheduleFlush();
    }

    public static void flushNow() {
        if (pendingChunks.isEmpty() && changedBlocks.isEmpty() && afterFlush.isEmpty()) {
            flushScheduled = false;
            return;
        }

        Set<Key> touched = new HashSet<>();
        for (Map.Entry<Key, ChunkEdit> entry : pendingChunks.entrySet()) {
            long started = System.nanoTime();
            ChunkEdit edit = entry.getValue();
            int blockCount = edit.data().blocks().size();
            int displayCount = edit.data().displays().size();
            boolean empty = edit.data().isEmpty();
            BlockEngineEvents.call(new BlockEngineChunkSaveEvent(edit.chunk(), blockCount, displayCount, empty));
            Data.save(edit.chunk(), dataKey(), edit.data());
            PerformanceMetrics.record(PerformanceMetrics.CHUNK_SAVE, System.nanoTime() - started, blockCount, 0);
            BlockEngineEvents.call(new BlockEngineChunkSavedEvent(edit.chunk(), blockCount, displayCount, empty));
            load(edit.chunk(), VisibilityManager.getInstance().config());
            touched.add(entry.getKey());
        }

        for (Block block : changedBlocks.values()) {
            BlockUpdates.update(block);
        }

        if (!touched.isEmpty()) {
            VisibilityManager.getInstance().refreshPlayersNear(touched);
        }

        pendingChunks.clear();
        changedBlocks.clear();
        flushScheduled = false;

        List<Runnable> callbacks = new ArrayList<>(afterFlush);
        afterFlush.clear();
        for (Runnable callback : callbacks) {
            runAfterFlush(callback);
        }
    }

    public static void clear() {
        pendingChunks.clear();
        changedBlocks.clear();
        afterFlush.clear();
        flushScheduled = false;
    }

    private static @NotNull ChunkEdit edit(@NotNull Chunk chunk) {
        Key key = Key.from(chunk);
        return pendingChunks.computeIfAbsent(key, ignored -> new ChunkEdit(
                chunk,
                Data.load(chunk, dataKey())
        ));
    }

    private static void scheduleFlush() {
        if (flushScheduled) {
            return;
        }
        flushScheduled = true;
        Bukkit.getScheduler().runTask(Main.getInstance(), ChunkEngine::flushNow);
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

    private record ChunkEdit(@NotNull Chunk chunk, @NotNull Data data) {
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
        private final @NotNull List<RuntimeBlockView> blocks = new ArrayList<>();
        private final @NotNull List<RuntimeBlockView> exposedBlocks = new ArrayList<>();
        private final @NotNull Map<Long, RuntimeBlockView> byLocalPosition = new HashMap<>();

        public LoadedChunk(@NotNull Key key) {
            this.key = key;
        }

        public @NotNull Key key() {
            return key;
        }

        public void add(@NotNull RuntimeBlockView block) {
            blocks.add(block);
            byLocalPosition.put(localKey(block.location().x() & 15, block.location().y(), block.location().z() & 15), block);
            if (block.exposed()) {
                exposedBlocks.add(block);
            }
        }

        public @NotNull List<RuntimeBlockView> blocks() {
            return Collections.unmodifiableList(blocks);
        }

        public @NotNull List<RuntimeBlockView> exposedBlocks() {
            return Collections.unmodifiableList(exposedBlocks);
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
        public static final int VERSION = 6;
        private static final int MIN_VERSION = 2;
        public static final PersistentDataType<byte[], Data> TYPE = new DataType();

        private final @NotNull Map<Integer, StoredBlock> blocks = new LinkedHashMap<>();
        private final @NotNull Map<UUID, StoredDisplay> displays = new LinkedHashMap<>();

        public boolean isEmpty() {
            return blocks.isEmpty() && displays.isEmpty();
        }

        public @NotNull Collection<StoredBlock> blocks() {
            return Collections.unmodifiableCollection(blocks.values());
        }

        public @NotNull Collection<StoredDisplay> displays() {
            return Collections.unmodifiableCollection(displays.values());
        }

        public @Nullable StoredBlock blockAt(int localX, int y, int localZ) {
            return blocks.get(packBlockKey(localX, y, localZ));
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
            @NotNull List<StoredDisplay> displays
    ) {
        public StoredBlock {
            validateLocal(localX, y, localZ);
            Objects.requireNonNull(fallbackBlock, "fallbackBlock");
            Objects.requireNonNull(data, "data");
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
                    dropsItem, dropInCreative, data, payload, updated);
        }

        public @NotNull StoredBlock withoutDisplay(@NotNull UUID id) {
            return new StoredBlock(localX, y, localZ, fallbackBlock, hardness, miningSpeed, unbreakable,
                    dropsItem, dropInCreative, data, payload, displays.stream()
                    .filter(display -> !display.id().equals(id))
                    .toList());
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

            dev.auto.blockengine.api.blocks.BlockDefinition.State state = definition.state(source.stateId());
            SimpleBlockData data = SimpleBlockData.copyOf(source);
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
                    data,
                    payload,
                    List.of()
            );
        }

        public @NotNull StoredBlock refreshSnapshot(@NotNull dev.auto.blockengine.api.blocks.BlockDefinition definition) {
            StoredBlock refreshed = from(localX, y, localZ, data, definition, payload);
            return new StoredBlock(localX, y, localZ, refreshed.fallbackBlock(), refreshed.hardness(), refreshed.miningSpeed(),
                    refreshed.unbreakable(), refreshed.dropsItem(), refreshed.dropInCreative(), refreshed.data(),
                    refreshed.payload(), displays);
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

                out.writeInt(Data.VERSION);
                out.writeInt(complex.blocks.size());
                for (StoredBlock block : complex.blocks.values()) {
                    writeBlock(out, block);
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
                int blockCount = in.readInt();
                for (int i = 0; i < blockCount; i++) {
                    StoredBlock block = readBlock(in, version);
                    data.setBlock(block);
                }
                if (version >= 6) {
                    int displayCount = in.readInt();
                    for (int i = 0; i < displayCount; i++) {
                        data.setDisplay(readDisplay(in));
                    }
                }
                PerformanceMetrics.record(PerformanceMetrics.CHUNK_DECODE, System.nanoTime() - started,
                        data.blocks.size(), primitive.length);
                return data;
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to decode BlockEngine chunk data.", exception);
            }
        }

        private static void writeBlock(@NotNull DataOutputStream out, @NotNull StoredBlock block) throws IOException {
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
            SimpleBlockData data = readBlockData(in);
            byte[] payload = in.readNBytes(in.readInt());
            List<StoredDisplay> displays = new ArrayList<>();
            if (version >= 6) {
                int displayCount = in.readInt();
                for (int i = 0; i < displayCount; i++) {
                    displays.add(readDisplay(in));
                }
            }

            StoredBlock block = new StoredBlock(localX, y, localZ, fallbackBlock, hardness, miningSpeed,
                    unbreakable, dropsItem, dropInCreative, data, payload, displays);
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
            int audienceSize = in.readInt();
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
            byte[] encoded = in.readNBytes(in.readInt());
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
            int size = in.readInt();
            Map<String, String> map = new LinkedHashMap<>(size);
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
            int size = in.readInt();
            Map<String, Integer> map = new LinkedHashMap<>(size);
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
            int size = in.readInt();
            Map<String, Boolean> map = new LinkedHashMap<>(size);
            for (int i = 0; i < size; i++) {
                map.put(in.readUTF(), in.readBoolean());
            }
            return map;
        }
    }
}
