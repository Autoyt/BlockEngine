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
    private @NotNull String defaultStateId;
    private @NotNull ItemDefinition item;
    private @NotNull PlacementDefinition placement;
    private final @NotNull Map<String, StateDefinition> states;

    public BlockDefinition(@NotNull String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.defaultStateId = "default";
        this.item = new ItemDefinition();
        this.placement = new PlacementDefinition();
        this.states = new LinkedHashMap<>();
        this.states.put("default", new StateDefinition());
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

    public @NotNull String defaultStateId() {
        return defaultStateId;
    }

    public @NotNull BlockDefinition defaultStateId(@NotNull String defaultStateId) {
        this.defaultStateId = Objects.requireNonNull(defaultStateId, "defaultStateId");
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
        private int lightLevel;
        private boolean washable;
        private boolean breakable;

        public BlockPropertiesDefinition() {
            this.hardness = 0.5f;
            this.lightLevel = 0;
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

        public int lightLevel() {
            return lightLevel;
        }

        public @NotNull BlockPropertiesDefinition lightLevel(int lightLevel) {
            this.lightLevel = lightLevel;
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

    public static final class StateDefinition {
        private @NotNull BlockPropertiesDefinition properties;
        private @Nullable String textureState;
        private @Nullable String soundState;
        private @Nullable String animationState;

        public StateDefinition() {
            this.properties = new BlockPropertiesDefinition();
        }

        public @NotNull BlockPropertiesDefinition properties() {
            return properties;
        }

        public @NotNull StateDefinition properties(@NotNull BlockPropertiesDefinition properties) {
            this.properties = Objects.requireNonNull(properties, "properties");
            return this;
        }

        public @Nullable String textureState() {
            return textureState;
        }

        public @NotNull StateDefinition textureState(@Nullable String textureState) {
            this.textureState = textureState;
            return this;
        }

        public @Nullable String soundState() {
            return soundState;
        }

        public @NotNull StateDefinition soundState(@Nullable String soundState) {
            this.soundState = soundState;
            return this;
        }

        public @Nullable String animationState() {
            return animationState;
        }

        public @NotNull StateDefinition animationState(@Nullable String animationState) {
            this.animationState = animationState;
            return this;
        }
    }
}
