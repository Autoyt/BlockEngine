package dev.auto.blockengine.runtime;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.visibility.VisibilityConfig;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
import java.util.UUID;

public final class ChunkEngine {
    private static final NamespacedKey CHUNK_DATA_KEY = new NamespacedKey(Main.getInstance(), "chunk_data");
    private static final Map<Key, LoadedChunk> chunks = new HashMap<>();

    private ChunkEngine() {
    }

    public static void load(@NotNull Chunk chunk, @NotNull VisibilityConfig config) {
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
    }

    public static void unload(@NotNull Chunk chunk) {
        chunks.remove(Key.from(chunk));
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
        public static final int VERSION = 5;
        private static final int MIN_VERSION = 2;
        public static final PersistentDataType<byte[], Data> TYPE = new DataType();

        private final @NotNull Map<Integer, StoredBlock> blocks = new LinkedHashMap<>();

        public boolean isEmpty() {
            return blocks.isEmpty();
        }

        public @NotNull Collection<StoredBlock> blocks() {
            return Collections.unmodifiableCollection(blocks.values());
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
            byte @NotNull [] payload
    ) {
        public StoredBlock {
            validateLocal(localX, y, localZ);
            Objects.requireNonNull(fallbackBlock, "fallbackBlock");
            Objects.requireNonNull(data, "data");
            payload = payload == null ? new byte[0] : payload.clone();
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
                    payload
            );
        }

        public @NotNull StoredBlock refreshSnapshot(@NotNull dev.auto.blockengine.api.blocks.BlockDefinition definition) {
            return from(localX, y, localZ, data, definition, payload);
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

            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);

                out.writeInt(Data.VERSION);
                out.writeInt(complex.blocks.size());
                for (StoredBlock block : complex.blocks.values()) {
                    writeBlock(out, block);
                }

                out.flush();
                return bytes.toByteArray();
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to encode BlockEngine chunk data.", exception);
            }
        }

        @Override
        public @NotNull Data fromPrimitive(byte @NotNull [] primitive, @NotNull PersistentDataAdapterContext context) {
            Objects.requireNonNull(primitive, "primitive");
            Objects.requireNonNull(context, "context");

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
                return data;
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to decode BlockEngine chunk data.", exception);
            }
        }

        private static void writeBlock(@NotNull DataOutputStream out, @NotNull StoredBlock block) throws IOException {
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
        }

        private static @NotNull StoredBlock readBlock(@NotNull DataInputStream in, int version) throws IOException {
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

            return new StoredBlock(localX, y, localZ, fallbackBlock, hardness, miningSpeed, unbreakable, dropsItem, dropInCreative, data, payload);
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
