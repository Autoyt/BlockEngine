package dev.auto.blockengine.defaultadapters;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockCreateContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public final class RedBlockAdapter implements BlockAdapter {
    @Override
    public @NotNull String name() {
        return "red_block";
    }

    @Override
    public void define(@NotNull BlockDefinition.Builder builder) {
        builder
                .setDefaultBlock(Material.RED_CONCRETE)
                .defaultState("default")
                .item(item -> item
                        .name("BlockEngine Test Red Block")
                        .lore("Debug block: blockengine_test:red_block")
                )
                .state("default", state -> state
                        .hardness(1.8f)
                        .miningSpeed(1.0f)
                        .dropsItem(true)
                        .textures(textures -> textures.all("minecraft:block/red_concrete"))
                        .sounds(sounds -> sounds
                                .mining("minecraft:block.stone.hit")
                                .breakSound("minecraft:block.stone.break")
                                .place("minecraft:block.stone.place")
                        )
                );
    }

    @Override
    public @NotNull BlockData createDefaultData(@NotNull BlockCreateContext context) {
        return context.createData();
    }
}
