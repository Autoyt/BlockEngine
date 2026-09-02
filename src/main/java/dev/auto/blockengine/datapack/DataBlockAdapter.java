package dev.auto.blockengine.datapack;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockCreateContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class DataBlockAdapter implements BlockAdapter {
    private final @NotNull BlockPackBlock block;

    public DataBlockAdapter(@NotNull BlockPackBlock block) {
        this.block = Objects.requireNonNull(block, "block");
    }

    @Override
    public @NotNull String name() {
        return block.name();
    }

    @Override
    public void define(@NotNull BlockDefinition.Builder builder) {
        builder
                .setDefaultBlock(block.vanillaBlock())
                .catalog(block.catalog())
                .creativeMenu(block.creativeMenu())
                .placement(block.placement())
                .defaultState(block.defaultState())
                .item(item -> item
                        .material(block.item().material())
                        .name(block.item().name())
                        .lore(block.item().lore())
                        .glint(block.item().glint())
                        .placeable(block.item().placeable()));

        for (var entry : block.states().entrySet()) {
            BlockPackBlock.State state = entry.getValue();
            builder.state(entry.getKey(), stateBuilder -> stateBuilder
                    .hardness(state.hardness())
                    .miningSpeed(state.miningSpeed())
                    .miningProfile(state.miningProfile())
                    .preferredTools(state.preferredTools())
                    .requirePreferredToolForDrops(state.requirePreferredToolForDrops())
                    .requireSilkTouchForDrops(state.requireSilkTouchForDrops())
                    .gravity(state.movement().gravity())
                    .dispenserPlaceable(state.movement().dispenserPlaceable())
                    .gravityBreaksOnPartialBlock(state.movement().gravityBreaksOnPartialBlock())
                    .unbreakable(state.unbreakable())
                    .dropsItem(state.dropsItem())
                    .dropInCreative(state.dropInCreative())
                    .textures(textures -> textures
                            .all(state.textures().all())
                            .side(state.textures().side())
                            .front(state.textures().front())
                            .top(state.textures().top())
                            .bottom(state.textures().bottom())
                            .north(state.textures().north())
                            .south(state.textures().south())
                            .east(state.textures().east())
                            .west(state.textures().west()))
                    .sounds(sounds -> sounds
                            .place(state.sounds().place())
                            .breakSound(state.sounds().breakSound())
                            .mining(state.sounds().mining())
                            .step(state.sounds().step())
                            .hit(state.sounds().hit())
                            .fall(state.sounds().fall())));
        }
    }

    @Override
    public @NotNull BlockData createDefaultData(@NotNull BlockCreateContext context) {
        return context.createData();
    }
}
