package dev.auto.blockengine.datapack;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockCreateContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import dev.auto.blockengine.api.drops.BlockDrop;
import dev.auto.blockengine.api.drops.BlockDropContext;
import dev.auto.blockengine.api.drops.DropTable;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
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

    @Override
    public @NotNull List<BlockDrop> getDrops(@NotNull BlockDropContext context) {
        BlockPackBlock.State state = block.states().get(context.stateId());
        if (state == null) {
            throw new IllegalArgumentException("Unknown drop state '" + context.stateId()
                    + "' for " + context.blockId());
        }
        if (state.drops() == null) {
            return BlockAdapter.super.getDrops(context);
        }

        // Use the Java table evaluator so JSON and code share chance, count,
        // Fortune, and Silk Touch behavior. Factories resolve only when selected.
        DropTable.Builder table = DropTable.builder();
        for (int index = 0; index < state.drops().size(); index++) {
            BlockPackBlock.Drop drop = state.drops().get(index);
            String path = context.blockId() + ": states." + context.stateId() + ".drops[" + index + "].item";
            table.item(() -> dropItem(drop.item(), context, path), entry -> entry
                    .count(drop.count().min(), drop.count().max())
                    .chance(drop.chance())
                    .fortune(drop.fortune()));
        }
        if (state.silkTouchSelf()) {
            table.silkTouchSelf();
        }

        return table.build().roll(context);
    }

    private static @NotNull ItemStack dropItem(
            @NotNull BlockPackBlock.DropItem item,
            @NotNull BlockDropContext context,
            @NotNull String path
    ) {
        if (item.self()) {
            return context.selfDrop().item();
        }
        if (item.material() != null) {
            return new ItemStack(item.material());
        }

        // Resolve against the current registry, allowing forward references and
        // avoiding stale definitions after a pack reload.
        var definition = BlockRegistry.getBlock(item.blockId());
        if (definition == null) {
            throw new IllegalArgumentException(path + ".block: Unknown BlockEngine block '" + item.blockId() + "'");
        }
        String stateId = item.stateId() == null ? definition.apiDefinition().defaultState() : item.stateId();
        if (!definition.apiDefinition().states().containsKey(stateId)) {
            throw new IllegalArgumentException(path + ".state: Unknown state '" + stateId
                    + "' for BlockEngine block '" + item.blockId() + "'");
        }

        return ItemManager.create(definition, stateId);
    }
}
