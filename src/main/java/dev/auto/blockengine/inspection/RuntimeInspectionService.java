package dev.auto.blockengine.inspection;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.inspection.BlockInspection;
import dev.auto.blockengine.api.inspection.BlockInspectionService;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.structure.SudoBlockManager;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Captures stored runtime facts without calling adapter load methods or mutating blocks. */
public final class RuntimeInspectionService implements BlockInspectionService {
    @Override
    public @NotNull BlockInspection inspect(@NotNull Block block) {
        Main.serverThread();
        Objects.requireNonNull(block, "block");
        var world = block.getWorld();
        if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
            throw new IllegalArgumentException("Inspection requires a loaded chunk");
        }

        var location = new BlockLocationKey(world.getUID(), block.getX(), block.getY(), block.getZ());
        var runtime = ChunkEngine.getBlock(location);
        String material = block.getType().getKey().toString();
        if (runtime != null) {
            var stored = runtime.storedBlock();
            return new BlockInspection(world.getUID(), world.getName(), block.getX(), block.getY(), block.getZ(),
                    BlockInspection.Kind.CUSTOM, stored.blockId(), stored.stateId(), material,
                    stored.displays().size(), runtime.exposed(), stored.unbreakable(),
                    stored.hardness(), stored.miningSpeed(), stored.payloadBytes());
        }

        var markers = SudoBlockManager.getInstance();
        String markerId = markers.markerBlockId(block);
        if (markerId != null) {
            String state = markers.markerStateId(block);
            return new BlockInspection(world.getUID(), world.getName(), block.getX(), block.getY(), block.getZ(),
                    BlockInspection.Kind.PLACEHOLDER, markerId, state == null ? "<unspecified>" : state,
                    material, 0, false, false, 0, 0, 0);
        }

        return new BlockInspection(world.getUID(), world.getName(), block.getX(), block.getY(), block.getZ(),
                BlockInspection.Kind.VANILLA, material, "", material, 0, false, false, 0, 0, 0);
    }

    @Override
    public boolean isWand(@Nullable ItemStack item) {
        Main.serverThread();
        return ItemManager.wand(item);
    }
}
