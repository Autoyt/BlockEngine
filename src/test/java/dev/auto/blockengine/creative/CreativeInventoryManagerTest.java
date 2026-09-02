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
    void scansExpansionPacksForCreativeBlocks() throws IOException {
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
                  "item": {
                    "name": "<red>Ruby Block"
                  },
                  "states": {
                    "default": {
                      "textures": {
                        "all": "ruby_block"
                      }
                    }
                  }
                }
                """);

        var blocks = CreativeInventoryManager.bootstrapBlocks(tempDir);

        assertEquals(1, blocks.size());
        assertEquals("example:ruby_block", blocks.getFirst().id());
        assertEquals("Ruby Block", blocks.getFirst().displayName());
    }

    @Test
    void skipsBlocksOptedOutOfCreativeMenu() throws IOException {
        Path pack = tempDir.resolve("expansion").resolve("packs").resolve("example");
        Files.createDirectories(pack.resolve("blocks"));
        Files.writeString(tempDir.resolve("generated-creative-blocks.json"), """
                [
                  {
                    "id": "example:hidden_block",
                    "name": "hidden_block",
                    "display-name": "Hidden Block"
                  }
                ]
                """);
        Files.writeString(pack.resolve("pack.json"), """
                {
                  "format": 1,
                  "namespace": "example"
                }
                """);
        Files.writeString(pack.resolve("blocks").resolve("hidden_block.json"), """
                {
                  "creative-menu": false,
                  "states": {
                    "default": {
                      "textures": {
                        "all": "hidden_block"
                      }
                    }
                  }
                }
                """);

        assertEquals(0, CreativeInventoryManager.bootstrapBlocks(tempDir).size());
    }
}
