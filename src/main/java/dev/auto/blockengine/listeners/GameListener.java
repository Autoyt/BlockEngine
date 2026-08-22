package dev.auto.blockengine.listeners;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.event.BlockEngineBlockBreakEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockBrokenEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockRemovedEvent;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.placement.PlacementManager;
import dev.auto.blockengine.placement.PlacementVerificationEngine;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.runtime.BlockContext;
import dev.auto.blockengine.runtime.BlockDataManager;
import dev.auto.blockengine.runtime.BlockRemover;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.visibility.VisibilityManager;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameListener implements Listener {
    private final Map<UUID, Integer> lastPlacementTicks = new HashMap<>();

    public GameListener() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        ChunkEngine.load(chunk, VisibilityManager.getInstance().config());
        BlockIntegrityManager.getInstance().enqueue(chunk);
        VisibilityManager.getInstance().refreshPlayersNear(ChunkEngine.Key.from(chunk));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        ChunkEngine.flushNow();
        ChunkEngine.Key key = ChunkEngine.Key.from(event.getChunk());
        ChunkEngine.unload(event.getChunk());
        VisibilityManager.getInstance().removeChunkDisplays(key);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        VisibilityManager.getInstance().handleMove(event);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        VisibilityManager.getInstance().forceRecalculate(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ResourcePackManager.getInstance().send(event.getPlayer());
        VisibilityManager.getInstance().forceRecalculate(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ChunkEngine.flushNow();
        lastPlacementTicks.remove(event.getPlayer().getUniqueId());
        VisibilityManager.getInstance().cleanup(event.getPlayer());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (BlockIntegrityManager.getInstance().verifyInteraction(event.getBlock())) {
            return;
        }
        RuntimeBlockView block = block(event.getBlock());
        if (block != null) {
            event.setCancelled(true);
            BlockContext context = BlockDataManager.getInstance().context(event.getBlock(), block, event.getPlayer());
            if (context != null && BlockEngineEvents.callCancellable(new BlockEngineBlockBreakEvent(
                    event.getBlock(),
                    context,
                    event.getPlayer()
            ))) {
                BlockDataManager.getInstance().save(event.getBlock(), context);
                return;
            }
            if (context != null && !context.adapter().onBreak(context)) {
                BlockDataManager.getInstance().save(event.getBlock(), context);
                return;
            }
            boolean drop = event.getPlayer().getGameMode() != GameMode.CREATIVE || block.storedBlock().dropInCreative();
            String blockId = block.storedBlock().blockId();
            String stateId = block.storedBlock().stateId();
            if (BlockRemover.remove(
                    event.getBlock(),
                    block,
                    drop,
                    Material.AIR,
                    false,
                    BlockEngineBlockRemovedEvent.Reason.PLAYER_BREAK
            )) {
                BlockEngineEvents.call(new BlockEngineBlockBrokenEvent(
                        event.getBlock(),
                        event.getPlayer(),
                        blockId,
                        stateId,
                        drop && block.storedBlock().dropsItem()
                ));
            }
        }
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
        PlacementManager.getInstance().place(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBreakPostVerify(BlockBreakEvent event) {
        postVerify(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlacePostVerify(BlockPlaceEvent event) {
        postVerify(event.getBlockPlaced());
        postVerify(event.getBlockReplacedState().getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPhysicsPostVerify(BlockPhysicsEvent event) {
        postVerify(event.getBlock());
        postVerify(event.getSourceBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBurnPostVerify(BlockBurnEvent event) {
        postVerify(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onFadePostVerify(BlockFadeEvent event) {
        postVerify(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onFromToPostVerify(BlockFromToEvent event) {
        postVerify(event.getBlock());
        postVerify(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityChangeBlockPostVerify(EntityChangeBlockEvent event) {
        postVerify(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPistonExtendPostVerify(BlockPistonExtendEvent event) {
        for (Block moved : event.getBlocks()) {
            postVerify(moved);
            postVerify(moved.getRelative(event.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPistonRetractPostVerify(BlockPistonRetractEvent event) {
        for (Block moved : event.getBlocks()) {
            postVerify(moved);
            postVerify(moved.getRelative(event.getDirection()));
        }
    }

    @EventHandler
    public void onPickBlock(PlayerPickBlockEvent event) {
        if (BlockIntegrityManager.getInstance().verifyInteraction(event.getBlock())) {
            return;
        }
        RuntimeBlockView customBlock = block(event.getBlock());
        if (customBlock == null) {
            return;
        }

        event.setCancelled(true);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            return;
        }

        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return;
        }

        ItemStack stack = ItemManager.create(definition, customBlock.storedBlock().stateId());
        pick(event.getPlayer(), stack, event.getTargetSlot());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }

        if (BlockIntegrityManager.getInstance().verifyInteraction(event.getClickedBlock())) {
            placeHeld(event);
            return;
        }

        RuntimeBlockView block = block(event.getClickedBlock());
        if (block == null) {
            placeHeld(event);
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        handleCustomRightClick(event, block);
    }

    private void handleCustomRightClick(PlayerInteractEvent event, RuntimeBlockView block) {
        if (event.getHand() != EquipmentSlot.HAND) {
            useHeldItem(event);
            return;
        }

        if (event.getPlayer().isSneaking()) {
            if (!placeHeld(event)) {
                useHeldItem(event);
            }
            return;
        }

        BlockContext context = BlockDataManager.getInstance().context(event.getClickedBlock(), block, event.getPlayer());
        if (context != null && context.adapter().onInteract(context, event.getPlayer())) {
            event.setCancelled(true);
            BlockDataManager.getInstance().save(event.getClickedBlock(), context);
            return;
        }

        if (placeHeld(event)) {
            return;
        }
        useHeldItem(event);
    }

    private void explode(Location origin, List<Block> vanillaBlocks) {
        vanillaBlocks.removeIf(block -> block(block) != null);

        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        Set<BlockLocationKey> affected = ExplosionImpactCalculator.affectedByExplosion(origin, vanillaBlocks);
        for (BlockLocationKey location : affected) {
            RuntimeBlockView customBlock = ChunkEngine.getBlock(location);
            if (customBlock == null) {
                continue;
            }
            if (customBlock.storedBlock().unbreakable()) {
                continue;
            }
            Block block = world.getBlockAt(
                    customBlock.location().x(),
                    customBlock.location().y(),
                    customBlock.location().z()
            );
            BlockRemover.remove(
                    block,
                    customBlock,
                    customBlock.storedBlock().dropsItem(),
                    Material.AIR,
                    false,
                    BlockEngineBlockRemovedEvent.Reason.EXPLOSION
            );
        }
    }

    private RuntimeBlockView block(Block block) {
        return ChunkEngine.getBlock(new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
    }

    private void postVerify(Block block) {
        if (BlockIntegrityManager.getInstance().config().listenToBlockUpdates()) {
            BlockIntegrityManager.getInstance().verifyNextTick(block);
        }
    }

    private boolean placeHeld(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return false;
        }

        ItemStack item = event.getItem();
        String blockId = ItemManager.blockId(item);
        if (blockId == null || !ItemManager.placeable(item)) {
            return false;
        }

        Player player = event.getPlayer();
        String namespace = ItemManager.namespace(item);
        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (namespace == null || !NamespaceRegistry.loaded(namespace) || definition == null) {
            event.setCancelled(true);
            return true;
        }

        Block clicked = event.getClickedBlock();
        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(event.getBlockFace());
        String stateId = ItemManager.stateId(item);
        BlockFace placedAgainst = event.getBlockFace().getOppositeFace();
        PlacementVerificationEngine.Result verification = PlacementVerificationEngine.verify(
                new PlacementVerificationEngine.Request(
                        target,
                        definition,
                        stateId,
                        player,
                        placedAgainst,
                        event.getHand()
                )
        );
        if (!verification.allowed()) {
            event.setCancelled(true);
            return true;
        }

        int tick = Bukkit.getCurrentTick();
        UUID playerId = player.getUniqueId();
        if (lastPlacementTicks.getOrDefault(playerId, -1) == tick) {
            event.setCancelled(true);
            return true;
        }

        event.setCancelled(true);
        if (!PlacementManager.getInstance().place(target, definition, player, placedAgainst, stateId)) {
            return true;
        }
        lastPlacementTicks.put(playerId, tick);
        player.swingHand(event.getHand());

        if (player.getGameMode() != GameMode.CREATIVE && item != null) {
            item.subtract();
        }
        return true;
    }

    private void useHeldItem(PlayerInteractEvent event) {
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.ALLOW);
    }

    private void pick(Player player, ItemStack stack, int targetSlot) {
        PlayerInventory inventory = player.getInventory();
        int slot = targetSlot >= 0 && targetSlot <= 8 ? targetSlot : inventory.getHeldItemSlot();
        inventory.setItem(slot, stack);
        inventory.setHeldItemSlot(slot);
        player.updateInventory();
    }
}


