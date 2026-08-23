package dev.auto.blockengine.listeners;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.event.BlockEngineGravityEvent;
import dev.auto.blockengine.api.event.BlockEngineModificationEvent;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.BlockContext;
import dev.auto.blockengine.runtime.BlockMover;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GravityListener implements Listener {
    private final @NotNull Map<UUID, PendingFall> fallingBlocks = new HashMap<>();

    public GravityListener() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    public void clear() {
        fallingBlocks.clear();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onGravity(BlockEngineGravityEvent event) {
        RuntimeBlockView customBlock = ChunkEngine.getBlock(location(event.startBlock()));
        if (customBlock == null || !customBlock.storedBlock().blockId().equals(event.blockId())) {
            return;
        }
        if (!BlockMover.canMove(event.startBlock(), customBlock, event.stopBlock(), BlockAdapter.MoveCause.GRAVITY)) {
            return;
        }

        ChunkEngine.Data data = ChunkEngine.data(event.startBlock().getChunk());
        data.removeBlock(event.startBlock().getX() & 15, event.startBlock().getY(), event.startBlock().getZ() & 15);
        event.startBlock().setType(Material.AIR, false);
        ChunkEngine.changed(event.startBlock());
        BlockIntegrityManager.getInstance().callEvent(
                BlockEngineModificationEvent.Action.REMOVE_CUSTOM_BLOCK,
                event.startBlock(),
                customBlock.storedBlock().blockId(),
                customBlock.storedBlock().stateId(),
                null,
                null
        );

        FallingBlock fallingBlock = event.startBlock().getWorld().spawnFallingBlock(
                event.startBlock().getLocation().add(0.5, 0.0, 0.5),
                customBlock.storedBlock().fallbackBlock().createBlockData()
        );
        fallingBlock.setDropItem(false);
        fallingBlock.setCancelDrop(true);
        fallingBlocks.put(fallingBlock.getUniqueId(), new PendingFall(
                event.startBlock().getLocation(),
                event.stopBlock().getLocation(),
                customBlock.storedBlock()
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        PendingFall pending = fallingBlocks.remove(event.getEntity().getUniqueId());
        if (pending == null) {
            return;
        }

        event.setCancelled(true);
        event.getEntity().remove();

        Block target = pending.stop().getBlock();
        BlockDefinition definition = BlockRegistry.getBlock(pending.storedBlock().blockId());
        if (definition == null || !BlockMover.canOccupy(target)) {
            drop(target, pending);
            return;
        }

        Block origin = pending.start().getBlock();
        BlockData data = pending.loadData(definition);
        BlockContext context = new BlockContext(definition.adapter(), data, origin, null);
        if (!definition.adapter().canMove(context, origin, target, BlockAdapter.MoveCause.GRAVITY)) {
            drop(target, pending);
            return;
        }

        definition.adapter().onMove(context, origin, target, BlockAdapter.MoveCause.GRAVITY);
        byte[] payload = definition.adapter().save(context.data());
        ChunkEngine.StoredBlock moved = BlockMover.movedBlock(
                origin,
                pending.storedBlock(),
                target,
                context.data(),
                definition,
                payload
        );
        ChunkEngine.data(target.getChunk()).setBlock(moved);
        target.setType(Main.getBackingBlock(), false);
        ChunkEngine.changed(target);
        BlockIntegrityManager.getInstance().callEvent(
                BlockEngineModificationEvent.Action.SET_CUSTOM_BLOCK,
                target,
                null,
                null,
                moved.blockId(),
                moved.stateId()
        );
    }

    private void drop(@NotNull Block target, @NotNull PendingFall pending) {
        BlockDefinition definition = BlockRegistry.getBlock(pending.storedBlock().blockId());
        if (definition == null || !pending.storedBlock().dropsItem()) {
            return;
        }
        target.getWorld().dropItemNaturally(target.getLocation().add(0.5, 0.5, 0.5),
                ItemManager.create(definition, pending.storedBlock().stateId()));
    }

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private record PendingFall(
            @NotNull Location start,
            @NotNull Location stop,
            @NotNull ChunkEngine.StoredBlock storedBlock
    ) {
        private @NotNull BlockData loadData(@NotNull BlockDefinition definition) {
            @Nullable BlockData loaded = storedBlock.loadData(definition);
            return loaded == null ? ChunkEngine.SimpleBlockData.copyOf(storedBlock.data()) : loaded;
        }
    }
}
