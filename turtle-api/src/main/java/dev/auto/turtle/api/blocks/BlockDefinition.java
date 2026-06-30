package dev.auto.turtle.api.blocks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Shared, runtime-safe block definition model.
 *
 * <p>This type is intentionally plain Java so the same shape can be used by the
 * public API, JSON loading, and runtime registration. It is mutable so usage
 * plugins can build blocks completely in code, while the runtime can still
 * hydrate the same model from JSON.</p>
 */
public final class BlockDefinition {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9._-]+(?:/[a-z0-9._-]+)*$");

    private @Nullable String namespace;
    private @NotNull String name;
    private final @NotNull Map<String, Object> config;
    private @NotNull ItemDefinition item;
    private @NotNull PlacementDefinition placement;
    private @NotNull String defaultStateId;
    private final @NotNull Map<String, StateDefinition> states;

    public BlockDefinition(@NotNull String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.config = new LinkedHashMap<>();
        this.item = new ItemDefinition();
        this.placement = new PlacementDefinition();
        this.defaultStateId = "default";
        this.states = new LinkedHashMap<>();
        this.states.put(this.defaultStateId, new StateDefinition());
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

    public @NotNull BlockDefinition name(@NotNull String name) {
        this.name = Objects.requireNonNull(name, "name");
        return this;
    }

    public @NotNull String id() {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalStateException("BlockDefinition namespace has not been attached yet.");
        }
        return namespace + ":" + name;
    }

    public @NotNull Map<String, Object> config() {
        return config;
    }

    public @NotNull BlockDefinition config(@NotNull Map<String, Object> config) {
        this.config.clear();
        this.config.putAll(Objects.requireNonNull(config, "config"));
        return this;
    }

    public @NotNull BlockDefinition putConfig(@NotNull String key, @Nullable Object value) {
        if (value == null) {
            this.config.remove(Objects.requireNonNull(key, "key"));
            return this;
        }
        this.config.put(Objects.requireNonNull(key, "key"), value);
        return this;
    }

    public @NotNull ItemDefinition item() {
        return item;
    }

    public @NotNull BlockDefinition item(@NotNull ItemDefinition item) {
        this.item = Objects.requireNonNull(item, "item");
        return this;
    }

    public @NotNull PlacementDefinition placement() {
        return placement;
    }

    public @NotNull BlockDefinition placement(@NotNull PlacementDefinition placement) {
        this.placement = Objects.requireNonNull(placement, "placement");
        return this;
    }

    public @NotNull String defaultStateId() {
        return defaultStateId;
    }

    public @NotNull BlockDefinition defaultStateId(@NotNull String defaultStateId) {
        this.defaultStateId = Objects.requireNonNull(defaultStateId, "defaultStateId");
        return this;
    }

    public @NotNull Map<String, StateDefinition> states() {
        return states;
    }

    public @NotNull StateDefinition state(@NotNull String stateId) {
        StateDefinition state = states.get(stateId);
        if (state == null) {
            throw new IllegalArgumentException("Unknown block state: " + stateId);
        }
        return state;
    }

    public @NotNull BlockDefinition putState(@NotNull String stateId, @NotNull StateDefinition state) {
        states.put(Objects.requireNonNull(stateId, "stateId"), Objects.requireNonNull(state, "state"));
        return this;
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
        if (!states.containsKey(defaultStateId)) {
            throw new IllegalStateException("Default state '" + defaultStateId + "' is not defined.");
        }
        for (Map.Entry<String, StateDefinition> entry : states.entrySet()) {
            entry.getValue().validate(entry.getKey());
        }
    }

    public static final class ItemDefinition {
        private @Nullable String material;
        private @Nullable String name;
        private final @NotNull List<String> lore;
        private boolean glint;

        public ItemDefinition() {
            this.lore = new ArrayList<>();
        }

        public @Nullable String material() {
            return material;
        }

        public @NotNull ItemDefinition material(@Nullable String material) {
            this.material = material;
            return this;
        }

        public @Nullable String name() {
            return name;
        }

        public @NotNull ItemDefinition name(@Nullable String name) {
            this.name = name;
            return this;
        }

        public @NotNull List<String> lore() {
            return lore;
        }

        public @NotNull ItemDefinition lore(@NotNull List<String> lore) {
            this.lore.clear();
            this.lore.addAll(Objects.requireNonNull(lore, "lore"));
            return this;
        }

        public boolean glint() {
            return glint;
        }

        public @NotNull ItemDefinition glint(boolean glint) {
            this.glint = glint;
            return this;
        }
    }

    public static final class PlacementDefinition {
        public enum Type {
            NONE,
            HORIZONTAL_FACING,
            AXIS
        }

        private @NotNull Type type;

        public PlacementDefinition() {
            this.type = Type.NONE;
        }

        public @NotNull Type type() {
            return type;
        }

        public @NotNull PlacementDefinition type(@NotNull Type type) {
            this.type = Objects.requireNonNull(type, "type");
            return this;
        }
    }

    public static final class BlockPropertiesDefinition {
        private float hardness;
        private boolean washable;
        private boolean breakable;

        public BlockPropertiesDefinition() {
            this.hardness = 0.5f;
            this.washable = false;
            this.breakable = true;
        }

        public float hardness() {
            return hardness;
        }

        public @NotNull BlockPropertiesDefinition hardness(float hardness) {
            this.hardness = hardness;
            return this;
        }

        public boolean washable() {
            return washable;
        }

        public @NotNull BlockPropertiesDefinition washable(boolean washable) {
            this.washable = washable;
            return this;
        }

        public boolean breakable() {
            return breakable;
        }

        public @NotNull BlockPropertiesDefinition breakable(boolean breakable) {
            this.breakable = breakable;
            return this;
        }
    }

    public static final class FaceTexturesDefinition {
        private @Nullable String all;
        private @Nullable String side;
        private @Nullable String front;
        private @Nullable String top;
        private @Nullable String bottom;
        private @Nullable String north;
        private @Nullable String south;
        private @Nullable String east;
        private @Nullable String west;

        public @Nullable String all() {
            return all;
        }

        public @NotNull FaceTexturesDefinition all(@Nullable String all) {
            this.all = all;
            return this;
        }

        public @Nullable String side() {
            return side;
        }

        public @NotNull FaceTexturesDefinition side(@Nullable String side) {
            this.side = side;
            return this;
        }

        public @Nullable String front() {
            return front;
        }

        public @NotNull FaceTexturesDefinition front(@Nullable String front) {
            this.front = front;
            return this;
        }

        public @Nullable String top() {
            return top;
        }

        public @NotNull FaceTexturesDefinition top(@Nullable String top) {
            this.top = top;
            return this;
        }

        public @Nullable String bottom() {
            return bottom;
        }

        public @NotNull FaceTexturesDefinition bottom(@Nullable String bottom) {
            this.bottom = bottom;
            return this;
        }

        public @Nullable String north() {
            return north;
        }

        public @NotNull FaceTexturesDefinition north(@Nullable String north) {
            this.north = north;
            return this;
        }

        public @Nullable String south() {
            return south;
        }

        public @NotNull FaceTexturesDefinition south(@Nullable String south) {
            this.south = south;
            return this;
        }

        public @Nullable String east() {
            return east;
        }

        public @NotNull FaceTexturesDefinition east(@Nullable String east) {
            this.east = east;
            return this;
        }

        public @Nullable String west() {
            return west;
        }

        public @NotNull FaceTexturesDefinition west(@Nullable String west) {
            this.west = west;
            return this;
        }

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

    public static final class StateDefinition {
        private @NotNull BlockPropertiesDefinition properties;
        private @NotNull FaceTexturesDefinition textures;

        public StateDefinition() {
            this.properties = new BlockPropertiesDefinition();
            this.textures = new FaceTexturesDefinition();
        }

        public @NotNull BlockPropertiesDefinition properties() {
            return properties;
        }

        public @NotNull StateDefinition properties(@NotNull BlockPropertiesDefinition properties) {
            this.properties = Objects.requireNonNull(properties, "properties");
            return this;
        }

        public @NotNull FaceTexturesDefinition textures() {
            return textures;
        }

        public @NotNull StateDefinition textures(@NotNull FaceTexturesDefinition textures) {
            this.textures = Objects.requireNonNull(textures, "textures");
            return this;
        }

        private void validate(@NotNull String stateId) {
            if (textures.isEmpty()) {
                throw new IllegalStateException("Block state '" + stateId + "' must define at least one texture.");
            }
        }
    }
}
