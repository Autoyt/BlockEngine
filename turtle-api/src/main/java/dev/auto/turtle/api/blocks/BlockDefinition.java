package dev.auto.turtle.api.blocks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Server-side definition of a Turtle custom block.
 */
public final class BlockDefinition {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9._-]+(?:/[a-z0-9._-]+)*$");

    private @Nullable String namespace;
    private final @NotNull String name;
    private final @NotNull Placement placement;
    private final @NotNull String defaultState;
    private final @NotNull Map<String, State> states;

    private BlockDefinition(
            @NotNull String name,
            @NotNull Placement placement,
            @NotNull String defaultState,
            @NotNull Map<String, State> states
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.placement = Objects.requireNonNull(placement, "placement");
        this.defaultState = Objects.requireNonNull(defaultState, "defaultState");
        this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    public static @NotNull Builder builder(@NotNull String name) {
        return new Builder(name);
    }

    public @Nullable String namespace() {
        return namespace;
    }

    public @NotNull BlockDefinition namespace(@Nullable String namespace) {
        this.namespace = namespace;
        return this;
    }

    public @NotNull String name() {
        return name;
    }

    public @NotNull String id() {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalStateException("BlockDefinition namespace has not been attached yet.");
        }
        return namespace + ":" + name;
    }

    public @NotNull Placement placement() {
        return placement;
    }

    public @NotNull String defaultState() {
        return defaultState;
    }

    public @NotNull Map<String, State> states() {
        return states;
    }

    public @NotNull State state(@NotNull String stateId) {
        State state = states.get(stateId);
        if (state == null) {
            throw new IllegalArgumentException("Unknown block state: " + stateId);
        }
        return state;
    }

    public void validate() {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalStateException("BlockDefinition namespace cannot be blank.");
        }
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalStateException("BlockDefinition namespace contains invalid characters: " + namespace);
        }
        if (name.isBlank()) {
            throw new IllegalStateException("BlockDefinition name cannot be blank.");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalStateException("BlockDefinition name must use lowercase path segments like 'folder/block_name': " + name);
        }
        if (!states.containsKey(defaultState)) {
            throw new IllegalStateException("Default state '" + defaultState + "' is not defined.");
        }
        for (Map.Entry<String, State> entry : states.entrySet()) {
            entry.getValue().validate(entry.getKey());
        }
    }

    public enum Placement {
        NONE,
        HORIZONTAL_FACING,
        AXIS,
        DIRECTIONAL
    }

    public record State(
            float hardness,
            float miningSpeed,
            boolean washable,
            @NotNull Textures textures,
            @NotNull Sounds sounds
    ) {
        public State {
            Objects.requireNonNull(textures, "textures");
            Objects.requireNonNull(sounds, "sounds");
        }

        private void validate(@NotNull String stateId) {
            if (textures.isEmpty()) {
                throw new IllegalStateException("Block state '" + stateId + "' must define at least one texture.");
            }
        }
    }

    public record Textures(
            @Nullable String all,
            @Nullable String side,
            @Nullable String front,
            @Nullable String top,
            @Nullable String bottom,
            @Nullable String north,
            @Nullable String south,
            @Nullable String east,
            @Nullable String west
    ) {
        public boolean isEmpty() {
            return all == null
                    && side == null
                    && front == null
                    && top == null
                    && bottom == null
                    && north == null
                    && south == null
                    && east == null
                    && west == null;
        }
    }

    public record Sounds(
            @Nullable String place,
            @Nullable String breakSound,
            @Nullable String step,
            @Nullable String hit,
            @Nullable String fall
    ) {
        public static @NotNull Sounds empty() {
            return new Sounds(null, null, null, null, null);
        }
    }

    public static final class Builder {
        private final @NotNull String name;
        private @NotNull Placement placement = Placement.NONE;
        private @NotNull String defaultState = "default";
        private final @NotNull Map<String, State> states = new LinkedHashMap<>();

        private Builder(@NotNull String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public @NotNull Builder placement(@NotNull Placement placement) {
            this.placement = Objects.requireNonNull(placement, "placement");
            return this;
        }

        public @NotNull Builder defaultState(@NotNull String defaultState) {
            this.defaultState = Objects.requireNonNull(defaultState, "defaultState");
            return this;
        }

        public @NotNull Builder state(@NotNull String id, @NotNull Consumer<StateBuilder> configure) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(configure, "configure");

            StateBuilder builder = new StateBuilder();
            configure.accept(builder);
            states.put(id, builder.build());
            return this;
        }

        public @NotNull BlockDefinition build() {
            if (states.isEmpty()) {
                state(defaultState, state -> state.textures(textures -> textures.all("missing")));
            }
            return new BlockDefinition(name, placement, defaultState, states);
        }
    }

    public static final class StateBuilder {
        private float hardness = 0.5f;
        private float miningSpeed = 1.0f;
        private boolean washable = false;
        private @NotNull Textures textures = new Textures(null, null, null, null, null, null, null, null, null);
        private @NotNull Sounds sounds = Sounds.empty();

        public @NotNull StateBuilder hardness(float hardness) {
            this.hardness = hardness;
            return this;
        }

        public @NotNull StateBuilder miningSpeed(float miningSpeed) {
            this.miningSpeed = miningSpeed;
            return this;
        }

        public @NotNull StateBuilder washable(boolean washable) {
            this.washable = washable;
            return this;
        }

        public @NotNull StateBuilder textures(@NotNull Consumer<TexturesBuilder> configure) {
            Objects.requireNonNull(configure, "configure");
            TexturesBuilder builder = new TexturesBuilder();
            configure.accept(builder);
            this.textures = builder.build();
            return this;
        }

        public @NotNull StateBuilder sounds(@NotNull Consumer<SoundsBuilder> configure) {
            Objects.requireNonNull(configure, "configure");
            SoundsBuilder builder = new SoundsBuilder();
            configure.accept(builder);
            this.sounds = builder.build();
            return this;
        }

        private @NotNull State build() {
            return new State(hardness, miningSpeed, washable, textures, sounds);
        }
    }

    public static final class TexturesBuilder {
        private @Nullable String all;
        private @Nullable String side;
        private @Nullable String front;
        private @Nullable String top;
        private @Nullable String bottom;
        private @Nullable String north;
        private @Nullable String south;
        private @Nullable String east;
        private @Nullable String west;

        public @NotNull TexturesBuilder all(@Nullable String all) {
            this.all = all;
            return this;
        }

        public @NotNull TexturesBuilder side(@Nullable String side) {
            this.side = side;
            return this;
        }

        public @NotNull TexturesBuilder front(@Nullable String front) {
            this.front = front;
            return this;
        }

        public @NotNull TexturesBuilder top(@Nullable String top) {
            this.top = top;
            return this;
        }

        public @NotNull TexturesBuilder bottom(@Nullable String bottom) {
            this.bottom = bottom;
            return this;
        }

        public @NotNull TexturesBuilder north(@Nullable String north) {
            this.north = north;
            return this;
        }

        public @NotNull TexturesBuilder south(@Nullable String south) {
            this.south = south;
            return this;
        }

        public @NotNull TexturesBuilder east(@Nullable String east) {
            this.east = east;
            return this;
        }

        public @NotNull TexturesBuilder west(@Nullable String west) {
            this.west = west;
            return this;
        }

        private @NotNull Textures build() {
            return new Textures(all, side, front, top, bottom, north, south, east, west);
        }
    }

    public static final class SoundsBuilder {
        private @Nullable String place;
        private @Nullable String breakSound;
        private @Nullable String step;
        private @Nullable String hit;
        private @Nullable String fall;

        public @NotNull SoundsBuilder place(@Nullable String place) {
            this.place = place;
            return this;
        }

        public @NotNull SoundsBuilder breakSound(@Nullable String breakSound) {
            this.breakSound = breakSound;
            return this;
        }

        public @NotNull SoundsBuilder step(@Nullable String step) {
            this.step = step;
            return this;
        }

        public @NotNull SoundsBuilder hit(@Nullable String hit) {
            this.hit = hit;
            return this;
        }

        public @NotNull SoundsBuilder fall(@Nullable String fall) {
            this.fall = fall;
            return this;
        }

        private @NotNull Sounds build() {
            return new Sounds(place, breakSound, step, hit, fall);
        }
    }
}
