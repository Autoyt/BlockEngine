package dev.auto.blockengine.datapack;

import dev.auto.blockengine.api.drops.Fortune;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPackDropLoaderTest {
    @TempDir
    Path tempDir;
    private int fixtureIndex;

    @Test
    void omittedDropsRetainLegacyDefaultsAndEmptyDropsRemainExplicit() throws IOException {
        BlockPackBlock.State legacy = state("");
        assertNull(legacy.drops());
        assertFalse(legacy.silkTouchSelf());
        assertTrue(legacy.dropsItem());

        BlockPackBlock.State empty = state(", \"drops\": []");
        assertEquals(List.of(), empty.drops());
        assertFalse(empty.silkTouchSelf());

        BlockPackBlock.State oldConstructor = new BlockPackBlock.State(
                legacy.hardness(), legacy.miningSpeed(), legacy.miningProfile(), legacy.preferredTools(),
                legacy.requirePreferredToolForDrops(), legacy.requireSilkTouchForDrops(), legacy.movement(),
                legacy.unbreakable(), legacy.dropsItem(), legacy.dropInCreative(), legacy.textures(), legacy.sounds()
        );
        assertNull(oldConstructor.drops());
        assertFalse(oldConstructor.silkTouchSelf());
    }

    @Test
    void parsesIndependentMixedEntriesAndDefaults() throws IOException {
        BlockPackBlock.State state = state("""
                , "silk-touch-self": true,
                "drops": [
                  {"item": "self"},
                  {"item": {"material": "raw_iron"}, "count": {"min": 1, "max": 4},
                   "chance": 0.25, "fortune": "ore-multiplier"},
                  {"item": {"block": "future:blocks/ruby", "state": "facing/north"},
                   "count": 3, "chance": 1, "fortune": "UNIFORM_BONUS"},
                  {"item": {"block": "future:other"}, "count": 0, "chance": 0, "fortune": "NoNe"}
                ]
                """);

        assertTrue(state.silkTouchSelf());
        assertEquals(4, state.drops().size());
        BlockPackBlock.Drop self = state.drops().get(0);
        assertTrue(self.item().self());
        assertNull(self.item().material());
        assertNull(self.item().blockId());
        assertEquals(new BlockPackBlock.DropCount(1, 1), self.count());
        assertEquals(1, self.chance());
        assertEquals(Fortune.NONE, self.fortune());

        BlockPackBlock.Drop ore = state.drops().get(1);
        assertEquals(Material.RAW_IRON, ore.item().material());
        assertEquals(new BlockPackBlock.DropCount(1, 4), ore.count());
        assertEquals(0.25, ore.chance());
        assertEquals(Fortune.ORE_MULTIPLIER, ore.fortune());

        BlockPackBlock.Drop reference = state.drops().get(2);
        assertEquals("future:blocks/ruby", reference.item().blockId());
        assertEquals("facing/north", reference.item().stateId());
        assertEquals(new BlockPackBlock.DropCount(3, 3), reference.count());
        assertEquals(Fortune.UNIFORM_BONUS, reference.fortune());
        assertNull(state.drops().get(3).item().stateId());
        assertEquals(new BlockPackBlock.DropCount(0, 0), state.drops().get(3).count());
        assertEquals(0, state.drops().get(3).chance());
        assertThrows(UnsupportedOperationException.class, () -> state.drops().clear());
    }

    @Test
    void settingsRemainIsolatedByStateAndAcceptFullIntegerBounds() throws IOException {
        BlockPackBlock block = block(load("""
                {"states": {
                  "default": {"textures": {"all": "base"}},
                  "empty": {"textures": {"all": "base"}, "drops": [], "silk-touch-self": true},
                  "full": {"textures": {"all": "base"}, "drops": [
                    {"item": {"material": "RAW_GOLD"}, "count": {"min": 0, "max": 2147483647}}
                  ]}
                }}
                """));

        assertNull(block.states().get("default").drops());
        assertFalse(block.states().get("default").silkTouchSelf());
        assertEquals(List.of(), block.states().get("empty").drops());
        assertTrue(block.states().get("empty").silkTouchSelf());
        assertFalse(block.states().get("full").silkTouchSelf());
        assertEquals(new BlockPackBlock.DropCount(0, Integer.MAX_VALUE),
                block.states().get("full").drops().getFirst().count());
    }

    @Test
    void rejectsWrongStateSettingTypesAndMalformedEntries() throws IOException {
        for (String value : new String[]{"null", "{}", "1", "true", "\"self\""}) {
            rejects(", \"drops\": " + value, "states.default.drops");
        }
        for (String value : new String[]{"null", "0", "\"true\"", "[]", "{}"}) {
            rejects(", \"silk-touch-self\": " + value, "states.default.silk-touch-self");
        }
        for (String entry : new String[]{"null", "1", "[]", "\"self\""}) {
            rejects(", \"drops\": [" + entry + "]", "states.default.drops[0]");
        }
        rejects(", \"drops\": [{}]", "states.default.drops[0].item");
        rejects(", \"drops\": [{\"item\": \"self\", \"extra\": 1}]", "states.default.drops[0].extra");
    }

    @Test
    void rejectsInvalidItemSourcesWithSpecificPaths() throws IOException {
        for (String item : new String[]{"null", "1", "true", "[]", "{}", "\"diamond\"", "\"SELF\"",
                "{\"self\": true}", "{\"material\": \"stone\", \"block\": \"other:block\"}"}) {
            rejectsItem(item, "item");
        }
        for (String material : new String[]{"null", "1", "\"missing_material\"", "\"air\"",
                "\"cave_air\"", "\"void_air\"", "\"minecraft:stone\"", "\" \""}) {
            rejectsItem("{\"material\": " + material + "}", "item.material");
        }
        rejectsItem("{\"material\": \"stone\", \"state\": \"default\"}", "item.state");
        rejectsItem("{\"block\": \"other:block\", \"extra\": 1}", "item.extra");

        for (String id : new String[]{"null", "1", "\"stone\"", "\"Bad:block\"", "\"a:b:c\"",
                "\"a:/b\"", "\"a:b//c\"", "\"a:b/\"", "\"a:bad name\""}) {
            rejectsItem("{\"block\": " + id + "}", "item.block");
        }
        for (String state : new String[]{"null", "1", "\"\"", "\"UPPER\"", "\"a:b\"", "\"a//b\""}) {
            rejectsItem("{\"block\": \"other:block\", \"state\": " + state + "}", "item.state");
        }
    }

    @Test
    void rejectsInvalidCountsAndRangesWithoutCoercion() throws IOException {
        for (String count : new String[]{"null", "true", "\"2\"", "[]", "1.0", "1.5", "-1", "2147483648"}) {
            rejectsEntryField("count", count, "count");
        }
        rejectsEntryField("count", "{}", "count.min");
        rejectsEntryField("count", "{\"min\": 1}", "count.max");
        rejectsEntryField("count", "{\"max\": 1}", "count.min");
        rejectsEntryField("count", "{\"min\": 2, \"max\": 1}", "count.max");
        rejectsEntryField("count", "{\"min\": 0, \"max\": 1, \"extra\": 0}", "count.extra");

        for (String value : new String[]{"null", "\"1\"", "1.5", "-1", "2147483648"}) {
            rejectsEntryField("count", "{\"min\": " + value + ", \"max\": 3}", "count.min");
            rejectsEntryField("count", "{\"min\": 0, \"max\": " + value + "}", "count.max");
        }
    }

    @Test
    void rejectsInvalidChancesAndFortuneValuesWithoutCoercion() throws IOException {
        for (String chance : new String[]{"null", "true", "\"0.5\"", "[]", "{}", "-0.1", "1.1", "1e309"}) {
            rejectsEntryField("chance", chance, "chance");
        }
        for (String fortune : new String[]{"null", "1", "true", "[]", "{}", "\"\"", "\"unknown\""}) {
            rejectsEntryField("fortune", fortune, "fortune");
        }
    }

    @Test
    void diagnosticsIdentifyTheStateEntryAndBlockFile() throws IOException {
        BlockPackLoader.Result result = load("""
                {"states": {
                  "default": {"textures": {"all": "base"}},
                  "ripe": {"textures": {"all": "base"}, "drops": [
                    {"item": "self"},
                    {"item": {"material": "raw_iron"}, "count": {"min": 1, "max": "four"}}
                  ]}
                }}
                """);
        assertError(result, "states.ripe.drops[1].count.max");
    }

    @Test
    void readsDropsFromZipPacks() throws IOException {
        Path root = tempDir.resolve("zip_case");
        Files.createDirectories(root);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(root.resolve("example.zip")))) {
            zipEntry(output, "pack.json", "{\"format\": 1, \"namespace\": \"example\"}");
            zipEntry(output, "blocks/block.json", document("""
                    , "silk-touch-self": true,
                    "drops": [{"item": {"material": "raw_iron"}, "count": {"min": 2, "max": 5},
                               "fortune": "ore_multiplier"}]
                    """));
        }

        BlockPackBlock.State state = block(BlockPackLoader.load(root)).states().get("default");
        assertTrue(state.silkTouchSelf());
        assertEquals(new BlockPackBlock.DropCount(2, 5), state.drops().getFirst().count());
        assertEquals(Fortune.ORE_MULTIPLIER, state.drops().getFirst().fortune());
    }

    @Test
    void generatedBundledSampleContainsBothLegacyAndFortuneBlocks() throws IOException {
        Path root = tempDir.resolve("bundled_sample");
        Files.createDirectories(root);
        try (InputStream sample = getClass().getResourceAsStream("/expansion-packs/sample-expansion-pack.zip")) {
            assertNotNull(sample, "The generated sample ZIP must be packaged as a resource.");
            Files.copy(sample, root.resolve("sample.zip"));
        }

        BlockPackLoader.Result result = BlockPackLoader.load(root);
        assertEquals(List.of(), result.errors());
        assertEquals(1, result.packs().size());
        List<BlockPackBlock> blocks = result.packs().getFirst().blocks();
        BlockPackBlock ruby = blocks.stream().filter(block -> block.name().equals("ruby_block")).findFirst().orElseThrow();
        BlockPackBlock ore = blocks.stream().filter(block -> block.name().equals("fortune_ore")).findFirst().orElseThrow();

        assertNull(ruby.states().get("default").drops());
        BlockPackBlock.State state = ore.states().get("default");
        assertTrue(state.silkTouchSelf());
        assertEquals(2, state.drops().size());
        assertEquals(Material.RAW_IRON, state.drops().getFirst().item().material());
        assertEquals(new BlockPackBlock.DropCount(1, 3), state.drops().getFirst().count());
        assertEquals(Fortune.ORE_MULTIPLIER, state.drops().getFirst().fortune());
        assertEquals(Material.FLINT, state.drops().get(1).item().material());
        assertEquals(0.25, state.drops().get(1).chance());
    }

    private BlockPackBlock.State state(String fields) throws IOException {
        return block(load(document(fields))).states().get("default");
    }

    private void rejectsItem(String item, String expectedField) throws IOException {
        rejects(", \"drops\": [{\"item\": " + item + "}]", "states.default.drops[0]." + expectedField);
    }

    private void rejectsEntryField(String field, String value, String expectedField) throws IOException {
        rejects(", \"drops\": [{\"item\": \"self\", \"" + field + "\": " + value + "}]",
                "states.default.drops[0]." + expectedField);
    }

    private void rejects(String fields, String expectedField) throws IOException {
        assertError(load(document(fields)), expectedField);
    }

    private static void assertError(BlockPackLoader.Result result, String expectedField) {
        assertEquals(0, result.packs().size());
        assertEquals(1, result.errors().size());
        String error = result.errors().getFirst();
        assertTrue(error.contains("block.json"), error);
        assertTrue(error.contains(expectedField), error);
    }

    private BlockPackLoader.Result load(String document) throws IOException {
        Path root = tempDir.resolve("case_" + fixtureIndex++);
        Path pack = root.resolve("example");
        Files.createDirectories(pack.resolve("blocks"));
        Files.writeString(pack.resolve("pack.json"), "{\"format\": 1, \"namespace\": \"example\"}");
        Files.writeString(pack.resolve("blocks/block.json"), document);
        return BlockPackLoader.load(root);
    }

    private static BlockPackBlock block(BlockPackLoader.Result result) {
        assertEquals(List.of(), result.errors());
        assertEquals(1, result.packs().size());
        assertEquals(1, result.packs().getFirst().blocks().size());
        return result.packs().getFirst().blocks().getFirst();
    }

    private static String document(String fields) {
        return "{\"states\": {\"default\": {\"textures\": {\"all\": \"block\"}" + fields + "}}}";
    }

    private static void zipEntry(ZipOutputStream output, String path, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(path));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
