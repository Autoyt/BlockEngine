package dev.auto.blockengine.world;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.event.BlockEngineModificationEvent;
import dev.auto.blockengine.api.world.BlockEngineManagedWorld;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.entity.ManagedDisplayManager;
import dev.auto.blockengine.placement.PlacementManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.BlockRemover;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ManagedWorld implements BlockEngineManagedWorld {
    private final @NotNull World world;

    public ManagedWorld(@NotNull World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public @NotNull World bukkitWorld() {
        return world;
    }

    @Override
    public boolean setBlock(
            int x,
            int y,
            int z,
            @NotNull String blockId,
            @Nullable String stateId,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst
    ) {
        Main.serverThread();
        dev.auto.blockengine.types.BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (definition == null) {
            return false;
        }

        Block block = world.getBlockAt(x, y, z);
        RuntimeBlockView existing = ChunkEngine.getBlock(location(block));
        if (existing != null) {
            ManagedDisplayManager.getInstance().removeBlockAttached(location(block));
        }
        return PlacementManager.getInstance().place(block, definition, player, placedAgainst, stateId);
    }

    @Override
    public boolean setBlock(
            int x,
            int y,
            int z,
            @NotNull BlockAdapter adapter,
            @Nullable String stateId,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst
    ) {
        Main.serverThread();
        dev.auto.blockengine.types.BlockDefinition definition = BlockRegistry.getBlock(adapter);
        if (definition == null) {
            return false;
        }

        Block block = world.getBlockAt(x, y, z);
        RuntimeBlockView existing = ChunkEngine.getBlock(location(block));
        if (existing != null) {
            ManagedDisplayManager.getInstance().removeBlockAttached(location(block));
        }
        return PlacementManager.getInstance().place(block, definition, player, placedAgainst, stateId);
    }

    @Override
    public boolean removeBlock(int x, int y, int z, boolean drop) {
        Main.serverThread();
        Block block = world.getBlockAt(x, y, z);
        return BlockRemover.remove(block, drop);
    }

    @Override
    public boolean clearBlock(int x, int y, int z, @NotNull Material replacement, boolean applyPhysics) {
        Main.serverThread();
        Block block = world.getBlockAt(x, y, z);
        RuntimeBlockView existing = ChunkEngine.getBlock(location(block));
        if (existing == null) {
            return false;
        }
        BlockIntegrityManager.getInstance().clearRecord(
                block,
                existing,
                BlockEngineModificationEvent.Action.CLEAR_CUSTOM_BLOCK
        );
        block.setType(replacement, applyPhysics);
        ChunkEngine.changed(block);
        return true;
    }

    @Override
    public boolean reconcileBlock(int x, int y, int z) {
        Main.serverThread();
        return BlockIntegrityManager.getInstance().reconcileBlock(
                world.getBlockAt(x, y, z),
                BlockEngineModificationEvent.Action.RECONCILE_STALE_BLOCK
        );
    }

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
