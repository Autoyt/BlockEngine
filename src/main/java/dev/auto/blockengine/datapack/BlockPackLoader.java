package dev.auto.blockengine.datapack;

import com.fasterxml.jackson.databind.JsonNode;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class BlockPackLoader {
    private static final int FORMAT = 1;
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9._-]+(?:/[a-z0-9._-]+)*$");

    private BlockPackLoader() {
    }

    public static @NotNull Result load(@NotNull Path packsRoot) {
        return load(packsRoot, packsRoot.resolve(".extracted"));
    }

    public static @NotNull Result load(@NotNull Path packsRoot, @NotNull Path extractedRoot) {
        Objects.requireNonNull(packsRoot, "packsRoot");
        Objects.requireNonNull(extractedRoot, "extractedRoot");
        if (!Files.isDirectory(packsRoot)) {
            return new Result(List.of(), List.of());
        }

        List<BlockPack> packs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try {
            delete(extractedRoot);
            Files.createDirectories(extractedRoot);
        } catch (IOException exception) {
            errors.add(extractedRoot + ": " + exception.getMessage());
        }

        try (Stream<Path> children = Files.list(packsRoot)) {
            for (Path source : children.sorted().toList()) {
                loadSource(source, extractedRoot, packs, errors);
            }
        } catch (IOException exception) {
            errors.add(packsRoot + ": " + exception.getMessage());
        }

        return new Result(packs, errors);
    }

    private static void loadSource(
            @NotNull Path source,
            @NotNull Path extractedRoot,
            @NotNull List<BlockPack> packs,
            @NotNull List<String> errors
    ) {
        try {
            if (Files.isDirectory(source)) {
                loadFolder(source, packs, errors);
                return;
            }
            if (Files.isRegularFile(source)
                    && source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                Path extracted = extractedRoot.resolve(safeName(source)).normalize();
                extractZip(source, extracted);
                loadFolder(locatePackFolder(extracted), packs, errors);
            }
        } catch (RuntimeException | IOException exception) {
            errors.add(source + ": " + exception.getMessage());
        }
    }

    private static void loadFolder(
            @NotNull Path folder,
            @NotNull List<BlockPack> packs,
            @NotNull List<String> errors
    ) {
        Path packFile = folder.resolve("pack.json");
        if (!Files.isRegularFile(packFile)) {
            return;
        }
        try {
            packs.add(loadPack(folder, packFile));
        } catch (RuntimeException | IOException exception) {
            errors.add(packFile + ": " + exception.getMessage());
        }
    }

    private static @NotNull Path locatePackFolder(@NotNull Path extracted) throws IOException {
        if (Files.isRegularFile(extracted.resolve("pack.json"))) {
            return extracted;
        }
        try (Stream<Path> children = Files.list(extracted)) {
            List<Path> folders = children.filter(Files::isDirectory).toList();
            if (folders.size() == 1 && Files.isRegularFile(folders.getFirst().resolve("pack.json"))) {
                return folders.getFirst();
            }
        }
        throw new IllegalArgumentException("Zip does not contain pack.json at root or inside one top-level folder.");
    }

    private static void extractZip(@NotNull Path zip, @NotNull Path outputRoot) throws IOException {
        Files.createDirectories(outputRoot);
        try (InputStream input = Files.newInputStream(zip);
             ZipInputStream zipInput = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                Path output = outputRoot.resolve(entry.getName()).normalize();
                if (!output.startsWith(outputRoot)) {
                    throw new IllegalArgumentException("Zip entry escapes pack folder: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }
                Files.createDirectories(output.getParent());
                Files.copy(zipInput, output);
            }
        }
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
        List<String> dependencies = namespaces(root.path("dependencies"), packFile);

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
        boolean packCreativeMenu = boolValue(root, "creative-menu", true);
        List<BlockPackBlock> blocks = loadBlocks(normalizedFolder, packCatalog, packCreativeMenu);
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
                dependencies,
                boolValue(root, "required", true),
                packCatalog,
                packCreativeMenu,
                icon,
                assetRoots,
                blocks
        );
    }

    private static @NotNull List<String> namespaces(@NotNull JsonNode node, @NotNull Path file) {
        if (!node.isArray()) {
            return List.of();
        }

        List<String> namespaces = new ArrayList<>();
        for (JsonNode value : node) {
            String namespace = value.asText();
            if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
                throw new IllegalArgumentException(file + ": Invalid dependency namespace '" + namespace + "'.");
            }
            namespaces.add(namespace);
        }
        return namespaces;
    }

    private static @NotNull List<BlockPackBlock> loadBlocks(
            @NotNull Path folder,
            boolean packCatalog,
            boolean packCreativeMenu
    ) throws IOException {
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
                    blocks.add(loadBlock(blocksRoot, file, packCatalog, packCreativeMenu));
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
            boolean packCatalog,
            boolean packCreativeMenu
    ) throws IOException {
        JsonNode root = Main.getJsonMapper().readTree(file.toFile());
        String fallbackName = blocksRoot.relativize(file).toString().replace('\\', '/');
        fallbackName = fallbackName.substring(0, fallbackName.length() - ".json".length());
        String name = textValue(root, "name", fallbackName);
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid block name '" + name + "'.");
        }
        if (root.has("vanilla-block")) {
            throw new IllegalArgumentException("'vanilla-block' is no longer supported; BlockEngine uses a fixed base block.");
        }

        BlockDefinition.Placement placement = enumValue(
                BlockDefinition.Placement.class,
                textValue(root, "placement", "none"),
                "placement",
                file
        );
        BlockPackBlock.Item item = item(root.path("item"), file);
        String defaultState = textValue(root, "default-state", "default");
        Map<String, BlockPackBlock.State> states = states(root.path("states"), file);
        if (!states.containsKey(defaultState)) {
            throw new IllegalArgumentException("Default state '" + defaultState + "' is not defined.");
        }

        return new BlockPackBlock(
                name,
                boolValue(root, "catalog", packCatalog),
                boolValue(root, "creative-menu", packCreativeMenu),
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
            states.put(stateId, state(entry.getValue(), file));
        });
        return states;
    }

    private static @NotNull BlockPackBlock.State state(
            @NotNull JsonNode node,
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
                material(node, "mining-profile", Material.STONE, true, file),
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
        validateMaterialFamily(material, raw, field, block, file);
        return material;
    }

    private static void validateMaterialFamily(
            @NotNull Material material,
            @NotNull String raw,
            @NotNull String field,
            boolean block,
            @NotNull Path file
    ) {
        try {
            if (block && !material.isBlock()) {
                throw new IllegalArgumentException(file + ": Material '" + raw + "' for " + field + " is not a block.");
            }
            if (!block && !material.isItem()) {
                throw new IllegalArgumentException(file + ": Material '" + raw + "' for " + field + " is not an item.");
            }
        } catch (IllegalStateException | LinkageError exception) {
            // Paper 26 resolves Material block/item families through live Bukkit registries.
            // Plain JVM tests do not bootstrap those registries, so keep name validation only there.
        }
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

    private static @NotNull String safeName(@NotNull Path source) {
        String name = source.getFileName().toString();
        int dot = name.toLowerCase(Locale.ROOT).lastIndexOf(".zip");
        if (dot >= 0) {
            name = name.substring(0, dot);
        }
        name = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return name.isBlank() ? "pack" : name;
    }

    private static void delete(@NotNull Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> files = Files.walk(path)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    public record Result(@NotNull List<BlockPack> packs, @NotNull List<String> errors) {
        public Result {
            packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        }
    }
}
