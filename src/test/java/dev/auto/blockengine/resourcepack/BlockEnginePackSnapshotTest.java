package dev.auto.blockengine.resourcepack;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockEnginePackSnapshotTest {
    private static final String BOOK = "assets/minecraft/items/enchanted_book.json";
    private static final String LANGUAGE = "assets/example/lang/en_us.json";

    @TempDir
    Path temporary;

    @Test
    void initializesResourcePackManagerWithoutTheAutoLibApiAtRuntime() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.auto.autolib.pack.CentralPackService"));
        assertFalse(ResourcePackManager.getInstance().isUsingSharedBackend());
    }

    @Test
    void keepsAllCreativeRoutesAndMergesSystemAndDataAssets() throws Exception {
        GeneratedPack base = pack("base", false, Map.of(
                "pack.mcmeta", "metadata",
                BOOK, "complete model",
                LANGUAGE, "{\"base\":\"base value\", \"common\":\"base value\"}"));
        GeneratedPack system = pack("system", false, Map.of(
                BOOK, "system subset",
                LANGUAGE, "{\"system\":\"system value\", \"common\":\"system value\"}",
                "assets/example/models/system.json", "system model"));
        GeneratedPack data = pack("data", true, Map.of(
                BOOK, "data subset",
                "assets/example/models/data.json", "data model"));

        BlockEnginePackSnapshot snapshot = BlockEnginePackSnapshot.capture(List.of(base, system, data));
        Map<String, byte[]> entries = snapshot.readEntries();
        assertEquals("complete model", text(entries, BOOK));
        assertEquals("system model", text(entries, "assets/example/models/system.json"));
        assertEquals("data model", text(entries, "assets/example/models/data.json"));
        assertFalse(entries.containsKey("pack.mcmeta"));
        assertTrue(snapshot.required());

        var translations = new ObjectMapper().readTree(entries.get(LANGUAGE));
        assertEquals("base value", translations.get("base").asText());
        assertEquals("system value", translations.get("system").asText());
        assertEquals("system value", translations.get("common").asText());
    }

    @Test
    void reloadMakesANewSnapshotWhileThePreviousOneRemainsReadable() throws Exception {
        String path = "assets/example/models/item.json";
        BlockEnginePackSnapshot before = BlockEnginePackSnapshot.capture(List.of(pack("base", false, Map.of(path, "previous"))));
        BlockEnginePackSnapshot after = BlockEnginePackSnapshot.capture(List.of(pack("base", false, Map.of(path, "current"))));

        assertEquals("previous", text(before.readEntries(), path));
        assertEquals("current", text(after.readEntries(), path));
        assertNotEquals(before.revision(), after.revision());
    }

    private GeneratedPack pack(String name, boolean required, Map<String, String> files)
            throws IOException, NoSuchAlgorithmException {
        Path zip = temporary.resolve(name + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (var file : files.entrySet()) {
                output.putNextEntry(new ZipEntry(file.getKey()));
                output.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(zip));
        return new GeneratedPack(UUID.randomUUID(), temporary, zip, zip.getFileName().toString(), sha1,
                "http://localhost/" + name + ".zip", Component.empty(), Component.empty(), Component.empty(), required);
    }

    private static String text(Map<String, byte[]> entries, String path) {
        return new String(entries.get(path), StandardCharsets.UTF_8);
    }
}
