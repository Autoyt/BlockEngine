package dev.auto.turtle.listeners;

import dev.auto.turtle.Main;
import dev.auto.turtle.items.TurtleItemManager;
import dev.auto.turtle.mining.MiningManager;
import dev.auto.turtle.placement.TurtlePlacementService;
import dev.auto.turtle.registry.BlockRegistry;
import dev.auto.turtle.registry.NamespaceRegistry;
import dev.auto.turtle.resourcepack.ResourcePackManager;
import dev.auto.turtle.runtime.LoadedTurtleChunk;
import dev.auto.turtle.runtime.RuntimeBlockView;
import dev.auto.turtle.runtime.TurtleBlockContext;
import dev.auto.turtle.runtime.TurtleBlockDataService;
import dev.auto.turtle.runtime.TurtleBlockRemover;
import dev.auto.turtle.runtime.TurtleChunkRuntime;
import dev.auto.turtle.types.BlockDefinition;
import dev.auto.turtle.types.BlockLocationKey;
import dev.auto.turtle.visibility.VisibilityService;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GameListener implements Listener {
    public GameListener() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        VisibilityService.handleMove(event);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        VisibilityService.forceRecalculate(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ResourcePackManager.send(event.getPlayer());
        VisibilityService.forceRecalculate(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        MiningManager.stop(event.getPlayer());
        VisibilityService.cleanup(event.getPlayer());
    }

    @EventHandler
    public void onStartBreaking(BlockDamageEvent event) {
        final Player player = event.getPlayer();
        RuntimeBlockView block = TurtleChunkRuntime.getBlock(new BlockLocationKey(
                event.getBlock().getWorld().getUID(),
                event.getBlock().getX(),
                event.getBlock().getY(),
                event.getBlock().getZ()
        ));
        if (block == null) {
            return;
        }

        event.setCancelled(true);
        if (player.getGameMode() == GameMode.CREATIVE) {
            MiningManager.breakNow(player, event.getBlock(), block);
            return;
        }
        MiningManager.start(player, event.getBlock(), block);
    }

    @EventHandler
    public void onStopBreaking(BlockDamageAbortEvent event) {
        MiningManager.abort(event.getPlayer());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        RuntimeBlockView block = TurtleChunkRuntime.getBlock(new BlockLocationKey(
                event.getBlock().getWorld().getUID(),
                event.getBlock().getX(),
                event.getBlock().getY(),
                event.getBlock().getZ()
        ));
        if (block != null) {
            event.setCancelled(true);
            if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
                MiningManager.breakNow(event.getPlayer(), event.getBlock(), block);
            }
        }
    }

    @EventHandler
    public void onFlow(BlockFromToEvent event) {
        RuntimeBlockView block = block(event.getToBlock());
        if (block == null) {
            return;
        }

        if (block.storedBlock().unbreakable() || !block.storedBlock().washable()) {
            event.setCancelled(true);
            return;
        }

        TurtleBlockRemover.remove(event.getToBlock(), block, block.storedBlock().dropsItem());
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        explode(event.getLocation(), event.blockList());
    }

    @EventHandler
    public void onExplode(BlockExplodeEvent event) {
        explode(event.getBlock().getLocation(), event.blockList());
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        TurtlePlacementService.place(event);
    }

    @EventHandler
    public void onPickBlock(PlayerPickBlockEvent event) {
        RuntimeBlockView customBlock = block(event.getBlock());
        if (customBlock == null) {
            return;
        }

        event.setCancelled(true);
        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return;
        }

        ItemStack stack = TurtleItemManager.create(definition, customBlock.storedBlock().stateId());
        pick(event.getPlayer(), stack, event.getTargetSlot());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }

        if (placeHeld(event)) {
            return;
        }

        RuntimeBlockView block = block(event.getClickedBlock());
        if (block == null) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
                MiningManager.breakNow(event.getPlayer(), event.getClickedBlock(), block);
                return;
            }
            if (!MiningManager.active(event.getPlayer(), event.getClickedBlock())) {
                MiningManager.start(event.getPlayer(), event.getClickedBlock(), block);
            }
            return;
        }

        TurtleBlockContext context = TurtleBlockDataService.context(event.getClickedBlock(), block, event.getPlayer());
        if (context == null) {
            return;
        }
        if (context.adapter().onInteract(context, event.getPlayer())) {
            event.setCancelled(true);
            TurtleBlockDataService.save(event.getClickedBlock(), context);
        }
    }

    private void explode(Location origin, List<Block> vanillaBlocks) {
        double radius = radius(origin, vanillaBlocks);
        List<RuntimeBlockView> customBlocks = customBlocks(origin, radius);

        Iterator<Block> iterator = vanillaBlocks.iterator();
        while (iterator.hasNext()) {
            if (block(iterator.next()) != null) {
                iterator.remove();
            }
        }

        for (RuntimeBlockView customBlock : customBlocks) {
            if (customBlock.storedBlock().unbreakable()) {
                continue;
            }
            if (!breaks(origin, radius, customBlock)) {
                continue;
            }

            World world = Bukkit.getWorld(customBlock.location().worldId());
            if (world == null) {
                continue;
            }
            Block block = world.getBlockAt(
                    customBlock.location().x(),
                    customBlock.location().y(),
                    customBlock.location().z()
            );
            TurtleBlockRemover.remove(block, customBlock, customBlock.storedBlock().dropsItem());
        }
    }

    private double radius(Location origin, List<Block> blocks) {
        double radius = 4.0;
        for (Block block : blocks) {
            radius = Math.max(radius, block.getLocation().add(0.5, 0.5, 0.5).distance(origin));
        }
        return radius + 1.0;
    }

    private List<RuntimeBlockView> customBlocks(Location origin, double radius) {
        List<RuntimeBlockView> result = new ArrayList<>();
        World world = origin.getWorld();
        if (world == null) {
            return result;
        }

        double maxDistanceSquared = radius * radius;
        for (LoadedTurtleChunk chunk : TurtleChunkRuntime.chunks()) {
            if (!chunk.key().worldId().equals(world.getUID())) {
                continue;
            }
            for (RuntimeBlockView block : chunk.blocks()) {
                double distanceSquared = origin.distanceSquared(new Location(
                        world,
                        block.location().x() + 0.5,
                        block.location().y() + 0.5,
                        block.location().z() + 0.5
                ));
                if (distanceSquared <= maxDistanceSquared) {
                    result.add(block);
                }
            }
        }
        return result;
    }

    private boolean breaks(Location origin, double radius, RuntimeBlockView block) {
        double distance = origin.distance(new Location(
                origin.getWorld(),
                block.location().x() + 0.5,
                block.location().y() + 0.5,
                block.location().z() + 0.5
        ));
        double falloff = Math.max(0.0, 1.0 - (distance / radius));
        double strength = falloff * 6.0;
        return strength >= Math.max(0.05f, block.storedBlock().hardness());
    }

    private RuntimeBlockView block(Block block) {
        return TurtleChunkRuntime.getBlock(new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
    }

    private boolean placeHeld(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return false;
        }

        ItemStack item = event.getItem();
        String blockId = TurtleItemManager.blockId(item);
        if (blockId == null || !TurtleItemManager.placeable(item)) {
            return false;
        }

        Player player = event.getPlayer();
        String namespace = TurtleItemManager.namespace(item);
        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (namespace == null || !NamespaceRegistry.loaded(namespace) || definition == null) {
            event.setCancelled(true);
            return true;
        }

        Block target = target(event);
        if (!target.isReplaceable()) {
            event.setCancelled(true);
            return true;
        }

        event.setCancelled(true);
        if (!TurtlePlacementService.place(target, definition, player, event.getBlockFace().getOppositeFace(), TurtleItemManager.stateId(item))) {
            return true;
        }

        if (player.getGameMode() != GameMode.CREATIVE && item != null) {
            item.subtract();
        }
        return true;
    }

    private Block target(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked.isReplaceable()) {
            return clicked;
        }
        return clicked.getRelative(event.getBlockFace());
    }

    private void pick(Player player, ItemStack stack, int targetSlot) {
        PlayerInventory inventory = player.getInventory();
        int slot = targetSlot >= 0 && targetSlot <= 8 ? targetSlot : inventory.getHeldItemSlot();
        inventory.setItem(slot, stack);
        inventory.setHeldItemSlot(slot);
        player.updateInventory();
    }
}
