package dev.auto.blockengine.datapack;

import dev.auto.blockengine.api.blocks.BlockDefinition;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record BlockPackBlock(
        @NotNull String name,
        @NotNull Material vanillaBlock,
        boolean catalog,
        boolean creativeMenu,
        @NotNull BlockDefinition.Placement placement,
        @NotNull Item item,
        @NotNull String defaultState,
        @NotNull Map<String, State> states
) {
    public BlockPackBlock {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(vanillaBlock, "vanillaBlock");
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
            @NotNull BlockDefinition.Sounds sounds
    ) {
        public State {
            Objects.requireNonNull(miningProfile, "miningProfile");
            preferredTools = Set.copyOf(Objects.requireNonNull(preferredTools, "preferredTools"));
            Objects.requireNonNull(movement, "movement");
            Objects.requireNonNull(textures, "textures");
            Objects.requireNonNull(sounds, "sounds");
        }
    }
}
