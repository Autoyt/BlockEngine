package dev.auto.turtle.defaultadapters;

import dev.auto.turtle.api.blocks.BlockAdapter;
import dev.auto.turtle.api.blocks.BlockCreateContext;
import dev.auto.turtle.api.blocks.BlockData;
import dev.auto.turtle.api.blocks.BlockDefinition;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public final class InvisibleBlockAdapter implements BlockAdapter {
    @Override
    public @NotNull String name() {
        return "invisible_block";
    }

    @Override
    public void define(@NotNull BlockDefinition.Builder builder) {
        builder
                .setDefaultBlock(Material.GLASS)
                .defaultState("default")
                .item(item -> item
                        .name("<white>Turtle Test Invisible Block")
                        .lore("<gray>Debug block: turtle_test:invisible_block")
                        .lore("<gray>Uses a transparent cube texture.")
                )
                .state("default", state -> state
                        .hardness(0.3f)
                        .miningSpeed(1.0f)
                        .unbreakable(true)
                        .dropsItem(false)
                        .textures(textures -> textures.all("turtle_test:block/transparent"))
                        .sounds(sounds -> sounds
                                .mining("minecraft:block.glass.hit")
                                .breakSound("minecraft:block.glass.break")
                                .place("minecraft:block.glass.place")
                        )
                );
    }

    @Override
    public @NotNull BlockData createDefaultData(@NotNull BlockCreateContext context) {
        return context.createData();
    }
}
