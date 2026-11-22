package dev.auto.blockengine.runtime;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.entity.ManagedDisplayManager;
import dev.auto.blockengine.api.event.BlockEngineBlockRemovedEvent;
import dev.auto.blockengine.api.event.BlockEngineModificationEvent;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BlockRemover {
    private BlockRemover() {
    }

    public static boolean remove(@NotNull Block block, boolean drop) {
        RuntimeBlockView customBlock = ChunkEngine.getBlock(new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
        if (customBlock == null) {
            return false;
        }
        return remove(block, customBlock, drop);
    }

    public static boolean remove(@NotNull Block block, @NotNull RuntimeBlockView customBlock, boolean drop) {
        return remove(block, customBlock, drop, Material.AIR);
    }

    public static boolean remove(
            @NotNull Block block,
            @NotNull RuntimeBlockView customBlock,
            boolean drop,
            @NotNull Material replacement
    ) {
        return remove(block, customBlock, drop, replacement, replacement != Material.AIR);
    }

    public static boolean remove(
            @NotNull Block block,
            @NotNull RuntimeBlockView customBlock,
            boolean drop,
            @NotNull Material replacement,
            boolean applyPhysics
    ) {
        return remove(block, customBlock, drop, replacement, applyPhysics, BlockEngineBlockRemovedEvent.Reason.PLUGIN_REQUEST);
    }

    public static boolean remove(
            @NotNull Block block,
            @NotNull RuntimeBlockView customBlock,
            boolean drop,
            @NotNull Material replacement,
            boolean applyPhysics,
            @NotNull BlockEngineBlockRemovedEvent.Reason reason
    ) {
        List<ItemStack> drops = List.of();
        if (drop && customBlock.storedBlock().dropsItem() && !customBlock.storedBlock().unbreakable()) {
            BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
            if (definition != null) {
                String stateId = customBlock.storedBlock().stateId();
                drops = List.of(definition.apiDefinition().states().containsKey(stateId)
                        ? ItemManager.create(definition, stateId)
                        : ItemManager.create(definition));
            }
        }

        // Non-player removals retain the existing single self-item behavior.
        return removeWithDrops(block, customBlock, drops, replacement, applyPhysics, reason).removed();
    }

    public static @NotNull RemovalResult removeWithDrops(
            @NotNull Block block,
            @NotNull RuntimeBlockView customBlock,
            @NotNull List<ItemStack> drops,
            @NotNull Material replacement,
            boolean applyPhysics,
            @NotNull BlockEngineBlockRemovedEvent.Reason reason
    ) {
        return removeWithDrops(block, customBlock, drops, replacement, applyPhysics, reason, block);
    }

    static @NotNull RemovalResult removeWithDrops(
            @NotNull Block block,
            @NotNull RuntimeBlockView customBlock,
            @NotNull List<ItemStack> drops,
            @NotNull Material replacement,
            boolean applyPhysics,
            @NotNull BlockEngineBlockRemovedEvent.Reason reason,
            @NotNull Block dropAt
    ) {
        if (customBlock.storedBlock().unbreakable()) {
            return new RemovalResult(false, List.of());
        }

        List<ItemStack> prepared = customBlock.storedBlock().dropsItem()
                ? BlockDropResolver.prepareItems(drops)
                : List.of();

        // A callback may have removed or replaced this block, even with the same id/state.
        BlockLocationKey location = new BlockLocationKey(
                block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()
        );
        RuntimeBlockView current = ChunkEngine.getBlock(location);
        if (!location.equals(customBlock.location()) || current == null
                || current.storedBlock() != customBlock.storedBlock()) {
            return new RemovalResult(false, List.of());
        }
        if (block.getType() != Main.getBackingBlock()) {
            BlockIntegrityManager.getInstance().reconcileBlock(block, BlockEngineModificationEvent.Action.RECONCILE_STALE_BLOCK);
            return new RemovalResult(false, List.of());
        }

        ChunkEngine.Data data = ChunkEngine.data(block.getChunk());
        data.removeBlock(block.getX() & 15, block.getY(), block.getZ() & 15);
        ManagedDisplayManager.getInstance().removeBlockAttached(location);

        block.setType(replacement, applyPhysics);
        ChunkEngine.changed(block);
        sound(block, customBlock);

        List<ItemStack> spawned = spawn(dropAt, prepared);
        BlockEngineEvents.call(new BlockEngineBlockRemovedEvent(
                block,
                customBlock.storedBlock().blockId(),
                customBlock.storedBlock().stateId(),
                reason,
                spawned
        ));
        BlockIntegrityManager.getInstance().callEvent(
                BlockEngineModificationEvent.Action.REMOVE_CUSTOM_BLOCK,
                block,
                customBlock.storedBlock().blockId(),
                customBlock.storedBlock().stateId(),
                null,
                null
        );
        return new RemovalResult(true, spawned);
    }

    private static @NotNull List<ItemStack> spawn(@NotNull Block block, @NotNull List<ItemStack> stacks) {
        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        List<ItemStack> spawned = new ArrayList<>();
        for (ItemStack stack : stacks) {
            Item entity = block.getWorld().dropItemNaturally(location, stack);
            if (entity.isValid()) {
                spawned.add(entity.getItemStack().clone());
            }
        }
        return List.copyOf(spawned);
    }

    public record RemovalResult(boolean removed, @NotNull List<ItemStack> drops) {
        public RemovalResult {
            drops = drops.stream().map(ItemStack::clone).toList();
        }

        @Override
        public @NotNull List<ItemStack> drops() {
            return drops.stream().map(ItemStack::clone).toList();
        }
    }

    private static void sound(@NotNull Block block, @NotNull RuntimeBlockView customBlock) {
        String sound = breakSound(customBlock);
        if (sound == null) {
            return;
        }

        World world = block.getWorld();
        world.playSound(
                block.getLocation().add(0.5, 0.5, 0.5),
                sound,
                SoundCategory.BLOCKS,
                0.8f,
                1.0f
        );
    }

    private static @Nullable String breakSound(@NotNull RuntimeBlockView customBlock) {
        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return null;
        }

        try {
            String sound = definition.apiDefinition()
                    .state(customBlock.storedBlock().stateId())
                    .sounds()
                    .breakSound();
            return sound == null || NamespacedKey.fromString(sound) == null ? null : sound;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
