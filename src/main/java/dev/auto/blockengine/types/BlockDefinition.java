package dev.auto.blockengine.types;

import dev.auto.blockengine.api.blocks.BlockAdapter;

public record BlockDefinition(
        BlockName name,
        BlockAdapter adapter,
        dev.auto.blockengine.api.blocks.BlockDefinition apiDefinition
) {
    public String id() {
        return name.getIdentifier();
    }
}
