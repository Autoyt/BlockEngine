package dev.auto.blockengine.runtime;

import dev.auto.blockengine.items.BlockEngineItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BlockEngineBlockRemover {
    private BlockEngineBlockRemover() {
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
        if (customBlock.storedBlock().unbreakable()) {
            return false;
        }

        if (drop && customBlock.storedBlock().dropsItem()) {
            drop(block, customBlock);
        }
        sound(block, customBlock);

        ChunkEngine.Data data = BlockEngineMutationBatcher.data(block.getChunk());
        data.removeBlock(block.getX() & 15, block.getY(), block.getZ() & 15);

        block.setType(replacement, applyPhysics);
        BlockEngineMutationBatcher.changed(block);
        return true;
    }

    private static void drop(@NotNull Block block, @NotNull RuntimeBlockView customBlock) {
        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return;
        }

        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(location, BlockEngineItemManager.create(definition));
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
