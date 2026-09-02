package dev.auto.blockengine.api.blocks;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Server-side definition of a BlockEngine custom block.
 *
 * <p>A definition is the static contract for one block type. It describes the
 * backing vanilla block, creative-menu visibility, placed item, placement
 * behavior, block states, mining behavior, textures, sounds, and movement
 * rules. Runtime behavior belongs in the owning {@link BlockAdapter}.</p>
 */
public final class BlockDefinition {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9._-]+(?:/[a-z0-9._-]+)*$");

    private @Nullable String namespace;
    private final @NotNull String name;
    private final @NotNull Material defaultBlock;
    private final boolean catalog;
    private final boolean creativeMenu;
    private final @NotNull Item item;
    private final @NotNull Placement placement;
    private final @NotNull String defaultState;
    private final @NotNull Map<String, State> states;

    private BlockDefinition(
            @NotNull String name,
            @NotNull Material defaultBlock,
            boolean catalog,
            boolean creativeMenu,
            @NotNull Item item,
            @NotNull Placement placement,
            @NotNull String defaultState,
            @NotNull Map<String, State> states
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.defaultBlock = Objects.requireNonNull(defaultBlock, "defaultBlock");
        this.catalog = catalog;
        this.creativeMenu = creativeMenu;
        this.item = Objects.requireNonNull(item, "item");
        this.placement = Objects.requireNonNull(placement, "placement");
        this.defaultState = Objects.requireNonNull(defaultState, "defaultState");
        this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    /**
     * Starts building a block definition with the given local name.
     *
     * @param name local block name inside the owning namespace
     * @return block definition builder
     */
    public static @NotNull Builder builder(@NotNull String name) {
        return new Builder(name);
    }

    /**
     * Returns the namespace attached by BlockEngine during registration.
     *
     * @return namespace, or null before registration attachment
     */
    public @Nullable String namespace() {
        return namespace;
    }

    /**
     * Attaches the owning namespace to this definition.
     *
     * <p>This is normally called by BlockEngine during registration. Plugins
     * should treat it as an engine-owned value.</p>
     *
     * @param namespace namespace to attach
     * @return this definition
     */
    public @NotNull BlockDefinition namespace(@Nullable String namespace) {
        this.namespace = namespace;
        return this;
    }

    /**
     * Returns the local block name.
     *
     * @return local block name
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * Returns the full BlockEngine block id.
     *
     * @return full block id in {@code namespace:name} form
     * @throws IllegalStateException if no namespace has been attached yet
     */
    public @NotNull String id() {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalStateException("BlockDefinition namespace has not been attached yet.");
        }
        return namespace + ":" + name;
    }

    /**
     * Returns the vanilla block used as the physical backing block.
     *
     * @return backing block material
     */
    public @NotNull Material defaultBlock() {
        return defaultBlock;
    }

    /**
     * Returns the vanilla block used as the physical backing block.
     *
     * @return backing block material
     */
    public @NotNull Material vanillaBlock() {
        return defaultBlock;
    }

    /**
     * Returns whether this block appears in BlockEngine's public catalog data.
     *
     * @return true if included in catalog output
     */
    public boolean catalog() {
        return catalog;
    }

    /**
     * Returns whether this block should appear in BlockEngine's creative menu.
     *
     * @return true if included in the creative menu
     */
    public boolean creativeMenu() {
        return creativeMenu;
    }

    /**
     * Returns the item representation used for inventory and creative-menu
     * entries.
     *
     * @return item definition
     */
    public @NotNull Item item() {
        return item;
    }

    /**
     * Returns the placement rule used to select initial states.
     *
     * @return placement rule
     */
    public @NotNull Placement placement() {
        return placement;
    }

    /**
     * Returns the default state id used when no explicit state is requested.
     *
     * @return default state id
     */
    public @NotNull String defaultState() {
        return defaultState;
    }

    /**
     * Returns all states keyed by state id.
     *
     * @return immutable state map
     */
    public @NotNull Map<String, State> states() {
        return states;
    }

    /**
     * Returns a state by id.
     *
     * @param stateId state id
     * @return matching state
     * @throws IllegalArgumentException if the state id is unknown
     */
    public @NotNull State state(@NotNull String stateId) {
        State state = states.get(stateId);
        if (state == null) {
            throw new IllegalArgumentException("Unknown block state: " + stateId);
        }
        return state;
    }

    /**
     * Validates identifiers and state texture requirements.
     *
     * @throws IllegalStateException if the definition cannot be registered
     */
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

    /**
     * Built-in placement rules for choosing an initial block state.
     */
    public enum Placement {
        /**
         * Do not modify the requested state during placement.
         */
        NONE,

        /**
         * Choose a horizontal facing state from the player's placement direction.
         */
        HORIZONTAL_FACING,

        /**
         * Choose an axis state from the clicked face.
         */
        AXIS,

        /**
         * Choose a full directional state from the clicked face.
         */
        DIRECTIONAL
    }

    /**
     * Tool categories used by mining and drop rules.
     */
    public enum ToolType {
        /**
         * Pickaxe-like tools.
         */
        PICKAXE,
        /**
         * Axe-like tools.
         */
        AXE,
        /**
         * Shovel-like tools.
         */
        SHOVEL,
        /**
         * Hoe-like tools.
         */
        HOE,
        /**
         * Shears.
         */
        SHEARS,
        /**
         * Sword-like tools.
         */
        SWORD
    }

    /**
     * Inventory item representation for a custom block.
     *
     * @param material item material used for the stack
     * @param name display name string, or null for the generated default
     * @param lore lore lines
     * @param glint true to apply enchantment glint
     * @param placeable true if the item can place the custom block
     */
    public record Item(
            @NotNull Material material,
            @Nullable String name,
            @NotNull List<String> lore,
            boolean glint,
            boolean placeable
    ) {
        public Item {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(lore, "lore");
            lore = List.copyOf(lore);
        }
    }

    /**
     * Static state values for one named custom block state.
     *
     * @param hardness base break hardness
     * @param miningSpeed mining speed multiplier
     * @param miningProfile vanilla block used as the mining behavior profile
     * @param preferredTools tools preferred for drops and mining checks
     * @param requirePreferredToolForDrops true to require a preferred tool for drops
     * @param requireSilkTouchForDrops true to require Silk Touch for drops
     * @param movement gravity and dispenser movement rules
     * @param unbreakable true to prevent normal breaking
     * @param dropsItem true to drop the custom block item when broken
     * @param dropInCreative true to drop items when broken in creative mode
     * @param textures resource-pack textures for this state
     * @param sounds sound keys for block actions
     */
    public record State(
            float hardness,
            float miningSpeed,
            @NotNull Material miningProfile,
            @NotNull Set<ToolType> preferredTools,
            boolean requirePreferredToolForDrops,
            boolean requireSilkTouchForDrops,
            @NotNull Movement movement,
            boolean unbreakable,
            boolean dropsItem,
            boolean dropInCreative,
            @NotNull Textures textures,
            @NotNull Sounds sounds
    ) {
        public State {
            Objects.requireNonNull(miningProfile, "miningProfile");
            Objects.requireNonNull(preferredTools, "preferredTools");
            preferredTools = preferredTools.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(EnumSet.copyOf(preferredTools));
            Objects.requireNonNull(movement, "movement");
            Objects.requireNonNull(textures, "textures");
            Objects.requireNonNull(sounds, "sounds");
        }

        private void validate(@NotNull String stateId) {
            if (textures.isEmpty()) {
                throw new IllegalStateException("Block state '" + stateId + "' must define at least one texture.");
            }
        }
    }

    /**
     * Texture references for one custom block state.
     *
     * <p>Texture strings are resource-pack references without the
     * {@code textures/} prefix or {@code .png} extension.</p>
     *
     * @param all texture applied to all faces
     * @param side texture applied to side faces
     * @param front texture applied to the front face
     * @param top texture applied to the top face
     * @param bottom texture applied to the bottom face
     * @param north texture applied to the north face
     * @param south texture applied to the south face
     * @param east texture applied to the east face
     * @param west texture applied to the west face
     */
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
        /**
         * Returns whether no texture references are defined.
         *
         * @return true when all texture references are null
         */
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

    /**
     * Movement rules for a custom block state.
     *
     * @param gravity true if the block should fall like sand
     * @param dispenserPlaceable true if dispensers may place this block
     * @param gravityBreaksOnPartialBlock true if gravity should break on partial supports
     */
    public record Movement(
            boolean gravity,
            boolean dispenserPlaceable,
            boolean gravityBreaksOnPartialBlock
    ) {
        /**
         * Returns the default non-gravity movement settings.
         *
         * @return normal movement settings
         */
        public static @NotNull Movement normal() {
            return new Movement(false, true, false);
        }
    }

    /**
     * Sound keys for one custom block state.
     *
     * @param place sound played when placed
     * @param breakSound sound played when broken
     * @param mining sound played while mining
     * @param step sound played when stepped on
     * @param hit sound played when hit
     * @param fall sound played when fallen on
     */
    public record Sounds(
            @Nullable String place,
            @Nullable String breakSound,
            @NotNull String mining,
            @Nullable String step,
            @Nullable String hit,
            @Nullable String fall
    ) {
        public Sounds {
            Objects.requireNonNull(mining, "mining");
        }

        /**
         * Returns default mostly silent sound settings with a safe mining sound.
         *
         * @return empty sound set
         */
        public static @NotNull Sounds empty() {
            return new Sounds(null, null, "minecraft:block.stone.hit", null, null, null);
        }
    }

    /**
     * Mutable builder for {@link BlockDefinition}.
     */
    public static final class Builder {
        private final @NotNull String name;
        private @NotNull Material defaultBlock = Material.STONE;
        private boolean catalog = true;
        private boolean creativeMenu = true;
        private @NotNull Item item = new Item(Material.KNOWLEDGE_BOOK, null, List.of(), false, true);
        private @NotNull Placement placement = Placement.NONE;
        private @NotNull String defaultState = "default";
        private final @NotNull Map<String, State> states = new LinkedHashMap<>();

        private Builder(@NotNull String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        /**
         * Sets the vanilla block used as the physical backing block.
         *
         * <p>This controls the real Bukkit block placed in the world. Avoid
         * changing it after worlds contain this custom block unless you are
         * deliberately migrating old data.</p>
         *
         * @param defaultBlock backing block material
         * @return this builder
         * @throws IllegalArgumentException if the material is not a block
         */
        public @NotNull Builder setDefaultBlock(@NotNull Material defaultBlock) {
            Objects.requireNonNull(defaultBlock, "defaultBlock");
            if (!defaultBlock.isBlock()) {
                throw new IllegalArgumentException("Default block fallback must be a block material: " + defaultBlock);
            }
            this.defaultBlock = defaultBlock;
            return this;
        }

        /**
         * Sets the vanilla block used as the physical backing block.
         *
         * @param vanillaBlock backing block material
         * @return this builder
         */
        public @NotNull Builder vanillaBlock(@NotNull Material vanillaBlock) {
            return setDefaultBlock(vanillaBlock);
        }

        /**
         * Sets whether this block appears in catalog data.
         *
         * @param catalog true to include the block in catalog output
         * @return this builder
         */
        public @NotNull Builder catalog(boolean catalog) {
            this.catalog = catalog;
            return this;
        }

        /**
         * Sets whether this block appears in the generated creative menu.
         *
         * <p>This defaults to true. Disable it for internal, transitional, or
         * data-only blocks that should still be usable by API calls.</p>
         *
         * @param creativeMenu true to include this block in the creative menu
         * @return this builder
         */
        public @NotNull Builder creativeMenu(boolean creativeMenu) {
            this.creativeMenu = creativeMenu;
            return this;
        }

        /**
         * Configures the inventory item representation for this block.
         *
         * @param configure item builder callback
         * @return this builder
         */
        public @NotNull Builder item(@NotNull Consumer<ItemBuilder> configure) {
            Objects.requireNonNull(configure, "configure");
            ItemBuilder builder = new ItemBuilder();
            configure.accept(builder);
            this.item = builder.build();
            return this;
        }

        /**
         * Sets the placement rule used for this block.
         *
         * @param placement placement rule
         * @return this builder
         */
        public @NotNull Builder placement(@NotNull Placement placement) {
            this.placement = Objects.requireNonNull(placement, "placement");
            return this;
        }

        /**
         * Sets the default state id.
         *
         * @param defaultState default state id
         * @return this builder
         */
        public @NotNull Builder defaultState(@NotNull String defaultState) {
            this.defaultState = Objects.requireNonNull(defaultState, "defaultState");
            return this;
        }

        /**
         * Adds or replaces a named state.
         *
         * @param id state id
         * @param configure state builder callback
         * @return this builder
         */
        public @NotNull Builder state(@NotNull String id, @NotNull Consumer<StateBuilder> configure) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(configure, "configure");

            StateBuilder builder = new StateBuilder();
            configure.accept(builder);
            states.put(id, builder.build(defaultBlock));
            return this;
        }

        /**
         * Builds an immutable block definition.
         *
         * <p>If no states were configured, a default state with a
         * {@code missing} texture reference is created so validation can report
         * a normal missing asset instead of failing with an empty state map.</p>
         *
         * @return immutable block definition
         */
        public @NotNull BlockDefinition build() {
            if (states.isEmpty()) {
                state(defaultState, state -> state.textures(textures -> textures.all("missing")));
            }
            return new BlockDefinition(name, defaultBlock, catalog, creativeMenu, item, placement, defaultState, states);
        }
    }

    /**
     * Mutable builder for {@link Item}.
     */
    public static final class ItemBuilder {
        private @NotNull Material material = Material.KNOWLEDGE_BOOK;
        private @Nullable String name;
        private final @NotNull List<String> lore = new ArrayList<>();
        private boolean glint = false;
        private boolean placeable = true;

        /**
         * Sets the Bukkit item material used for this block's inventory stack.
         *
         * @param material item material
         * @return this builder
         * @throws IllegalArgumentException if the material is not an item
         */
        public @NotNull ItemBuilder material(@NotNull Material material) {
            Objects.requireNonNull(material, "material");
            if (!material.isItem()) {
                throw new IllegalArgumentException("Block item material must be an item material: " + material);
            }
            this.material = material;
            return this;
        }

        /**
         * Sets the display name used for this block's inventory item.
         *
         * @param name display name, or null for the generated default
         * @return this builder
         */
        public @NotNull ItemBuilder name(@Nullable String name) {
            this.name = name;
            return this;
        }

        /**
         * Adds one lore line to this block's inventory item.
         *
         * @param line lore line
         * @return this builder
         */
        public @NotNull ItemBuilder lore(@NotNull String line) {
            this.lore.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        /**
         * Replaces all lore lines for this block's inventory item.
         *
         * @param lore lore lines
         * @return this builder
         */
        public @NotNull ItemBuilder lore(@NotNull List<String> lore) {
            this.lore.clear();
            this.lore.addAll(Objects.requireNonNull(lore, "lore"));
            return this;
        }

        /**
         * Sets whether the item should render with enchantment glint.
         *
         * @param glint true to show glint
         * @return this builder
         */
        public @NotNull ItemBuilder glint(boolean glint) {
            this.glint = glint;
            return this;
        }

        /**
         * Sets whether this item can place the custom block.
         *
         * <p>Leave this true for normal block items. Set it false for catalog or
         * display-only items that should not enter the placement pipeline.</p>
         *
         * @param placeable true if the item should place the block
         * @return this builder
         */
        public @NotNull ItemBuilder placeable(boolean placeable) {
            this.placeable = placeable;
            return this;
        }

        private @NotNull Item build() {
            return new Item(material, name, lore, glint, placeable);
        }
    }

    /**
     * Mutable builder for {@link State}.
     */
    public static final class StateBuilder {
        private float hardness = 0.5f;
        private float miningSpeed = 1.0f;
        private @Nullable Material miningProfile;
        private final @NotNull Set<ToolType> preferredTools = EnumSet.noneOf(ToolType.class);
        private boolean requirePreferredToolForDrops = false;
        private boolean requireSilkTouchForDrops = false;
        private @NotNull Movement movement = Movement.normal();
        private boolean unbreakable = false;
        private boolean dropsItem = true;
        private boolean dropInCreative = false;
        private @NotNull Textures textures = new Textures(null, null, null, null, null, null, null, null, null);
        private @NotNull Sounds sounds = Sounds.empty();

        /**
         * Sets the base hardness used by BlockEngine break logic.
         *
         * @param hardness block hardness
         * @return this builder
         */
        public @NotNull StateBuilder hardness(float hardness) {
            this.hardness = hardness;
            return this;
        }

        /**
         * Sets the mining speed multiplier for this state.
         *
         * @param miningSpeed mining speed multiplier
         * @return this builder
         */
        public @NotNull StateBuilder miningSpeed(float miningSpeed) {
            this.miningSpeed = miningSpeed;
            return this;
        }

        /**
         * Sets the vanilla block whose mining behavior should be used as a
         * profile for this state.
         *
         * @param miningProfile block material used as mining profile
         * @return this builder
         * @throws IllegalArgumentException if the material is not a block
         */
        public @NotNull StateBuilder miningProfile(@NotNull Material miningProfile) {
            Objects.requireNonNull(miningProfile, "miningProfile");
            if (!miningProfile.isBlock()) {
                throw new IllegalArgumentException("Mining profile must be a block material: " + miningProfile);
            }
            this.miningProfile = miningProfile;
            return this;
        }

        /**
         * Adds one preferred tool category.
         *
         * @param tool preferred tool
         * @return this builder
         */
        public @NotNull StateBuilder preferredTool(@NotNull ToolType tool) {
            this.preferredTools.add(Objects.requireNonNull(tool, "tool"));
            return this;
        }

        /**
         * Adds multiple preferred tool categories.
         *
         * @param first first preferred tool
         * @param additional additional preferred tools
         * @return this builder
         */
        public @NotNull StateBuilder preferredTools(@NotNull ToolType first, ToolType @NotNull ... additional) {
            this.preferredTools.add(Objects.requireNonNull(first, "first"));
            Objects.requireNonNull(additional, "additional");
            Collections.addAll(this.preferredTools, additional);
            return this;
        }

        /**
         * Replaces preferred tool categories.
         *
         * @param tools preferred tools
         * @return this builder
         */
        public @NotNull StateBuilder preferredTools(@NotNull Set<ToolType> tools) {
            this.preferredTools.clear();
            this.preferredTools.addAll(Objects.requireNonNull(tools, "tools"));
            return this;
        }

        /**
         * Sets whether drops require one of the preferred tools.
         *
         * @param requirePreferredToolForDrops true to require a preferred tool
         * @return this builder
         */
        public @NotNull StateBuilder requirePreferredToolForDrops(boolean requirePreferredToolForDrops) {
            this.requirePreferredToolForDrops = requirePreferredToolForDrops;
            return this;
        }

        /**
         * Sets whether drops require Silk Touch.
         *
         * @param requireSilkTouchForDrops true to require Silk Touch
         * @return this builder
         */
        public @NotNull StateBuilder requireSilkTouchForDrops(boolean requireSilkTouchForDrops) {
            this.requireSilkTouchForDrops = requireSilkTouchForDrops;
            return this;
        }

        /**
         * Configures movement behavior for this state.
         *
         * @param configure movement builder callback
         * @return this builder
         */
        public @NotNull StateBuilder movement(@NotNull Consumer<MovementBuilder> configure) {
            Objects.requireNonNull(configure, "configure");
            MovementBuilder builder = new MovementBuilder();
            configure.accept(builder);
            this.movement = builder.build();
            return this;
        }

        /**
         * Sets whether this state should fall like a gravity block.
         *
         * @param gravity true to enable gravity
         * @return this builder
         */
        public @NotNull StateBuilder gravity(boolean gravity) {
            movement = new Movement(gravity, movement.dispenserPlaceable(), movement.gravityBreaksOnPartialBlock());
            return this;
        }

        /**
         * Sets whether dispensers can place this state.
         *
         * @param dispenserPlaceable true to allow dispenser placement
         * @return this builder
         */
        public @NotNull StateBuilder dispenserPlaceable(boolean dispenserPlaceable) {
            movement = new Movement(movement.gravity(), dispenserPlaceable, movement.gravityBreaksOnPartialBlock());
            return this;
        }

        /**
         * Sets whether gravity blocks break when landing on partial supports.
         *
         * @param gravityBreaksOnPartialBlock true to break on partial supports
         * @return this builder
         */
        public @NotNull StateBuilder gravityBreaksOnPartialBlock(boolean gravityBreaksOnPartialBlock) {
            movement = new Movement(movement.gravity(), movement.dispenserPlaceable(), gravityBreaksOnPartialBlock);
            return this;
        }

        /**
         * Sets whether normal breaking should be prevented for this state.
         *
         * @param unbreakable true to make the state unbreakable
         * @return this builder
         */
        public @NotNull StateBuilder unbreakable(boolean unbreakable) {
            this.unbreakable = unbreakable;
            return this;
        }

        /**
         * Sets whether this state drops its block item when broken.
         *
         * @param dropsItem true to drop the custom block item
         * @return this builder
         */
        public @NotNull StateBuilder dropsItem(boolean dropsItem) {
            this.dropsItem = dropsItem;
            return this;
        }

        /**
         * Sets whether this state drops items when broken in creative mode.
         *
         * @param dropInCreative true to drop in creative mode
         * @return this builder
         */
        public @NotNull StateBuilder dropInCreative(boolean dropInCreative) {
            this.dropInCreative = dropInCreative;
            return this;
        }

        /**
         * Configures texture references for this state.
         *
         * @param configure texture builder callback
         * @return this builder
         */
        public @NotNull StateBuilder textures(@NotNull Consumer<TexturesBuilder> configure) {
            Objects.requireNonNull(configure, "configure");
            TexturesBuilder builder = new TexturesBuilder();
            configure.accept(builder);
            this.textures = builder.build();
            return this;
        }

        /**
         * Configures sounds for this state.
         *
         * @param configure sounds builder callback
         * @return this builder
         */
        public @NotNull StateBuilder sounds(@NotNull Consumer<SoundsBuilder> configure) {
            Objects.requireNonNull(configure, "configure");
            SoundsBuilder builder = new SoundsBuilder();
            configure.accept(builder);
            this.sounds = builder.build();
            return this;
        }

        private @NotNull State build(@NotNull Material defaultBlock) {
            return new State(
                    hardness,
                    miningSpeed,
                    miningProfile == null ? defaultBlock : miningProfile,
                    preferredTools,
                    requirePreferredToolForDrops,
                    requireSilkTouchForDrops,
                    movement,
                    unbreakable,
                    dropsItem,
                    dropInCreative,
                    textures,
                    sounds
            );
        }
    }

    /**
     * Mutable builder for {@link Movement}.
     */
    public static final class MovementBuilder {
        private boolean gravity = false;
        private boolean dispenserPlaceable = true;
        private boolean gravityBreaksOnPartialBlock = false;

        /**
         * Sets whether this state should fall like a gravity block.
         *
         * @param gravity true to enable gravity
         * @return this builder
         */
        public @NotNull MovementBuilder gravity(boolean gravity) {
            this.gravity = gravity;
            return this;
        }

        /**
         * Sets whether dispensers can place this state.
         *
         * @param dispenserPlaceable true to allow dispenser placement
         * @return this builder
         */
        public @NotNull MovementBuilder dispenserPlaceable(boolean dispenserPlaceable) {
            this.dispenserPlaceable = dispenserPlaceable;
            return this;
        }

        /**
         * When enabled, gravity blocks break and drop instead of landing on
         * partial collision supports such as slabs, stairs, signs, or torches.
         *
         * @param gravityBreaksOnPartialBlock true to break on partial supports
         * @return this builder
         */
        public @NotNull MovementBuilder breaksViaGravity(boolean gravityBreaksOnPartialBlock) {
            this.gravityBreaksOnPartialBlock = gravityBreaksOnPartialBlock;
            return this;
        }

        private @NotNull Movement build() {
            return new Movement(gravity, dispenserPlaceable, gravityBreaksOnPartialBlock);
        }
    }

    /**
     * Mutable builder for {@link Textures}.
     */
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

        /**
         * Sets the texture used for every face.
         *
         * @param all texture reference, or null to clear
         * @return this builder
         */
        public @NotNull TexturesBuilder all(@Nullable String all) {
            this.all = all;
            return this;
        }

        /**
         * Sets the texture used for side faces.
         *
         * @param side texture reference, or null to clear
         * @return this builder
         */
        public @NotNull TexturesBuilder side(@Nullable String side) {
            this.side = side;
            return this;
        }

        /**
         * Sets the texture used for the front face.
         *
         * @param front texture reference, or null to clear
         * @return this builder
         */
        public @NotNull TexturesBuilder front(@Nullable String front) {
            this.front = front;
            return this;
        }

        /**
         * Sets the texture used for the top face.
         *
         * @param top texture reference, or null to clear
         * @return this builder
         */
        public @NotNull TexturesBuilder top(@Nullable String top) {
            this.top = top;
            return this;
        }

        /**
         * Sets the texture used for the bottom face.
         *
         * @param bottom texture reference, or null to clear
         * @return this builder
         */
        public @NotNull TexturesBuilder bottom(@Nullable String bottom) {
            this.bottom = bottom;
            return this;
        }

        /**
         * Sets the texture used for the north face.
         *
         * @param north texture reference, or null to clear
         * @return this builder
         */
        public @NotNull TexturesBuilder north(@Nullable String north) {
            this.north = north;
            return this;
        }

        /**
         * Sets the texture used for the south face.
         *
         * @param south texture reference, or null to clear
         * @return this builder
         */
        public @NotNull TexturesBuilder south(@Nullable String south) {
            this.south = south;
            return this;
        }

        /**
         * Sets the texture used for the east face.
         *
         * @param east texture reference, or null to clear
         * @return this builder
         */
        public @NotNull TexturesBuilder east(@Nullable String east) {
            this.east = east;
            return this;
        }

        /**
         * Sets the texture used for the west face.
         *
         * @param west texture reference, or null to clear
         * @return this builder
         */
        public @NotNull TexturesBuilder west(@Nullable String west) {
            this.west = west;
            return this;
        }

        private @NotNull Textures build() {
            return new Textures(all, side, front, top, bottom, north, south, east, west);
        }
    }

    /**
     * Mutable builder for {@link Sounds}.
     */
    public static final class SoundsBuilder {
        private @Nullable String place;
        private @Nullable String breakSound;
        private @NotNull String mining = "minecraft:block.stone.hit";
        private @Nullable String step;
        private @Nullable String hit;
        private @Nullable String fall;

        /**
         * Sets the sound played when this state is placed.
         *
         * @param place sound key, or null to clear
         * @return this builder
         */
        public @NotNull SoundsBuilder place(@Nullable String place) {
            this.place = place;
            return this;
        }

        /**
         * Sets the sound played when this state is broken.
         *
         * @param breakSound sound key, or null to clear
         * @return this builder
         */
        public @NotNull SoundsBuilder breakSound(@Nullable String breakSound) {
            this.breakSound = breakSound;
            return this;
        }

        /**
         * Sets the sound played while this state is mined.
         *
         * @param mining sound key
         * @return this builder
         */
        public @NotNull SoundsBuilder mining(@NotNull String mining) {
            this.mining = Objects.requireNonNull(mining, "mining");
            return this;
        }

        /**
         * Sets the sound played when an entity steps on this state.
         *
         * @param step sound key, or null to clear
         * @return this builder
         */
        public @NotNull SoundsBuilder step(@Nullable String step) {
            this.step = step;
            return this;
        }

        /**
         * Sets the sound played when this state is hit.
         *
         * @param hit sound key, or null to clear
         * @return this builder
         */
        public @NotNull SoundsBuilder hit(@Nullable String hit) {
            this.hit = hit;
            return this;
        }

        /**
         * Sets the sound played when an entity falls on this state.
         *
         * @param fall sound key, or null to clear
         * @return this builder
         */
        public @NotNull SoundsBuilder fall(@Nullable String fall) {
            this.fall = fall;
            return this;
        }

        private @NotNull Sounds build() {
            return new Sounds(place, breakSound, mining, step, hit, fall);
        }
    }
}
