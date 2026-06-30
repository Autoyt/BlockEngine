package dev.auto.turtle.types;

import dev.auto.turtle.api.blocks.BlockAdapter;

public record BlockDefinition(BlockName name, BlockAdapter adapter) {
}
