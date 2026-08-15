package dev.auto.turtle.types;

import dev.auto.turtle.api.blocks.BlockData;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class TurtleBlockInstance {
    private final @NotNull BlockLocationKey location;
    private final @NotNull BlockDefinition definition;
    private final @NotNull BlockData data;

    public TurtleBlockInstance(
            @NotNull BlockLocationKey location,
            @NotNull BlockDefinition definition,
            @NotNull BlockData data
    ) {
        this.location = Objects.requireNonNull(location, "location");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.data = Objects.requireNonNull(data, "data");
    }

    public @NotNull BlockLocationKey location() {
        return location;
    }

    public @NotNull BlockDefinition definition() {
        return definition;
    }

    public @NotNull BlockData data() {
        return data;
    }

    public byte @NotNull [] savePayload() {
        return definition.adapter().save(data);
    }
}
