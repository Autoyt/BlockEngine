package dev.auto.turtle.api.blocks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Optional hook surface for advanced Turtle blocks.
 *
 * <p>Most blocks can be defined entirely in JSON. Adapters are only needed when
 * a block wants custom runtime behavior or wants to consume extra config
 * values from the block definition.</p>
 */
public abstract class BlockAdapter {
    public abstract String name();
    public abstract @NotNull String jsonDefinitionPath();
}
