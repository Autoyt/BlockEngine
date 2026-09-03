package dev.auto.blockengine.structure;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.chat.BlockEngineChat;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.AsyncStructureGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class SudoBlockListeners implements Listener {
    private final @NotNull SudoBlockManager manager = SudoBlockManager.getInstance();

    public SudoBlockListeners() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(@NotNull BlockPlaceEvent event) {
        String blockId = ItemManager.sudoBlockId(event.getItemInHand());
        if (blockId == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!allowed(player)) {
            event.setCancelled(true);
            BlockEngineChat.error(player, "You do not have permission to place BlockEngine sudo blocks.");
            return;
        }
        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (definition == null) {
            event.setCancelled(true);
            BlockEngineChat.error(player, "Unknown sudo block '" + blockId + "'.");
            return;
        }

        manager.placeMarker(event.getBlockPlaced(), definition.id(), ItemManager.sudoStateId(event.getItemInHand()));
        player.swingHand(event.getHand());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onWandInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !ItemManager.wand(event.getItem())) {
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        event.setCancelled(true);
        if (!allowed(event.getPlayer())) {
            BlockEngineChat.error(event.getPlayer(), "You do not have permission to use the Block Engine Wand.");
            return;
        }

        if (manager.isSudoMarker(clicked)) {
            manager.hidePreview(clicked);
            BlockEngineChat.success(event.getPlayer(), "Sudo block preview hidden.");
            return;
        }

        RuntimeBlockView customBlock = customBlock(clicked);
        if (customBlock == null) {
            return;
        }
        if (manager.convertCustomBlockToMarker(clicked, customBlock)) {
            BlockEngineChat.success(event.getPlayer(), "Converted custom block to a sudo structure marker.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(@NotNull BlockBreakEvent event) {
        manager.removePreviewIfMarker(event.getBlock());
    }

    @EventHandler
    public void onStructureGenerate(@NotNull AsyncStructureGenerateEvent event) {
        event.setBlockTransformer(SudoBlockManager.transformerKey(), (region, x, y, z, blockState, transformationState) -> {
            String blockId = manager.markerBlockId(blockState);
            if (blockId == null || BlockRegistry.getBlock(blockId) == null) {
                return blockState;
            }

            World world = region.getWorld();
            manager.recordStructureMarker(world, x, y, z, blockId, manager.markerStateId(blockState));
            BlockState barrier = blockState.copy();
            barrier.setType(Material.BARRIER);
            return barrier;
        });
    }

    public static boolean allowed(@NotNull Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission(SudoBlockManager.PERMISSION);
    }

    private static RuntimeBlockView customBlock(@NotNull Block block) {
        return ChunkEngine.getBlock(new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
    }

    public static void pick(@NotNull Player player, @NotNull ItemStack stack, int targetSlot) {
        int slot = targetSlot >= 0 && targetSlot <= 8 ? targetSlot : player.getInventory().getHeldItemSlot();
        player.getInventory().setItem(slot, stack);
        player.getInventory().setHeldItemSlot(slot);
        player.updateInventory();
    }
}
