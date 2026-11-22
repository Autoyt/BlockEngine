package dev.auto.blockengine.datapack;

import com.fasterxml.jackson.databind.JsonNode;
import dev.auto.blockengine.api.drops.Fortune;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict parsing for optional drop settings; legacy block fields keep their existing parser. */
final class BlockPackDropParser {
    private static final Pattern BLOCK_ID = Pattern.compile("^[a-z0-9._-]+:[a-z0-9._-]+(?:/[a-z0-9._-]+)*$");
    private static final Pattern STATE_ID = Pattern.compile("^[a-z0-9._-]+(?:/[a-z0-9._-]+)*$");
    private static final Pattern MATERIAL_NAME = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Set<String> ENTRY_KEYS = Set.of("item", "count", "chance", "fortune");
    private static final Set<String> ITEM_KEYS = Set.of("material", "block", "state");
    private static final Set<String> COUNT_KEYS = Set.of("min", "max");

    private BlockPackDropParser() {
    }

    static @Nullable List<BlockPackBlock.Drop> drops(
            @NotNull JsonNode state,
            @NotNull String stateId,
            @NotNull Path file
    ) {
        JsonNode node = state.get("drops");
        if (node == null) {
            return null;
        }

        String field = "states." + stateId + ".drops";
        if (!node.isArray()) {
            throw invalid(file, field, "Expected an array; omit this field to use the legacy self-drop.");
        }

        List<BlockPackBlock.Drop> drops = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            drops.add(entry(node.get(index), file, field + "[" + index + "]"));
        }
        return List.copyOf(drops);
    }

    static boolean silkTouchSelf(@NotNull JsonNode state, @NotNull String stateId, @NotNull Path file) {
        JsonNode value = state.get("silk-touch-self");
        if (value == null) {
            return false;
        }
        if (!value.isBoolean()) {
            throw invalid(file, "states." + stateId + ".silk-touch-self", "Expected a boolean.");
        }
        return value.booleanValue();
    }

    private static @NotNull BlockPackBlock.Drop entry(
            @NotNull JsonNode node,
            @NotNull Path file,
            @NotNull String field
    ) {
        object(node, ENTRY_KEYS, file, field);

        BlockPackBlock.DropItem item = item(node.path("item"), file, field + ".item");
        BlockPackBlock.DropCount count = node.has("count")
                ? count(node.get("count"), file, field + ".count")
                : new BlockPackBlock.DropCount(1, 1);
        double chance = node.has("chance") ? chance(node.get("chance"), file, field + ".chance") : 1;
        Fortune fortune = node.has("fortune")
                ? BlockPackLoader.enumValue(Fortune.class,
                        text(node.get("fortune"), file, field + ".fortune"), field + ".fortune", file)
                : Fortune.NONE;

        return new BlockPackBlock.Drop(item, count, chance, fortune);
    }

    private static @NotNull BlockPackBlock.DropItem item(
            @NotNull JsonNode node,
            @NotNull Path file,
            @NotNull String field
    ) {
        if (node.isTextual()) {
            if (!"self".equals(node.textValue())) {
                throw invalid(file, field, "The only string item source is 'self'.");
            }
            return new BlockPackBlock.DropItem(null, null, null, true);
        }

        object(node, ITEM_KEYS, file, field);
        boolean hasMaterial = node.has("material");
        boolean hasBlock = node.has("block");
        if (hasMaterial == hasBlock) {
            throw invalid(file, field, "Expected exactly one of 'material' or 'block'.");
        }

        if (hasMaterial) {
            if (node.has("state")) {
                throw invalid(file, field + ".state", "A state is only valid with a block item source.");
            }

            String raw = text(node.get("material"), file, field + ".material");
            if (!MATERIAL_NAME.matcher(raw).matches()) {
                throw invalid(file, field + ".material", "Expected a Bukkit material name such as 'raw_iron'.");
            }
            Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
            if (material == null) {
                throw invalid(file, field + ".material", "Unknown material '" + raw + "'.");
            }
            if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                throw invalid(file, field + ".material", "Air cannot be used as a drop item.");
            }
            BlockPackLoader.validateMaterialFamily(material, raw, field + ".material", false, file);
            return new BlockPackBlock.DropItem(material, null, null, false);
        }

        String blockId = text(node.get("block"), file, field + ".block");
        if (!BLOCK_ID.matcher(blockId).matches()) {
            throw invalid(file, field + ".block", "Expected a full lowercase block id such as 'example:ruby_ore'.");
        }

        String stateId = null;
        if (node.has("state")) {
            stateId = text(node.get("state"), file, field + ".state");
            if (!STATE_ID.matcher(stateId).matches()) {
                throw invalid(file, field + ".state", "Expected a lowercase state id.");
            }
        }
        return new BlockPackBlock.DropItem(null, blockId, stateId, false);
    }

    private static @NotNull BlockPackBlock.DropCount count(
            @NotNull JsonNode node,
            @NotNull Path file,
            @NotNull String field
    ) {
        if (node.isIntegralNumber()) {
            int count = nonnegativeInt(node, file, field);
            return new BlockPackBlock.DropCount(count, count);
        }

        object(node, COUNT_KEYS, file, field);
        int min = nonnegativeInt(node.path("min"), file, field + ".min");
        int max = nonnegativeInt(node.path("max"), file, field + ".max");
        if (max < min) {
            throw invalid(file, field + ".max", "Maximum count must be at least the minimum count.");
        }
        return new BlockPackBlock.DropCount(min, max);
    }

    private static double chance(@NotNull JsonNode node, @NotNull Path file, @NotNull String field) {
        if (!node.isNumber()) {
            throw invalid(file, field, "Expected a finite number from zero through one.");
        }

        double chance = node.doubleValue();
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
            throw invalid(file, field, "Expected a finite number from zero through one.");
        }
        return chance;
    }

    private static int nonnegativeInt(@NotNull JsonNode node, @NotNull Path file, @NotNull String field) {
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < 0) {
            throw invalid(file, field, "Expected an integer from zero through " + Integer.MAX_VALUE + ".");
        }
        return node.intValue();
    }

    private static @NotNull String text(@NotNull JsonNode node, @NotNull Path file, @NotNull String field) {
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw invalid(file, field, "Expected a nonblank string.");
        }
        return node.textValue();
    }

    private static void object(
            @NotNull JsonNode node,
            @NotNull Set<String> allowedKeys,
            @NotNull Path file,
            @NotNull String field
    ) {
        if (!node.isObject()) {
            throw invalid(file, field, "Expected an object.");
        }

        node.fieldNames().forEachRemaining(key -> {
            if (!allowedKeys.contains(key)) {
                throw invalid(file, field + "." + key, "Unknown field.");
            }
        });
    }

    private static @NotNull IllegalArgumentException invalid(
            @NotNull Path file,
            @NotNull String field,
            @NotNull String message
    ) {
        return new IllegalArgumentException(file + ": " + field + ": " + message);
    }
}
