package dev.auto.turtle.registry;

import dev.auto.turtle.api.blocks.BlockAdapter;
import dev.auto.turtle.types.BlockDefinition;
import dev.auto.turtle.types.BlockName;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class BlockRegistry {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern BLOCK_ID_PATTERN = Pattern.compile("^[a-z0-9._-]+:[a-z0-9._-]+(?:/[a-z0-9._-]+)*$");

    private static final Map<BlockName, BlockDefinition> blocks = new HashMap<>();

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
        if (id == null || !BLOCK_ID_PATTERN.matcher(id).matches()) {
            return null;
        }

        String[] parts = id.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        return getBlock(parts[0], parts[1]);
    }

    public static BlockDefinition getBlock(BlockName name) {
        if (name == null) {
            return null;
        }
        return blocks.get(name);
    }

    public static BlockDefinition registerBlock(@NotNull BlockAdapter adapter, @NotNull String namespace) {
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid namespace: " + namespace);
        }

        dev.auto.turtle.api.blocks.BlockDefinition.Builder builder =
                dev.auto.turtle.api.blocks.BlockDefinition.builder(adapter.name());
        adapter.define(builder);
        dev.auto.turtle.api.blocks.BlockDefinition apiDefinition = builder.build();
        apiDefinition.namespace(namespace);
        apiDefinition.validate();

        BlockDefinition definition = new BlockDefinition(new BlockName(apiDefinition.name(), namespace), adapter);
        blocks.put(definition.name(), definition);
        return definition;
    }

}
