package dev.auto.blockengine.defaultadapters;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockContext;
import dev.auto.blockengine.api.blocks.BlockCreateContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import dev.auto.blockengine.chat.BlockEngineChat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class DemoBreakBlockAdapter implements BlockAdapter {
    @Override
    public @NotNull String name() {
        return "demo_break";
    }

    @Override
    public void define(@NotNull BlockDefinition.Builder builder) {
        builder
                .defaultState("default")
                .item(item -> item
                        .name("<#ff8c1a>Demo: On Break")
                        .lore("<gray>Breaking this block gives a bonus emerald.")
                )
                .state("default", state -> state
                        .hardness(0.8f)
                        .miningSpeed(1.0f)
                        .dropsItem(true)
                        .textures(textures -> textures.all("blockengine_test:block/demo_break"))
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

    @Override
    public boolean onBreak(@NotNull BlockContext context) {
        Player player = context.player();
        if (player != null) {
            player.getInventory().addItem(new ItemStack(Material.EMERALD));
            BlockEngineChat.success(player, "Demo block gave you a bonus emerald.");
        }
        return true;
    }
}
