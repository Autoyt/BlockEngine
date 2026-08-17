package dev.auto.blockengine.defaultadapters;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockCreateContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public final class DemoMiningBlockAdapter implements BlockAdapter {
    @Override
    public @NotNull String name() {
        return "demo_mining";
    }

    @Override
    public void define(@NotNull BlockDefinition.Builder builder) {
        builder
                .setDefaultBlock(Material.LIME_CONCRETE)
                .defaultState("default")
                .item(item -> item
                        .name("<#7dff42>Demo: Mining Speed")
                        .lore("<gray>Low hardness and high mining speed.")
                        .lore("<gray>Also drops when broken in creative.")
                )
                .state("default", state -> state
                        .hardness(0.25f)
                        .miningSpeed(3.0f)
                        .dropsItem(true)
                        .dropInCreative(true)
                        .textures(textures -> textures.all("blockengine_test:block/demo_mining"))
                        .sounds(sounds -> sounds
                                .mining("minecraft:block.grass.hit")
                                .breakSound("minecraft:block.grass.break")
                                .place("minecraft:block.grass.place")
                        )
                );
    }

    @Override
    public @NotNull BlockData createDefaultData(@NotNull BlockCreateContext context) {
        return context.createData();
    }
}
