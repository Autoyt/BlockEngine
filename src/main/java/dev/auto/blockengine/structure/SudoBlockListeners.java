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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.AsyncStructureGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
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
            BlockEngineChat.error(player, "You do not have permission to place BlockEngine placeholder blocks.");
            return;
        }
        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (definition == null) {
            event.setCancelled(true);
            BlockEngineChat.error(player, "Unknown placeholder block '" + blockId + "'.");
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

        Player player = event.getPlayer();
        if (manager.isSudoMarker(clicked) && player.isSneaking()) {
            SudoBlockManager.PreviewToggle result = manager.togglePreview(clicked);
            if (result == SudoBlockManager.PreviewToggle.VISIBLE) {
                BlockEngineChat.success(player, "Placeholder block preview shown.");
            } else if (result == SudoBlockManager.PreviewToggle.HIDDEN) {
                BlockEngineChat.success(player, "Placeholder block preview hidden.");
            }
            manager.playWandFeedback(player, clicked, result != SudoBlockManager.PreviewToggle.NOT_A_MARKER);
            return;
        }

        if (manager.isSudoMarker(clicked)) {
            if (manager.convertMarkerToCustomBlock(clicked)) {
                BlockEngineChat.success(player, "Converted placeholder block to a full block.");
                manager.playWandFeedback(player, clicked, false);
            } else {
                BlockEngineChat.error(player, "That placeholder block no longer points at a registered full block.");
                manager.playWandFeedback(player, clicked, false);
            }
            return;
        }

        RuntimeBlockView customBlock = customBlock(clicked);
        if (customBlock == null) {
            BlockEngineChat.error(player, "That is not a BlockEngine full block or placeholder block.");
            return;
        }
        if (manager.convertCustomBlockToMarker(clicked, customBlock)) {
            BlockEngineChat.success(player, "Converted full block to a placeholder block.");
            manager.playWandFeedback(player, clicked, true);
        } else {
            BlockEngineChat.error(player, "Could not convert that full block to a placeholder block.");
            manager.playWandFeedback(player, clicked, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMarkerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || !manager.isSudoMarker(clicked)) {
            return;
        }

        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(@NotNull BlockBreakEvent event) {
        manager.removePreviewIfMarker(event.getBlock());
    }

    @EventHandler
    public void onStructureGenerate(@NotNull AsyncStructureGenerateEvent event) {
        event.setBlockTransformer(SudoBlockManager.transformerKey(), manager.structureBlockTransformer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureBlockInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.STRUCTURE_BLOCK || !allowed(event.getPlayer())) {
            return;
        }
        scheduleStructureBlockScans(clicked);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureBlockPower(@NotNull BlockRedstoneEvent event) {
        if (event.getNewCurrent() <= 0 || event.getBlock().getType() != Material.STRUCTURE_BLOCK) {
            return;
        }
        scheduleStructureBlockScans(event.getBlock());
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

    private void scheduleStructureBlockScans(@NotNull Block structureBlock) {
        long[] delays = {1L, 10L, 40L, 100L, 200L};
        for (long delay : delays) {
            Main.getInstance().getServer().getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (structureBlock.getType() != Material.STRUCTURE_BLOCK) {
                    return;
                }
                BoundingBox area = manager.structureLoadArea(structureBlock);
                if (area != null) {
                    manager.convertLoadedMarkers(structureBlock.getWorld(), area);
                }
            }, delay);
        }
    }
}
