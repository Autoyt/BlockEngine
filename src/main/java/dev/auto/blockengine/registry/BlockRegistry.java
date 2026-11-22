package dev.auto.blockengine.registry;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockName;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class BlockRegistry {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");

    private static final Map<BlockName, BlockDefinition> blocks = new HashMap<>();
    private static final Map<String, BlockDefinition> blocksById = new HashMap<>();
    private static final Map<BlockAdapter, BlockDefinition> blocksByAdapter = new IdentityHashMap<>();
    private static final Map<BlockName, Plugin> owners = new HashMap<>();

    public static void clear() {
        ItemManager.clearRegistrations();
        blocks.clear();
        blocksById.clear();
        blocksByAdapter.clear();
        owners.clear();
    }

    public static void removeAll(@NotNull Plugin owner) {
        for (BlockDefinition block : java.util.List.copyOf(blocks.values())) {
            if (owners.get(block.name()) != owner) {
                continue;
            }

            ItemManager.unregister(block);
            blocks.remove(block.name());
            blocksById.remove(block.id());
            blocksByAdapter.remove(block.adapter());
            owners.remove(block.name());
        }
    }

    public static boolean hasBlocks(@NotNull Plugin owner) {
        return owners.values().stream().anyMatch(registeredOwner -> registeredOwner == owner);
    }

    public static BlockDefinition getBlock(String namespace, String name) {
        if (namespace == null || name == null) {
            return null;
        }
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            return null;
        }
        return getBlock(new BlockName(name, namespace));
    }

    public static BlockDefinition getBlock(String id) {
        return id == null ? null : blocksById.get(id);
    }

    public static BlockDefinition getBlock(BlockName name) {
        if (name == null) {
            return null;
        }
        return blocks.get(name);
    }

    public static BlockDefinition getBlock(@NotNull BlockAdapter adapter) {
        return blocksByAdapter.get(adapter);
    }

    public static Collection<BlockDefinition> getBlocks() {
        return Collections.unmodifiableCollection(blocks.values());
    }

    public static BlockDefinition registerBlock(@NotNull BlockAdapter adapter, @NotNull String namespace) {
        return registerBlock(adapter, namespace, Main.getInstance());
    }

    public static BlockDefinition registerBlock(
            @NotNull BlockAdapter adapter,
            @NotNull String namespace,
            @NotNull Plugin owner
    ) {
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid namespace: " + namespace);
        }

        dev.auto.blockengine.api.blocks.BlockDefinition.Builder builder =
                dev.auto.blockengine.api.blocks.BlockDefinition.builder(adapter.name());
        adapter.define(builder);
        dev.auto.blockengine.api.blocks.BlockDefinition apiDefinition = builder.build();
        apiDefinition.namespace(namespace);
        apiDefinition.validate();

        BlockDefinition definition = new BlockDefinition(new BlockName(apiDefinition.name(), namespace), adapter, apiDefinition);
        if (blocks.containsKey(definition.name())) {
            throw new IllegalArgumentException("Duplicate BlockEngine block id: " + definition.id());
        }
        blocks.put(definition.name(), definition);
        blocksById.put(definition.id(), definition);
        blocksByAdapter.put(adapter, definition);
        owners.put(definition.name(), owner);

        try {
            ItemManager.register(definition);
            adapter.onRegistered(ItemManager.create(definition));
        } catch (RuntimeException exception) {
            Main.getInstance().getLogger().log(Level.WARNING,
                    "Block registration callback failed for " + definition.id(), exception);
        }

        return definition;
    }

}
