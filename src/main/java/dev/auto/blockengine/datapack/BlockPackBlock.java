package dev.auto.blockengine.datapack;

import dev.auto.blockengine.api.blocks.BlockDefinition;
import dev.auto.blockengine.api.drops.Fortune;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record BlockPackBlock(
        @NotNull String name,
        boolean catalog,
        boolean creativeMenu,
        @NotNull BlockDefinition.Placement placement,
        @NotNull Item item,
        @NotNull String defaultState,
        @NotNull Map<String, State> states
) {
    public BlockPackBlock {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(defaultState, "defaultState");
        states = Map.copyOf(Objects.requireNonNull(states, "states"));
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
            lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        }
    }

    public record State(
            float hardness,
            float miningSpeed,
            @NotNull Material miningProfile,
            @NotNull Set<BlockDefinition.ToolType> preferredTools,
            boolean requirePreferredToolForDrops,
            boolean requireSilkTouchForDrops,
            @NotNull BlockDefinition.Movement movement,
            boolean unbreakable,
            boolean dropsItem,
            boolean dropInCreative,
            @NotNull BlockDefinition.Textures textures,
            @NotNull BlockDefinition.Sounds sounds,
            @Nullable List<Drop> drops,
            boolean silkTouchSelf
    ) {
        public State {
            Objects.requireNonNull(miningProfile, "miningProfile");
            preferredTools = Set.copyOf(Objects.requireNonNull(preferredTools, "preferredTools"));
            Objects.requireNonNull(movement, "movement");
            Objects.requireNonNull(textures, "textures");
            Objects.requireNonNull(sounds, "sounds");
            drops = drops == null ? null : List.copyOf(drops);
        }

        public State(
                float hardness,
                float miningSpeed,
                @NotNull Material miningProfile,
                @NotNull Set<BlockDefinition.ToolType> preferredTools,
                boolean requirePreferredToolForDrops,
                boolean requireSilkTouchForDrops,
                @NotNull BlockDefinition.Movement movement,
                boolean unbreakable,
                boolean dropsItem,
                boolean dropInCreative,
                @NotNull BlockDefinition.Textures textures,
                @NotNull BlockDefinition.Sounds sounds
        ) {
            this(hardness, miningSpeed, miningProfile, preferredTools,
                    requirePreferredToolForDrops, requireSilkTouchForDrops, movement,
                    unbreakable, dropsItem, dropInCreative, textures, sounds, null, false);
        }
    }

    public record Drop(
            @NotNull DropItem item,
            @NotNull DropCount count,
            double chance,
            @NotNull Fortune fortune
    ) {
        public Drop {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(count, "count");
            Objects.requireNonNull(fortune, "fortune");
            if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
                throw new IllegalArgumentException("Drop chance must be a finite value from zero through one.");
            }
        }
    }

    public record DropCount(int min, int max) {
        public DropCount {
            if (min < 0 || max < min) {
                throw new IllegalArgumentException("Drop counts require 0 <= min <= max.");
            }
        }
    }

    public record DropItem(
            @Nullable Material material,
            @Nullable String blockId,
            @Nullable String stateId,
            boolean self
    ) {
        public DropItem {
            int sources = (material == null ? 0 : 1) + (blockId == null ? 0 : 1) + (self ? 1 : 0);
            if (sources != 1) {
                throw new IllegalArgumentException("A drop item must have exactly one source.");
            }
            if (stateId != null && blockId == null) {
                throw new IllegalArgumentException("A drop item state requires a block source.");
            }
        }
    }
}
