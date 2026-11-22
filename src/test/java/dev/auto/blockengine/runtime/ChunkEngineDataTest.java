package dev.auto.blockengine.runtime;

import dev.auto.blockengine.placement.BlockOrientation;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkEngineDataTest {
    private static final PersistentDataAdapterContext CONTEXT = () -> {
        throw new AssertionError("The byte-array format should not allocate nested containers");
    };

    @Test
    void currentFormatPreservesPayloadStateAndSignedHeight() {
        var state = new ChunkEngine.SimpleBlockData("demo:lamp", "on");
        state.stringData().put("owner", "player");
        state.intData().put("energy", 14);
        state.booleanData().put("lit", true);
        byte[] payload = {1, 2, 3};
        var block = new ChunkEngine.StoredBlock(15, -64, 0, Material.STONE,
                2.5f, 4.0f, false, true, false, state, payload, List.of());

        var data = new ChunkEngine.Data();
        data.setBlock(block);
        payload[0] = 0;
        byte[] bytes = ChunkEngine.Data.TYPE.toPrimitive(data, CONTEXT);
        var decoded = ChunkEngine.Data.TYPE.fromPrimitive(bytes, CONTEXT).blockAt(15, -64, 0);

        assertNotNull(decoded);
        assertEquals("on", decoded.stateId());
        assertEquals(Map.of("owner", "player"), decoded.data().stringData());
        assertEquals(Map.of("energy", 14), decoded.data().intData());
        assertEquals(Map.of("lit", true), decoded.data().booleanData());
        assertArrayEquals(new byte[]{1, 2, 3}, decoded.payload());
        assertThrows(UncheckedIOException.class, () ->
                ChunkEngine.Data.TYPE.fromPrimitive(Arrays.copyOf(bytes, bytes.length - 1), CONTEXT));
    }

    @Test
    void preservesEveryOrientationAcrossStorageAndDisplayUpdates() {
        var state = new ChunkEngine.SimpleBlockData("demo:log", "default");
        var base = new ChunkEngine.StoredBlock(0, 1, 2, Material.OAK_LOG, 1, 1,
                false, true, false, state, null, List.of());
        for (BlockOrientation orientation : BlockOrientation.values()) {
            var data = new ChunkEngine.Data();
            data.setBlock(base.withOrientation(orientation).withoutDisplay(java.util.UUID.randomUUID()));
            byte[] bytes = ChunkEngine.Data.TYPE.toPrimitive(data, CONTEXT);
            assertEquals(orientation == BlockOrientation.IDENTITY ? 6 : 7, ByteBuffer.wrap(bytes).getInt());
            var restored = ChunkEngine.Data.TYPE.fromPrimitive(bytes, CONTEXT).blockAt(0, 1, 2);
            assertNotNull(restored);
            assertEquals(orientation, restored.orientation());
        }
    }

    @Test
    void readsEachLegacyFlagLayout() throws IOException {
        for (int version = 2; version <= 6; version++) {
            var buffer = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(buffer)) {
                out.writeInt(version);
                out.writeInt(1);
                out.writeByte(1);
                out.writeInt(-16);
                out.writeByte(2);
                out.writeUTF("STONE");
                out.writeFloat(1.0f);
                out.writeFloat(2.0f);
                if (version < 5) {
                    out.writeBoolean(false);
                }
                if (version >= 3) {
                    out.writeBoolean(true);
                    out.writeBoolean(false);
                }
                if (version >= 4) {
                    out.writeBoolean(true);
                }
                out.writeUTF("demo:legacy");
                out.writeUTF("saved");
                out.writeInt(0);
                out.writeInt(0);
                out.writeInt(0);
                out.writeInt(1);
                out.writeByte(42);
                if (version >= 6) {
                    out.writeInt(0); // Block-attached displays
                    out.writeInt(0); // World displays
                }
            }

            var block = ChunkEngine.Data.TYPE.fromPrimitive(buffer.toByteArray(), CONTEXT).blockAt(1, -16, 2);
            assertNotNull(block);
            assertEquals(version >= 3, block.unbreakable());
            assertEquals(version < 3, block.dropsItem());
            assertEquals(version >= 4, block.dropInCreative());
            assertEquals("saved", block.stateId());
            assertEquals(BlockOrientation.IDENTITY, block.orientation());
            assertArrayEquals(new byte[]{42}, block.payload());
        }
    }

    @Test
    void rejectsNegativeCountsTrailingDataAndImpossibleSizes() {
        for (int count : new int[]{-1, Integer.MAX_VALUE}) {
            var invalid = ByteBuffer.allocate(12).putInt(6).putInt(count).putInt(0).array();
            assertThrows(UncheckedIOException.class, () -> ChunkEngine.Data.TYPE.fromPrimitive(invalid, CONTEXT));
        }
        var trailing = ByteBuffer.allocate(13).putInt(6).putInt(0).putInt(0).put((byte) 1).array();
        assertThrows(UncheckedIOException.class, () -> ChunkEngine.Data.TYPE.fromPrimitive(trailing, CONTEXT));
    }

    @Test
    void rejectsUnreloadableAdapterPayloadBeforeRecordingIt() {
        var state = new ChunkEngine.SimpleBlockData("demo:lamp", "on");
        assertThrows(IllegalArgumentException.class, () -> new ChunkEngine.StoredBlock(
                0, 0, 0, Material.STONE, 1, 1, false, true, false, state,
                new byte[ChunkEngine.MAX_PAYLOAD_BYTES + 1], List.of()));
    }
}
