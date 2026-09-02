package dev.auto.blockengine.creative;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreativeInventoryManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void readsGeneratedEnchantmentManifest() throws IOException {
        Files.writeString(tempDir.resolve("generated-creative-enchantments.json"), """
                [
                  {
                    "id": "example:ruby_block",
                    "enchantment": "example:creative/ruby_block",
                    "name": "ruby_block",
                    "display-name": "Ruby Block"
                  }
                ]
                """);

        var blocks = CreativeInventoryManager.bootstrapBlocks(tempDir);

        assertEquals(1, blocks.size());
        assertEquals("example:ruby_block", blocks.getFirst().id());
        assertEquals("Ruby Block", blocks.getFirst().displayName());
    }

    @Test
    void ignoresExpansionPacksUntilManifestIsWrittenOnStartup() throws IOException {
        Path pack = tempDir.resolve("expansion").resolve("packs").resolve("example");
        Files.createDirectories(pack.resolve("blocks"));
        Files.writeString(pack.resolve("pack.json"), """
                {
                  "format": 1,
                  "namespace": "example"
                }
                """);
        Files.writeString(pack.resolve("blocks").resolve("ruby_block.json"), """
                {
                  "states": {
                    "default": {
                      "textures": {
                        "all": "ruby_block"
                      }
                    }
                  }
                }
                """);

        assertEquals(0, CreativeInventoryManager.bootstrapBlocks(tempDir).size());
    }

    @Test
    void skipsInvalidManifestEntries() throws IOException {
        Files.writeString(tempDir.resolve("generated-creative-enchantments.json"), """
                [
                  {
                    "id": "Bad Namespace:hidden_block",
                    "enchantment": "example:creative/hidden_block",
                    "name": "hidden_block",
                    "display-name": "Hidden Block"
                  }
                ]
                """);

        assertEquals(0, CreativeInventoryManager.bootstrapBlocks(tempDir).size());
    }
}
