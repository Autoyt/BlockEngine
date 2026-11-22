package dev.auto.blockengine.world;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.event.BlockEngineModificationEvent;
import dev.auto.blockengine.api.world.BlockEngineManagedWorld;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.placement.PlacementManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.BlockRemover;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.visibility.VisibilityManager;
import org.bukkit.Chunk;
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
        return PlacementManager.getInstance().place(block, definition, player, placedAgainst, stateId);
    }

    @Override
    public boolean removeBlock(int x, int y, int z, boolean drop) {
        Main.serverThread();
        Block block = existingBlock(x, y, z);
        return block != null && BlockRemover.remove(block, drop);
    }

    @Override
    public boolean clearBlock(int x, int y, int z, @NotNull Material replacement, boolean applyPhysics) {
        Main.serverThread();
        if (!Objects.requireNonNull(replacement, "replacement").isBlock()) {
            return false;
        }

        Block block = existingBlock(x, y, z);
        if (block == null) {
            return false;
        }

        RuntimeBlockView existing = ChunkEngine.getBlock(location(block));
        if (existing == null) {
            return false;
        }
        BlockIntegrityManager.getInstance().clearRecord(
                block,
                existing,
                BlockEngineModificationEvent.Action.CLEAR_CUSTOM_BLOCK,
                () -> {
                    block.setType(replacement, applyPhysics);
                    ChunkEngine.changed(block);
                }
        );
        return true;
    }

    @Override
    public boolean reconcileBlock(int x, int y, int z) {
        Main.serverThread();
        Block block = existingBlock(x, y, z);
        return block != null && BlockIntegrityManager.getInstance().reconcileBlock(
                block,
                BlockEngineModificationEvent.Action.RECONCILE_STALE_BLOCK
        );
    }

    private @Nullable Block existingBlock(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return null;
        }
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) && !world.isChunkGenerated(chunkX, chunkZ)) {
            return null;
        }

        // CraftWorld.getBlockAt creates a handle without loading its persisted chunk.
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        ChunkEngine.load(chunk, VisibilityManager.getInstance().config());
        return chunk.getBlock(x & 15, y, z & 15);
    }

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
