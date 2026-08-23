package dev.auto.blockengine.defaultadapters;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockCreateContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public final class DemoMovementBlockAdapter implements BlockAdapter {
    @Override
    public @NotNull String name() {
        return "demo_movement";
    }

    @Override
    public void define(@NotNull BlockDefinition.Builder builder) {
        builder
                .setDefaultBlock(Material.CYAN_CONCRETE)
                .defaultState("default")
                .item(item -> item
                        .name("<aqua>Demo: Movement Block")
                        .lore("<gray>Falls and can be placed by dispensers.")
                )
                .state("default", state -> state
                        .hardness(1.0f)
                        .miningSpeed(1.0f)
                        .dropsItem(true)
                        .movement(movement -> movement
                                .gravity(true)
                                .breaksViaGravity(true)
                                .dispenserPlaceable(true)
                        )
                        .textures(textures -> textures.all("minecraft:block/cyan_concrete"))
                        .sounds(sounds -> sounds
                                .mining("minecraft:block.stone.hit")
                                .breakSound("minecraft:block.stone.break")
                                .place("minecraft:block.stone.place")
                                .fall("minecraft:block.stone.fall")
                        )
                );
    }

    @Override
    public @NotNull BlockData createDefaultData(@NotNull BlockCreateContext context) {
        return context.createData();
    }
}
