package dev.auto.blockengine.creative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CreativeInventoryManager {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9._-]+(?:/[a-z0-9._-]+)*$");
    private static final String MANIFEST_FILE = "generated-creative-blocks.json";
    private static final String ENCHANTMENT_PREFIX = "creative/";

    private CreativeInventoryManager() {
    }

    public static @NotNull List<CreativeBlock> bootstrapBlocks(@NotNull Path dataDirectory) {
        Map<String, CreativeBlock> blocks = new LinkedHashMap<>();
        readManifest(dataDirectory.resolve(MANIFEST_FILE), blocks);
        readExpansionPacks(
                dataDirectory.resolve("expansion").resolve("packs"),
                dataDirectory.resolve("generated-expansion-packs").resolve("creative-bootstrap"),
                blocks
        );
        return blocks.values().stream()
                .sorted(Comparator.comparing(CreativeBlock::id))
                .toList();
    }

    public static void configureEnchantment(
            @NotNull EnchantmentRegistryEntry.Builder builder,
            @NotNull CreativeBlock block
    ) {
        builder.description(Component.translatable(enchantmentTranslationKey(block.id())))
                .supportedItems(RegistrySet.keySet(
                        RegistryKey.ITEM,
                        List.of(ItemTypeKeys.KNOWLEDGE_BOOK, ItemTypeKeys.ENCHANTED_BOOK)
                ))
                .primaryItems(RegistrySet.keySet(RegistryKey.ITEM, List.<TypedKey<ItemType>>of()))
                .weight(1)
                .maxLevel(1)
                .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(1, 0))
                .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(1, 0))
                .anvilCost(0)
                .activeSlots(List.<EquipmentSlotGroup>of())
                .exclusiveWith(RegistrySet.keySet(RegistryKey.ENCHANTMENT, List.<TypedKey<Enchantment>>of()));
    }

    public static @NotNull TypedKey<Enchantment> enchantmentTypedKey(@NotNull String blockId) {
        return TypedKey.create(RegistryKey.ENCHANTMENT, Key.key(enchantmentKey(blockId).asString()));
    }

    public static @NotNull NamespacedKey enchantmentKey(@NotNull String blockId) {
        String[] parts = splitBlockId(blockId);
        return new NamespacedKey(parts[0], ENCHANTMENT_PREFIX + parts[1]);
    }

    public static @NotNull String blockTranslationKey(@NotNull String blockId) {
        String[] parts = splitBlockId(blockId);
        return "block." + parts[0] + "." + parts[1].replace('/', '.');
    }

    public static @NotNull String enchantmentTranslationKey(@NotNull String blockId) {
        String[] parts = splitBlockId(blockId);
        return "enchantment." + parts[0] + ".creative." + parts[1].replace('/', '.');
    }

    public static @Nullable ItemStack convertCreativeStack(@Nullable ItemStack stack) {
        String blockId = creativeBlockId(stack);
        if (blockId == null) {
            return stack;
        }
        BlockDefinition block = BlockRegistry.getBlock(blockId);
        if (block == null) {
            return stack;
        }

        ItemStack converted = ItemManager.create(block);
        converted.setAmount(Math.max(1, stack.getAmount()));
        return converted;
    }

    public static @Nullable String creativeBlockId(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        String existing = ItemManager.blockId(stack);
        if (existing != null) {
            return null;
        }

        ItemMeta meta = stack.getItemMeta();
        String direct = blockId(meta.getEnchants().keySet());
        if (direct != null) {
            return direct;
        }
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            return blockId(storageMeta.getStoredEnchants().keySet());
        }
        return null;
    }

    public static void writeBootstrapManifest(@NotNull Collection<BlockDefinition> blocks) {
        Path file = Main.getInstance().getDataFolder().toPath().resolve(MANIFEST_FILE);
        ArrayNode root = MAPPER.createArrayNode();
        blocks.stream()
                .filter(block -> block.apiDefinition().creativeMenu())
                .sorted(Comparator.comparing(BlockDefinition::id))
                .forEach(block -> {
                    ObjectNode node = root.addObject();
                    node.put("id", block.id());
                    node.put("name", block.apiDefinition().name());
                    node.put("display-name", BlockDisplayNames.plain(block.apiDefinition().item().name(), block));
                });
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writeValue(file.toFile(), root);
        } catch (IOException exception) {
            Main.getInstance().getLogger().warning("Failed to write BlockEngine creative manifest: "
                    + exception.getMessage());
        }
    }

    private static @Nullable String blockId(@NotNull Collection<Enchantment> enchantments) {
        for (Enchantment enchantment : enchantments) {
            NamespacedKey key = enchantment.getKey();
            if (!key.getKey().startsWith(ENCHANTMENT_PREFIX)) {
                continue;
            }
            return key.getNamespace() + ":" + key.getKey().substring(ENCHANTMENT_PREFIX.length());
        }
        return null;
    }

    private static void readManifest(@NotNull Path file, @NotNull Map<String, CreativeBlock> blocks) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(file.toFile());
            if (!root.isArray()) {
                return;
            }
            for (JsonNode node : root) {
                String id = text(node, "id", "");
                String[] parts = validBlockId(id);
                if (parts == null) {
                    continue;
                }
                String name = text(node, "name", parts[1]);
                String displayName = text(node, "display-name", BlockDisplayNames.fallback(name, id));
                blocks.put(id, new CreativeBlock(id, parts[0], parts[1], displayName));
            }
        } catch (IOException exception) {
            // Bootstrap logging is handled by the caller; a stale manifest should not block startup.
        }
    }

    private static void readExpansionPacks(
            @NotNull Path packsRoot,
            @NotNull Path extractedRoot,
            @NotNull Map<String, CreativeBlock> blocks
    ) {
        if (!Files.isDirectory(packsRoot)) {
            return;
        }
        try {
            delete(extractedRoot);
            Files.createDirectories(extractedRoot);
        } catch (IOException exception) {
            return;
        }

        try (Stream<Path> children = Files.list(packsRoot)) {
            for (Path source : children.sorted().toList()) {
                readExpansionPackSource(source, extractedRoot, blocks);
            }
        } catch (IOException exception) {
            // Creative bootstrap discovery is opportunistic; runtime pack loading reports validation errors.
        }
    }

    private static void readExpansionPackSource(
            @NotNull Path source,
            @NotNull Path extractedRoot,
            @NotNull Map<String, CreativeBlock> blocks
    ) {
        try {
            if (Files.isDirectory(source)) {
                readExpansionPackFolder(source, blocks);
                return;
            }
            if (Files.isRegularFile(source)
                    && source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                Path extracted = extractedRoot.resolve(safeName(source)).normalize();
                extractZip(source, extracted);
                readExpansionPackFolder(locatePackFolder(extracted), blocks);
            }
        } catch (RuntimeException | IOException exception) {
            // Full data-pack loading will surface invalid packs later.
        }
    }

    private static void readExpansionPackFolder(
            @NotNull Path folder,
            @NotNull Map<String, CreativeBlock> blocks
    ) throws IOException {
        Path packFile = folder.resolve("pack.json");
        if (!Files.isRegularFile(packFile)) {
            return;
        }
        JsonNode pack = MAPPER.readTree(packFile.toFile());
        String namespace = text(pack, "namespace", "");
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            return;
        }
        boolean packCreative = bool(pack, "creative-menu", true);

        Path blocksRoot = folder.resolve("blocks").normalize();
        if (!Files.isDirectory(blocksRoot)) {
            return;
        }
        try (Stream<Path> files = Files.walk(blocksRoot)) {
            for (Path file : files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted()
                    .toList()) {
                readExpansionBlock(blocksRoot, file, namespace, packCreative, blocks);
            }
        }
    }

    private static void readExpansionBlock(
            @NotNull Path blocksRoot,
            @NotNull Path file,
            @NotNull String namespace,
            boolean packCreative,
            @NotNull Map<String, CreativeBlock> blocks
    ) {
        try {
            JsonNode root = MAPPER.readTree(file.toFile());
            String fallbackName = blocksRoot.relativize(file).toString().replace('\\', '/');
            fallbackName = fallbackName.substring(0, fallbackName.length() - ".json".length());
            String name = text(root, "name", fallbackName);
            if (!NAME_PATTERN.matcher(name).matches()) {
                return;
            }
            String id = namespace + ":" + name;
            if (!bool(root, "creative-menu", packCreative)) {
                blocks.remove(id);
                return;
            }
            String itemName = nullableText(root.path("item"), "name");
            String displayName = itemName == null
                    ? BlockDisplayNames.fallback(name, id)
                    : plainMiniMessage(itemName);
            blocks.put(id, new CreativeBlock(id, namespace, name, displayName));
        } catch (IOException exception) {
            // Runtime pack loading reports validation errors.
        }
    }

    private static @NotNull String plainMiniMessage(@NotNull String text) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(text));
    }

    private static @Nullable String[] validBlockId(@NotNull String id) {
        int split = id.indexOf(':');
        if (split <= 0 || split == id.length() - 1) {
            return null;
        }
        String namespace = id.substring(0, split);
        String name = id.substring(split + 1);
        if (!NAMESPACE_PATTERN.matcher(namespace).matches() || !NAME_PATTERN.matcher(name).matches()) {
            return null;
        }
        return new String[] { namespace, name };
    }

    private static @NotNull String[] splitBlockId(@NotNull String blockId) {
        String[] parts = validBlockId(blockId);
        if (parts == null) {
            throw new IllegalArgumentException("Invalid BlockEngine block id: " + blockId);
        }
        return parts;
    }

    private static @NotNull String text(@NotNull JsonNode node, @NotNull String field, @NotNull String fallback) {
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

    private static boolean bool(@NotNull JsonNode node, @NotNull String field, boolean fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asBoolean(fallback);
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

    public record CreativeBlock(
            @NotNull String id,
            @NotNull String namespace,
            @NotNull String name,
            @NotNull String displayName
    ) {
        public CreativeBlock {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(displayName, "displayName");
        }
    }
}
