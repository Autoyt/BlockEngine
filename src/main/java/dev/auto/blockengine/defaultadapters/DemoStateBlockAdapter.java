package dev.auto.blockengine.defaultadapters;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockContext;
import dev.auto.blockengine.api.blocks.BlockCreateContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class DemoStateBlockAdapter implements BlockAdapter {
    private static final String[] STATES = {"red", "green", "purple"};

    @Override
    public @NotNull String name() {
        return "demo_state";
    }

    @Override
    public void define(@NotNull BlockDefinition.Builder builder) {
        builder
                .setDefaultBlock(Material.PURPLE_CONCRETE)
                .defaultState("red")
                .item(item -> item
                        .name("<#b84dff>Demo: State Cycle")
                        .lore("<gray>Right-click to cycle red, green, and purple states.")
                )
                .state("red", state -> state
                        .hardness(1.0f)
                        .miningSpeed(1.0f)
                        .dropsItem(true)
                        .textures(textures -> textures.all("BlockEngine_test:block/demo_state_red"))
                        .sounds(sounds -> sounds.mining("minecraft:block.stone.hit"))
                )
                .state("green", state -> state
                        .hardness(1.0f)
                        .miningSpeed(1.0f)
                        .dropsItem(true)
                        .textures(textures -> textures.all("BlockEngine_test:block/demo_state_green"))
                        .sounds(sounds -> sounds.mining("minecraft:block.stone.hit"))
                )
                .state("purple", state -> state
                        .hardness(1.0f)
                        .miningSpeed(1.0f)
                        .dropsItem(true)
                        .textures(textures -> textures.all("BlockEngine_test:block/demo_state_purple"))
                        .sounds(sounds -> sounds.mining("minecraft:block.stone.hit"))
                );
    }

    @Override
    public @NotNull BlockData createDefaultData(@NotNull BlockCreateContext context) {
        BlockData data = context.createData();
        data.stateId("red");
        return data;
    }

    @Override
    public boolean onInteract(@NotNull BlockContext context, @NotNull Player player) {
        int index = index(context.stateId());
        String next = STATES[(index + 1) % STATES.length];
        context.setState(next);
        player.sendMessage("BlockEngine demo: state changed to " + next + ".");
        return true;
    }

    private static int index(@NotNull String stateId) {
        for (int i = 0; i < STATES.length; i++) {
            if (STATES[i].equals(stateId)) {
                return i;
            }
        }
        return 0;
    }
}
