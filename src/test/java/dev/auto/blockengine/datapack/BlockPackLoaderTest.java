package dev.auto.blockengine.datapack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPackLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsInvalidNamespace() throws IOException {
        Path pack = tempDir.resolve("example");
        Files.createDirectories(pack.resolve("blocks"));
        Files.writeString(pack.resolve("pack.json"), """
                {
                  "format": 1,
                  "namespace": "Bad Namespace"
                }
                """);

        BlockPackLoader.Result result = BlockPackLoader.load(tempDir);

        assertEquals(0, result.packs().size());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("Invalid namespace"));
    }

    @Test
    void rejectsAssetPathsOutsidePackFolder() throws IOException {
        Path pack = tempDir.resolve("bad");
        Files.createDirectories(pack.resolve("blocks"));
        Files.writeString(pack.resolve("pack.json"), """
                {
                  "format": 1,
                  "namespace": "bad",
                  "assets": ["../outside"]
                }
                """);
        Files.writeString(pack.resolve("blocks").resolve("block.json"), """
                {
                  "states": {
                    "default": {
                      "textures": {
                        "all": "block"
                      }
                    }
                  }
                }
                """);

        BlockPackLoader.Result result = BlockPackLoader.load(tempDir);

        assertEquals(0, result.packs().size());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("Path escapes pack folder"));
    }

    @Test
    void rejectsInvalidDependencyNamespaces() throws IOException {
        Path pack = tempDir.resolve("bad_dependency");
        Files.createDirectories(pack.resolve("blocks"));
        Files.writeString(pack.resolve("pack.json"), """
                {
                  "format": 1,
                  "namespace": "bad_dependency",
                  "dependencies": ["Bad Namespace"]
                }
                """);

        BlockPackLoader.Result result = BlockPackLoader.load(tempDir);

        assertEquals(0, result.packs().size());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("Invalid dependency namespace"));
    }

    @Test
    void scansZipPackFiles() throws IOException {
        Path zip = tempDir.resolve("bad.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("pack.json"));
            output.write("""
                    {
                      "format": 1,
                      "namespace": "Bad Namespace"
                    }
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }

        BlockPackLoader.Result result = BlockPackLoader.load(tempDir, tempDir.resolve("extracted"));

        assertEquals(0, result.packs().size());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("Invalid namespace"));
    }

    @Test
    void creativeMenuDefaultsToTrue() throws IOException {
        Path pack = tempDir.resolve("creative_default");
        Files.createDirectories(pack.resolve("blocks"));
        Files.writeString(pack.resolve("pack.json"), """
                {
                  "format": 1,
                  "namespace": "creative_default"
                }
                """);
        Files.writeString(pack.resolve("blocks").resolve("block.json"), """
                {
                  "states": {
                    "default": {
                      "textures": {
                        "all": "block"
                      }
                    }
                  }
                }
                """);

        BlockPackLoader.Result result = BlockPackLoader.load(tempDir);

        assertEquals(1, result.packs().size());
        assertTrue(result.packs().getFirst().creativeMenu());
        assertTrue(result.packs().getFirst().blocks().getFirst().creativeMenu());
    }

    @Test
    void blockCreativeMenuOverridesPackCreativeMenu() throws IOException {
        Path pack = tempDir.resolve("creative_override");
        Files.createDirectories(pack.resolve("blocks"));
        Files.writeString(pack.resolve("pack.json"), """
                {
                  "format": 1,
                  "namespace": "creative_override",
                  "creative-menu": false
                }
                """);
        Files.writeString(pack.resolve("blocks").resolve("block.json"), """
                {
                  "creative-menu": true,
                  "states": {
                    "default": {
                      "textures": {
                        "all": "block"
                      }
                    }
                  }
                }
                """);

        BlockPackLoader.Result result = BlockPackLoader.load(tempDir);

        assertEquals(1, result.packs().size());
        assertTrue(result.packs().getFirst().blocks().getFirst().creativeMenu());
    }

    @Test
    void rejectsVanillaBlockOverride() throws IOException {
        Path pack = tempDir.resolve("vanilla_override");
        Files.createDirectories(pack.resolve("blocks"));
        Files.writeString(pack.resolve("pack.json"), """
                {
                  "format": 1,
                  "namespace": "vanilla_override"
                }
                """);
        Files.writeString(pack.resolve("blocks").resolve("block.json"), """
                {
                  "vanilla-block": "stone",
                  "states": {
                    "default": {
                      "textures": {
                        "all": "block"
                      }
                    }
                  }
                }
                """);

        BlockPackLoader.Result result = BlockPackLoader.load(tempDir);

        assertEquals(0, result.packs().size());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("'vanilla-block' is no longer supported"));
    }
}
