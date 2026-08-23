package dev.auto.blockengine.datapack;

import com.fasterxml.jackson.databind.JsonNode;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class BlockPackLoader {
    private static final int FORMAT = 1;
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9._-]+(?:/[a-z0-9._-]+)*$");

    private BlockPackLoader() {
    }

    public static @NotNull Result load(@NotNull Path packsRoot) {
        Objects.requireNonNull(packsRoot, "packsRoot");
        if (!Files.isDirectory(packsRoot)) {
            return new Result(List.of(), List.of());
        }

        List<BlockPack> packs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (Stream<Path> children = Files.list(packsRoot)) {
            for (Path folder : children.filter(Files::isDirectory).sorted().toList()) {
                Path packFile = folder.resolve("pack.json");
                if (!Files.isRegularFile(packFile)) {
                    continue;
                }
                try {
                    packs.add(loadPack(folder, packFile));
                } catch (RuntimeException | IOException exception) {
                    errors.add(packFile + ": " + exception.getMessage());
                }
            }
        } catch (IOException exception) {
            errors.add(packsRoot + ": " + exception.getMessage());
        }

        return new Result(packs, errors);
    }

    private static @NotNull BlockPack loadPack(@NotNull Path folder, @NotNull Path packFile) throws IOException {
        JsonNode root = Main.getJsonMapper().readTree(packFile.toFile());
        int format = intValue(root, "format", FORMAT);
        if (format != FORMAT) {
            throw new IllegalArgumentException("Unsupported pack format " + format + "; expected " + FORMAT + ".");
        }

        String namespace = requiredText(root, "namespace", packFile);
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid namespace '" + namespace + "'.");
        }

        Path normalizedFolder = folder.toAbsolutePath().normalize();
        Path icon = null;
        if (root.hasNonNull("icon")) {
            Path iconPath = requiredPath(normalizedFolder, root.path("icon").asText(), packFile);
            if (Files.isRegularFile(iconPath)) {
                icon = iconPath;
            }
        } else {
            Path defaultIcon = normalizedFolder.resolve("pack.png").normalize();
            if (Files.isRegularFile(defaultIcon)) {
                icon = defaultIcon;
            }
        }
        List<Path> assetRoots = new ArrayList<>();
        JsonNode assets = root.path("assets");
        if (assets.isArray()) {
            for (JsonNode asset : assets) {
                assetRoots.add(requiredPath(normalizedFolder, asset.asText(), packFile));
            }
        } else {
            Path defaultAssets = normalizedFolder.resolve("assets").normalize();
            if (Files.exists(defaultAssets)) {
                assetRoots.add(defaultAssets);
            }
        }

        boolean packCatalog = boolValue(root, "catalog", true);
        List<BlockPackBlock> blocks = loadBlocks(normalizedFolder, packCatalog);
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("Pack does not define any blocks in blocks/**/*.json.");
        }

        return new BlockPack(
                normalizedFolder,
                namespace,
                textValue(root, "title", ""),
                textValue(root, "description", ""),
                textValue(root, "prompt", ""),
                textValue(root, "url-ending", namespace),
                boolValue(root, "required", true),
                packCatalog,
                icon,
                assetRoots,
                blocks
        );
    }

    private static @NotNull List<BlockPackBlock> loadBlocks(@NotNull Path folder, boolean packCatalog) throws IOException {
        Path blocksRoot = folder.resolve("blocks").normalize();
        if (!Files.isDirectory(blocksRoot)) {
            return List.of();
        }

        List<BlockPackBlock> blocks = new ArrayList<>();
        try (Stream<Path> files = Files.walk(blocksRoot)) {
            for (Path file : files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted()
                    .toList()) {
                try {
                    blocks.add(loadBlock(blocksRoot, file, packCatalog));
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException(file + ": " + exception.getMessage(), exception);
                }
            }
        }
        return blocks;
    }

    private static @NotNull BlockPackBlock loadBlock(
            @NotNull Path blocksRoot,
            @NotNull Path file,
            boolean packCatalog
    ) throws IOException {
        JsonNode root = Main.getJsonMapper().readTree(file.toFile());
        String fallbackName = blocksRoot.relativize(file).toString().replace('\\', '/');
        fallbackName = fallbackName.substring(0, fallbackName.length() - ".json".length());
        String name = textValue(root, "name", fallbackName);
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid block name '" + name + "'.");
        }

        Material vanillaBlock = material(root, "vanilla-block", Material.BARRIER, true, file);
        BlockDefinition.Placement placement = enumValue(
                BlockDefinition.Placement.class,
                textValue(root, "placement", "none"),
                "placement",
                file
        );
        BlockPackBlock.Item item = item(root.path("item"), file);
        String defaultState = textValue(root, "default-state", "default");
        Map<String, BlockPackBlock.State> states = states(root.path("states"), vanillaBlock, file);
        if (!states.containsKey(defaultState)) {
            throw new IllegalArgumentException("Default state '" + defaultState + "' is not defined.");
        }

        return new BlockPackBlock(
                name,
                vanillaBlock,
                boolValue(root, "catalog", packCatalog),
                placement,
                item,
                defaultState,
                states
        );
    }

    private static @NotNull BlockPackBlock.Item item(@NotNull JsonNode node, @NotNull Path file) {
        Material material = material(node, "material", Material.KNOWLEDGE_BOOK, false, file);
        List<String> lore = new ArrayList<>();
        JsonNode loreNode = node.path("lore");
        if (loreNode.isArray()) {
            for (JsonNode line : loreNode) {
                lore.add(line.asText());
            }
        }
        return new BlockPackBlock.Item(
                material,
                nullableText(node, "name"),
                lore,
                boolValue(node, "glint", false),
                boolValue(node, "placeable", true)
        );
    }

    private static @NotNull Map<String, BlockPackBlock.State> states(
            @NotNull JsonNode node,
            @NotNull Material vanillaBlock,
            @NotNull Path file
    ) {
        if (!node.isObject() || node.isEmpty()) {
            throw new IllegalArgumentException("Block must define at least one state.");
        }

        Map<String, BlockPackBlock.State> states = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String stateId = entry.getKey();
            if (!NAME_PATTERN.matcher(stateId).matches()) {
                throw new IllegalArgumentException("Invalid state id '" + stateId + "'.");
            }
            states.put(stateId, state(entry.getValue(), vanillaBlock, file));
        });
        return states;
    }

    private static @NotNull BlockPackBlock.State state(
            @NotNull JsonNode node,
            @NotNull Material vanillaBlock,
            @NotNull Path file
    ) {
        BlockDefinition.Textures textures = textures(node.path("textures"));
        if (textures.isEmpty()) {
            throw new IllegalArgumentException("State must define at least one texture.");
        }

        JsonNode movement = node.path("movement");
        return new BlockPackBlock.State(
                floatValue(node, "hardness", 0.5f),
                floatValue(node, "mining-speed", 1.0f),
                material(node, "mining-profile", vanillaBlock, true, file),
                preferredTools(node.path("preferred-tools"), file),
                boolValue(node, "require-preferred-tool-for-drops", false),
                boolValue(node, "require-silk-touch-for-drops", false),
                new BlockDefinition.Movement(
                        boolValue(movement, "gravity", false),
                        boolValue(movement, "dispenser-placeable", true),
                        boolValue(movement, "breaks-via-gravity", false)
                ),
                boolValue(node, "unbreakable", false),
                boolValue(node, "drops-item", true),
                boolValue(node, "drop-in-creative", false),
                textures,
                sounds(node.path("sounds"))
        );
    }

    private static @NotNull Set<BlockDefinition.ToolType> preferredTools(@NotNull JsonNode node, @NotNull Path file) {
        EnumSet<BlockDefinition.ToolType> tools = EnumSet.noneOf(BlockDefinition.ToolType.class);
        if (!node.isArray()) {
            return tools;
        }
        for (JsonNode value : node) {
            tools.add(enumValue(BlockDefinition.ToolType.class, value.asText(), "preferred-tools", file));
        }
        return tools;
    }

    private static @NotNull BlockDefinition.Textures textures(@NotNull JsonNode node) {
        return new BlockDefinition.Textures(
                nullableText(node, "all"),
                nullableText(node, "side"),
                nullableText(node, "front"),
                nullableText(node, "top"),
                nullableText(node, "bottom"),
                nullableText(node, "north"),
                nullableText(node, "south"),
                nullableText(node, "east"),
                nullableText(node, "west")
        );
    }

    private static @NotNull BlockDefinition.Sounds sounds(@NotNull JsonNode node) {
        return new BlockDefinition.Sounds(
                nullableText(node, "place"),
                nullableText(node, "break"),
                textValue(node, "mining", "minecraft:block.stone.hit"),
                nullableText(node, "step"),
                nullableText(node, "hit"),
                nullableText(node, "fall")
        );
    }

    private static @NotNull Material material(
            @NotNull JsonNode node,
            @NotNull String field,
            @NotNull Material fallback,
            boolean block,
            @NotNull Path file
    ) {
        String raw = textValue(node, field, fallback.name());
        Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        if (material == null) {
            throw new IllegalArgumentException(file + ": Unknown material '" + raw + "' for " + field + ".");
        }
        if (block && !material.isBlock()) {
            throw new IllegalArgumentException(file + ": Material '" + raw + "' for " + field + " is not a block.");
        }
        if (!block && !material.isItem()) {
            throw new IllegalArgumentException(file + ": Material '" + raw + "' for " + field + " is not an item.");
        }
        return material;
    }

    private static <T extends Enum<T>> @NotNull T enumValue(
            @NotNull Class<T> type,
            @NotNull String raw,
            @NotNull String field,
            @NotNull Path file
    ) {
        String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(file + ": Unknown " + field + " value '" + raw + "'.", exception);
        }
    }

    private static @NotNull Path requiredPath(
            @NotNull Path folder,
            @NotNull String raw,
            @NotNull Path file
    ) {
        if (raw.isBlank()) {
            throw new IllegalArgumentException(file + ": Asset path cannot be blank.");
        }
        Path path = folder.resolve(raw).normalize();
        if (!path.startsWith(folder)) {
            throw new IllegalArgumentException(file + ": Path escapes pack folder: " + raw);
        }
        return path;
    }

    private static @NotNull String requiredText(@NotNull JsonNode node, @NotNull String field, @NotNull Path file) {
        String value = textValue(node, field, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException(file + ": Missing required field '" + field + "'.");
        }
        return value;
    }

    private static @NotNull String textValue(@NotNull JsonNode node, @NotNull String field, @NotNull String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asText(fallback);
    }

    private static @Nullable String nullableText(@NotNull JsonNode node, @NotNull String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static boolean boolValue(@NotNull JsonNode node, @NotNull String field, boolean fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asBoolean(fallback);
    }

    private static int intValue(@NotNull JsonNode node, @NotNull String field, int fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asInt(fallback);
    }

    private static float floatValue(@NotNull JsonNode node, @NotNull String field, float fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : (float) value.asDouble(fallback);
    }

    public record Result(@NotNull List<BlockPack> packs, @NotNull List<String> errors) {
        public Result {
            packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        }
    }
}
