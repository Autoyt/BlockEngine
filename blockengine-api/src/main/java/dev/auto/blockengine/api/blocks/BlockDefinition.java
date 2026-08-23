package dev.auto.blockengine.api.blocks;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
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
 */
public final class BlockDefinition {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9._-]+(?:/[a-z0-9._-]+)*$");

    private @Nullable String namespace;
    private final @NotNull String name;
    private final @NotNull Material defaultBlock;
    private final boolean catalog;
    private final @NotNull Item item;
    private final @NotNull Placement placement;
    private final @NotNull String defaultState;
    private final @NotNull Map<String, State> states;

    private BlockDefinition(
            @NotNull String name,
            @NotNull Material defaultBlock,
            boolean catalog,
            @NotNull Item item,
            @NotNull Placement placement,
            @NotNull String defaultState,
            @NotNull Map<String, State> states
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.defaultBlock = Objects.requireNonNull(defaultBlock, "defaultBlock");
        this.catalog = catalog;
        this.item = Objects.requireNonNull(item, "item");
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

    public @NotNull Material defaultBlock() {
        return defaultBlock;
    }

    public @NotNull Material vanillaBlock() {
        return defaultBlock;
    }

    public boolean catalog() {
        return catalog;
    }

    public @NotNull Item item() {
        return item;
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

    public enum ToolType {
        PICKAXE,
        AXE,
        SHOVEL,
        HOE,
        SHEARS,
        SWORD
    }

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

    public record State(
            float hardness,
            float miningSpeed,
            @NotNull Material miningProfile,
            @NotNull Set<ToolType> preferredTools,
            boolean requirePreferredToolForDrops,
            boolean requireSilkTouchForDrops,
            @NotNull Redstone redstone,
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
            Objects.requireNonNull(redstone, "redstone");
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

    public record Redstone(
            @NotNull Set<BlockFace> inputFaces,
            @NotNull Set<BlockFace> outputFaces,
            int weakPower,
            int strongPower
    ) {
        public Redstone {
            Objects.requireNonNull(inputFaces, "inputFaces");
            Objects.requireNonNull(outputFaces, "outputFaces");
            inputFaces = immutableFaces(inputFaces);
            outputFaces = immutableFaces(outputFaces);
            weakPower = Math.clamp(weakPower, 0, 15);
            strongPower = Math.clamp(strongPower, 0, 15);
        }

        public boolean hasInputs() {
            return !inputFaces.isEmpty();
        }

        public boolean hasOutputs() {
            return !outputFaces.isEmpty() && (weakPower > 0 || strongPower > 0);
        }

        public static @NotNull Redstone none() {
            return new Redstone(Set.of(), Set.of(), 0, 0);
        }
    }

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

        public static @NotNull Sounds empty() {
            return new Sounds(null, null, "minecraft:block.stone.hit", null, null, null);
        }
    }

    public static final class Builder {
        private final @NotNull String name;
        private @NotNull Material defaultBlock = Material.STONE;
        private boolean catalog = true;
        private @NotNull Item item = new Item(Material.KNOWLEDGE_BOOK, null, List.of(), false, true);
        private @NotNull Placement placement = Placement.NONE;
        private @NotNull String defaultState = "default";
        private final @NotNull Map<String, State> states = new LinkedHashMap<>();

        private Builder(@NotNull String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public @NotNull Builder setDefaultBlock(@NotNull Material defaultBlock) {
            Objects.requireNonNull(defaultBlock, "defaultBlock");
            if (!defaultBlock.isBlock()) {
                throw new IllegalArgumentException("Default block fallback must be a block material: " + defaultBlock);
            }
            this.defaultBlock = defaultBlock;
            return this;
        }

        public @NotNull Builder vanillaBlock(@NotNull Material vanillaBlock) {
            return setDefaultBlock(vanillaBlock);
        }

        public @NotNull Builder catalog(boolean catalog) {
            this.catalog = catalog;
            return this;
        }

        public @NotNull Builder item(@NotNull Consumer<ItemBuilder> configure) {
            Objects.requireNonNull(configure, "configure");
            ItemBuilder builder = new ItemBuilder();
            configure.accept(builder);
            this.item = builder.build();
            return this;
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
            states.put(id, builder.build(defaultBlock));
            return this;
        }

        public @NotNull BlockDefinition build() {
            if (states.isEmpty()) {
                state(defaultState, state -> state.textures(textures -> textures.all("missing")));
            }
            return new BlockDefinition(name, defaultBlock, catalog, item, placement, defaultState, states);
        }
    }

    public static final class ItemBuilder {
        private @NotNull Material material = Material.KNOWLEDGE_BOOK;
        private @Nullable String name;
        private final @NotNull List<String> lore = new ArrayList<>();
        private boolean glint = false;
        private boolean placeable = true;

        public @NotNull ItemBuilder material(@NotNull Material material) {
            Objects.requireNonNull(material, "material");
            if (!material.isItem()) {
                throw new IllegalArgumentException("Block item material must be an item material: " + material);
            }
            this.material = material;
            return this;
        }

        public @NotNull ItemBuilder name(@Nullable String name) {
            this.name = name;
            return this;
        }

        public @NotNull ItemBuilder lore(@NotNull String line) {
            this.lore.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        public @NotNull ItemBuilder lore(@NotNull List<String> lore) {
            this.lore.clear();
            this.lore.addAll(Objects.requireNonNull(lore, "lore"));
            return this;
        }

        public @NotNull ItemBuilder glint(boolean glint) {
            this.glint = glint;
            return this;
        }

        public @NotNull ItemBuilder placeable(boolean placeable) {
            this.placeable = placeable;
            return this;
        }

        private @NotNull Item build() {
            return new Item(material, name, lore, glint, placeable);
        }
    }

    public static final class StateBuilder {
        private float hardness = 0.5f;
        private float miningSpeed = 1.0f;
        private @Nullable Material miningProfile;
        private final @NotNull Set<ToolType> preferredTools = EnumSet.noneOf(ToolType.class);
        private boolean requirePreferredToolForDrops = false;
        private boolean requireSilkTouchForDrops = false;
        private @NotNull Redstone redstone = Redstone.none();
        private boolean unbreakable = false;
        private boolean dropsItem = true;
        private boolean dropInCreative = false;
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

        public @NotNull StateBuilder miningProfile(@NotNull Material miningProfile) {
            Objects.requireNonNull(miningProfile, "miningProfile");
            if (!miningProfile.isBlock()) {
                throw new IllegalArgumentException("Mining profile must be a block material: " + miningProfile);
            }
            this.miningProfile = miningProfile;
            return this;
        }

        public @NotNull StateBuilder preferredTool(@NotNull ToolType tool) {
            this.preferredTools.add(Objects.requireNonNull(tool, "tool"));
            return this;
        }

        public @NotNull StateBuilder preferredTools(@NotNull ToolType first, ToolType @NotNull ... additional) {
            this.preferredTools.add(Objects.requireNonNull(first, "first"));
            Objects.requireNonNull(additional, "additional");
            Collections.addAll(this.preferredTools, additional);
            return this;
        }

        public @NotNull StateBuilder preferredTools(@NotNull Set<ToolType> tools) {
            this.preferredTools.clear();
            this.preferredTools.addAll(Objects.requireNonNull(tools, "tools"));
            return this;
        }

        public @NotNull StateBuilder requirePreferredToolForDrops(boolean requirePreferredToolForDrops) {
            this.requirePreferredToolForDrops = requirePreferredToolForDrops;
            return this;
        }

        public @NotNull StateBuilder requireSilkTouchForDrops(boolean requireSilkTouchForDrops) {
            this.requireSilkTouchForDrops = requireSilkTouchForDrops;
            return this;
        }

        public @NotNull StateBuilder redstone(@NotNull Consumer<RedstoneBuilder> configure) {
            Objects.requireNonNull(configure, "configure");
            RedstoneBuilder builder = new RedstoneBuilder();
            configure.accept(builder);
            this.redstone = builder.build();
            return this;
        }

        public @NotNull StateBuilder unbreakable(boolean unbreakable) {
            this.unbreakable = unbreakable;
            return this;
        }

        public @NotNull StateBuilder dropsItem(boolean dropsItem) {
            this.dropsItem = dropsItem;
            return this;
        }

        public @NotNull StateBuilder dropInCreative(boolean dropInCreative) {
            this.dropInCreative = dropInCreative;
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

        private @NotNull State build(@NotNull Material defaultBlock) {
            return new State(
                    hardness,
                    miningSpeed,
                    miningProfile == null ? defaultBlock : miningProfile,
                    preferredTools,
                    requirePreferredToolForDrops,
                    requireSilkTouchForDrops,
                    redstone,
                    unbreakable,
                    dropsItem,
                    dropInCreative,
                    textures,
                    sounds
            );
        }
    }

    public static final class RedstoneBuilder {
        private final @NotNull Set<BlockFace> inputFaces = EnumSet.noneOf(BlockFace.class);
        private final @NotNull Set<BlockFace> outputFaces = EnumSet.noneOf(BlockFace.class);
        private int weakPower = 0;
        private int strongPower = 0;

        public @NotNull RedstoneBuilder inputFaces(BlockFace @NotNull ... faces) {
            inputFaces.clear();
            addFaces(inputFaces, faces);
            return this;
        }

        public @NotNull RedstoneBuilder outputFaces(BlockFace @NotNull ... faces) {
            outputFaces.clear();
            addFaces(outputFaces, faces);
            return this;
        }

        public @NotNull RedstoneBuilder inputAllFaces() {
            inputFaces.clear();
            inputFaces.addAll(allFaces());
            return this;
        }

        public @NotNull RedstoneBuilder outputAllFaces() {
            outputFaces.clear();
            outputFaces.addAll(allFaces());
            return this;
        }

        public @NotNull RedstoneBuilder noInput() {
            inputFaces.clear();
            return this;
        }

        public @NotNull RedstoneBuilder noOutput() {
            outputFaces.clear();
            weakPower = 0;
            strongPower = 0;
            return this;
        }

        public @NotNull RedstoneBuilder weakPower(int weakPower) {
            this.weakPower = Math.clamp(weakPower, 0, 15);
            return this;
        }

        public @NotNull RedstoneBuilder strongPower(int strongPower) {
            this.strongPower = Math.clamp(strongPower, 0, 15);
            return this;
        }

        private @NotNull Redstone build() {
            return new Redstone(inputFaces, outputFaces, weakPower, strongPower);
        }

        private static void addFaces(@NotNull Set<BlockFace> target, BlockFace @NotNull [] faces) {
            Objects.requireNonNull(faces, "faces");
            for (BlockFace face : faces) {
                validateRedstoneFace(face);
                target.add(face);
            }
        }
    }

    private static @NotNull Set<BlockFace> allFaces() {
        return EnumSet.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN);
    }

    private static @NotNull Set<BlockFace> immutableFaces(@NotNull Set<BlockFace> faces) {
        if (faces.isEmpty()) {
            return Set.of();
        }
        EnumSet<BlockFace> copy = EnumSet.noneOf(BlockFace.class);
        for (BlockFace face : faces) {
            validateRedstoneFace(face);
            copy.add(face);
        }
        return Collections.unmodifiableSet(copy);
    }

    private static void validateRedstoneFace(@NotNull BlockFace face) {
        Objects.requireNonNull(face, "face");
        if (!allFaces().contains(face)) {
            throw new IllegalArgumentException("Redstone face must be one of the six block faces: " + face);
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
        private @NotNull String mining = "minecraft:block.stone.hit";
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

        public @NotNull SoundsBuilder mining(@NotNull String mining) {
            this.mining = Objects.requireNonNull(mining, "mining");
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
            return new Sounds(place, breakSound, mining, step, hit, fall);
        }
    }
}
