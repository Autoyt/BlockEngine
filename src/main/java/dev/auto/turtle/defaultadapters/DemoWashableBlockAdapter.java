package dev.auto.turtle.defaultadapters;

import dev.auto.turtle.api.blocks.BlockAdapter;
import dev.auto.turtle.api.blocks.BlockCreateContext;
import dev.auto.turtle.api.blocks.BlockData;
import dev.auto.turtle.api.blocks.BlockDefinition;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public final class DemoWashableBlockAdapter implements BlockAdapter {
    @Override
    public @NotNull String name() {
        return "demo_washable";
    }

    @Override
    public void define(@NotNull BlockDefinition.Builder builder) {
        builder
                .setDefaultBlock(Material.CYAN_CONCRETE)
                .defaultState("default")
                .item(item -> item
                        .name("<#00c8d7>Demo: Washable")
                        .lore("<gray>Water flow should break and drop this block.")
                )
                .state("default", state -> state
                        .hardness(0.6f)
                        .miningSpeed(1.0f)
                        .washable(true)
                        .dropsItem(true)
                        .textures(textures -> textures.all("turtle_test:block/demo_washable"))
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
