package dev.auto.turtle.registry;

import com.fasterxml.jackson.databind.JsonNode;
import dev.auto.turtle.api.blocks.BlockAdapter;
import dev.auto.turtle.api.util.DataComb;
import dev.auto.turtle.types.BlockDefinition;
import dev.auto.turtle.types.BlockName;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

    public static BlockDefinition registerBlock(BlockAdapter adapter, String namespace, JsonNode dataDefinition) {
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid namespace: " + namespace);
        }

        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(dataDefinition, "dataDefinition");

        JsonNode jsonNameNode = dataDefinition.get("name");
        String jsonName = null;
        if (jsonNameNode != null && !jsonNameNode.isNull()) {
            if (!jsonNameNode.isTextual()) {
                throw new IllegalArgumentException("Field 'name' must be a string.");
            }
            jsonName = jsonNameNode.asText().trim();
            if (jsonName.isEmpty()) {
                jsonName = null;
            }
        }

        String adapterName = adapter.name();
        if (adapterName != null) {
            adapterName = adapterName.trim();
            if (adapterName.isEmpty()) {
                adapterName = null;
            }
        }

        DataComb jsonState = new DataComb()
                .setProperty("namespace", namespace)
                .setProperty("name", jsonName);

        DataComb adapterState = new DataComb()
                .setProperty("namespace", namespace)
                .setProperty("name", adapterName);

        // Finish tomorrow
    }
}
