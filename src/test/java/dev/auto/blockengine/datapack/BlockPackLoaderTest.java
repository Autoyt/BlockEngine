package dev.auto.blockengine.datapack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
