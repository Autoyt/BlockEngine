package dev.auto.blockengine.registry;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockName;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

public class BlockRegistry {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");

    private static final Map<BlockName, BlockDefinition> blocks = new HashMap<>();
    private static final Map<String, BlockDefinition> blocksById = new HashMap<>();
    private static final Map<BlockAdapter, BlockDefinition> blocksByAdapter = new IdentityHashMap<>();

    public static void clear() {
        blocks.clear();
        blocksById.clear();
        blocksByAdapter.clear();
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
        return definition;
    }

}
