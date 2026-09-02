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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class CreativeInventoryManager {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9._-]+(?:/[a-z0-9._-]+)*$");
    private static final String MANIFEST_FILE = "generated-creative-enchantments.json";
    private static final String ENCHANTMENT_PREFIX = "creative/";
    private static int pendingRestartEntries = 0;

    private CreativeInventoryManager() {
    }

    public static @NotNull List<CreativeBlock> bootstrapBlocks(@NotNull Path dataDirectory) {
        Map<String, CreativeBlock> blocks = new LinkedHashMap<>();
        readManifest(dataDirectory.resolve(MANIFEST_FILE), blocks);
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

    public static int pendingRestartEntries() {
        return pendingRestartEntries;
    }

    public static void writeBootstrapManifest(@NotNull Collection<BlockDefinition> blocks) {
        Path file = Main.getInstance().getDataFolder().toPath().resolve(MANIFEST_FILE);
        Map<String, CreativeBlock> realizedBlocks = new LinkedHashMap<>();
        readManifest(file, realizedBlocks);

        ArrayNode root = MAPPER.createArrayNode();
        List<BlockDefinition> creativeBlocks = blocks.stream()
                .filter(block -> block.apiDefinition().creativeMenu())
                .sorted(Comparator.comparing(BlockDefinition::id))
                .toList();
        pendingRestartEntries = newEntries(creativeBlocks, realizedBlocks.keySet());

        creativeBlocks.forEach(block -> {
            ObjectNode node = root.addObject();
            node.put("id", block.id());
            node.put("enchantment", enchantmentKey(block.id()).asString());
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

    private static int newEntries(
            @NotNull Collection<BlockDefinition> currentBlocks,
            @NotNull Collection<String> realizedBlockIds
    ) {
        int entries = 0;
        for (BlockDefinition block : currentBlocks) {
            if (!realizedBlockIds.contains(block.id())) {
                entries++;
            }
        }
        return entries;
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
