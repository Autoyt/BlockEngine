package dev.auto.blockengine.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.event.BlockEngineBlockBreakEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockBrokenEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockRemovedEvent;
import dev.auto.blockengine.api.event.BlockEngineModificationEvent;
import dev.auto.blockengine.entity.PacketEntityManager;
import dev.auto.blockengine.entity.VirtualItemDisplay;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.placement.AdventurePlacement;
import dev.auto.blockengine.placement.PlacementManager;
import dev.auto.blockengine.placement.PlacementInventory;
import dev.auto.blockengine.placement.PlacementVerificationEngine;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.runtime.BlockContext;
import dev.auto.blockengine.runtime.BlockDataManager;
import dev.auto.blockengine.runtime.BlockDropResolver;
import dev.auto.blockengine.runtime.BlockRemover;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.GravityManager;
import dev.auto.blockengine.structure.SudoBlockListeners;
import dev.auto.blockengine.structure.SudoBlockManager;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.visibility.VisibilityManager;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.SwingAnimation;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.Player;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import io.papermc.paper.event.block.BlockPreDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class GameListener implements Listener {
    private final Map<UUID, MainHandClick> mainHandClicks = new HashMap<>();
    private final Map<UUID, Integer> placementTicks = new HashMap<>();
    private final Map<UUID, MiningSession> miningSessions = new HashMap<>();
    private final Set<BlockLocationKey> breakingBlocks = new HashSet<>();

    public GameListener() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    public void stop() {
        for (MiningSession session : List.copyOf(miningSessions.values())) {
            stopMining(session.player());
        }
        mainHandClicks.clear();
        placementTicks.clear();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        ChunkEngine.load(event.getChunk(), VisibilityManager.getInstance().config());
        GravityManager.getInstance().queueLoaded(ChunkEngine.get(ChunkEngine.Key.from(event.getChunk())));
        BlockIntegrityManager.getInstance().enqueue(event.getChunk());
        VisibilityManager.getInstance().refreshPlayersNear(ChunkEngine.Key.from(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        ChunkEngine.Key key = ChunkEngine.Key.from(event.getChunk());
        ChunkEngine.unload(event.getChunk());
        VisibilityManager.getInstance().removeChunkDisplays(key);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        ChunkEngine.saveWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        for (org.bukkit.Chunk chunk : event.getWorld().getLoadedChunks()) {
            ChunkEngine.unload(chunk);
            VisibilityManager.getInstance().removeChunkDisplays(ChunkEngine.Key.from(chunk));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        VisibilityManager.getInstance().handleMove(event);
        Location to = event.getTo();
        if (to == null || event.getFrom().getBlockX() == to.getBlockX()
                && event.getFrom().getBlockY() == to.getBlockY()
                && event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (!event.getPlayer().isOnGround()) {
            return;
        }
        playCustomSound(block(to.clone().subtract(0.0, 0.1, 0.0).getBlock()), "step");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        boolean changeWorld = !event.getFrom().getWorld().equals(event.getTo().getWorld());
        VisibilityManager.getInstance().scheduleRecalculate(event.getPlayer(), changeWorld);
    }

    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        VisibilityManager.getInstance().scheduleRecalculate(event.getPlayer(), true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ItemManager.updatePlacementItems(event.getPlayer().getInventory());
        ResourcePackManager.getInstance().send(event.getPlayer());
        VisibilityManager.getInstance().forceRecalculate(event.getPlayer());
    }

    @EventHandler
    public void onInventorySlotChange(PlayerInventorySlotChangeEvent event) {
        ItemStack current = event.getNewItemStack();
        ItemStack updated = ItemManager.updatePlacementItem(current);
        if (updated == current) {
            return;
        }

        PlayerInventory inventory = event.getPlayer().getInventory();
        int slot = event.getSlot();
        if (slot >= 0 && slot < inventory.getSize() && current.equals(inventory.getItem(slot))) {
            inventory.setItem(slot, updated);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ChunkEngine.flushNow();
        mainHandClicks.remove(event.getPlayer().getUniqueId());
        placementTicks.remove(event.getPlayer().getUniqueId());
        stopMining(event.getPlayer());
        VisibilityManager.getInstance().cleanup(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        if (ItemManager.wand(event.getItemInHand())) {
            event.setCancelled(true);
            return;
        }
        if (BlockIntegrityManager.getInstance().verifyInteraction(event.getBlock())) {
            return;
        }
        RuntimeBlockView block = block(event.getBlock());
        if (block == null) {
            return;
        }
        event.setCancelled(true);
        if (block.storedBlock().unbreakable()) {
            return;
        }
        playCustomSound(block, "hit");
        if (event.getInstaBreak() || VanillaMiningCalculator.progressPerTick(event.getPlayer(), block) >= 1.0f) {
            stopMining(event.getPlayer());
            event.getPlayer().breakBlock(event.getBlock());
        } else {
            startMining(event.getPlayer(), event.getBlock(), block);
        }
    }

    @EventHandler
    public void onDamageAbort(BlockDamageAbortEvent event) {
        stopMining(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockReplaced(BlockEngineModificationEvent event) {
        // Data-only ticks can continue a dig. A removal or placement starts a new block
        // lifetime even when the replacement uses exactly the same id and state.
        for (MiningSession session : List.copyOf(miningSessions.values())) {
            if (session.sameBlock(event.block())) {
                stopMining(session.player());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (ItemManager.wand(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            return;
        }
        if (BlockIntegrityManager.getInstance().verifyInteraction(event.getBlock())) {
            return;
        }
        RuntimeBlockView block = block(event.getBlock());
        if (block != null) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            boolean creative = player.getGameMode() == GameMode.CREATIVE;
            boolean drop = event.isDropItems() && (creative
                    ? block.storedBlock().dropInCreative()
                    : VanillaMiningCalculator.canHarvest(player, block));
            if (breakCustomBlock(event.getBlock(), block, player, drop) && event.getExpToDrop() > 0) {
                event.getBlock().getWorld().spawn(event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                        ExperienceOrb.class, orb -> orb.setExperience(event.getExpToDrop()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        playCustomSound(block(player.getLocation().clone().subtract(0.0, 0.1, 0.0).getBlock()), "fall");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplosionCandidates(EntityExplodeEvent event) {
        addExplosionCandidates(event.getLocation(), event.blockList(), event.getExplosionResult());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplosionCandidates(BlockExplodeEvent event) {
        addExplosionCandidates(event.getBlock().getLocation().add(0.5, 0.5, 0.5), event.blockList(), event.getExplosionResult());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        explode(event.blockList(), event.getExplosionResult(), event.getYield());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        explode(event.blockList(), event.getExplosionResult(), event.getYield());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        // Player interactions own the custom placement. Never leave a vanilla carrier block behind.
        if (!(event instanceof PlayerPlacementCheck) && ItemManager.blockId(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockPreDispenseEvent event) {
        Block source = event.getBlock();
        ItemStack item = event.getItemStack();
        if (source.getType() != Material.DISPENSER || !ItemManager.placeable(item)) {
            return;
        }

        // Paper still has the entire stack in this exact slot, including the last item.
        event.setCancelled(true);
        BlockDefinition definition = BlockRegistry.getBlock(ItemManager.blockId(item));
        String stateId = ItemManager.stateId(item);
        String resolvedState = stateId == null || stateId.isBlank() ? definition.apiDefinition().defaultState() : stateId;
        var state = definition.apiDefinition().states().get(resolvedState);
        if (!NamespaceRegistry.loaded(definition.name().namespace()) || state == null || !state.movement().dispenserPlaceable()) {
            return;
        }

        BlockFace facing = dispenserFacing(source);
        Block target = source.getRelative(facing);
        if (!PlacementVerificationEngine.verify(new PlacementVerificationEngine.Request(
                target, definition, resolvedState, null, facing, null, PlacementVerificationEngine.Source.DISPENSER
        )).allowed()) {
            return;
        }

        ItemStack expected = item.clone();
        boolean placed = PlacementManager.getInstance().place(target, definition, null, facing, resolvedState,
                () -> source.getState(false) instanceof Dispenser dispenser
                        && PlacementInventory.consumeOne(dispenser.getInventory(), event.getSlot(), expected));
        if (placed) {
            queueGravityUpdateAround(target);
        }
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickBlock(PlayerPickBlockEvent event) {
        String sudoBlockId = SudoBlockManager.getInstance().markerBlockId(event.getBlock());
        if (sudoBlockId != null) {
            event.setCancelled(true);
            if (!SudoBlockListeners.allowed(event.getPlayer())) {
                return;
            }
            BlockDefinition definition = BlockRegistry.getBlock(sudoBlockId);
            if (definition == null) {
                return;
            }
            pick(event.getPlayer(), ItemManager.createSudo(definition, SudoBlockManager.getInstance().markerStateId(event.getBlock())), event.getTargetSlot());
            return;
        }

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand();
        if (hand == null || ItemManager.wand(item)) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            if (ItemManager.blockId(item) != null) {
                event.setUseItemInHand(Event.Result.DENY);
            }
            return;
        }

        Block clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null) {
            return;
        }

        Player player = event.getPlayer();
        int tick = Bukkit.getCurrentTick();
        MainHandClick click = new MainHandClick(tick, clicked, event.getBlockFace());
        if (hand == EquipmentSlot.OFF_HAND && click.equals(mainHandClicks.remove(player.getUniqueId()))) {
            event.setCancelled(true);
            return;
        }

        RuntimeBlockView customBlock = BlockIntegrityManager.getInstance().verifyInteraction(clicked) ? null : block(clicked);
        if (!player.isSneaking() && event.useInteractedBlock() != Event.Result.DENY) {
            if (customBlock != null && hand == EquipmentSlot.HAND) {
                BlockContext context = BlockDataManager.getInstance().context(clicked, customBlock, player);
                if (context != null && context.adapter().onInteract(context, player)) {
                    event.setCancelled(true);
                    mainHandClicks.put(player.getUniqueId(), click);
                    BlockDataManager.getInstance().save(clicked, context);
                    return;
                }
            }
            if (customBlock == null && clicked.getType().isInteractable() && ItemManager.blockId(item) != null) {
                // Let a chest, door, etc. receive the click without also using the carrier item.
                event.setUseItemInHand(Event.Result.DENY);
                return;
            }
        }

        String blockId = ItemManager.blockId(item);
        if (blockId == null || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        event.setCancelled(true);

        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (definition == null || !definition.apiDefinition().item().placeable()
                || !NamespaceRegistry.loaded(definition.name().namespace())) {
            return;
        }

        if (hand == EquipmentSlot.HAND) {
            mainHandClicks.put(player.getUniqueId(), click);
        }
        placeHeld(event, clicked, definition, item, hand);
    }

    private void addExplosionCandidates(Location origin, List<Block> blocks, ExplosionResult result) {
        World world = origin.getWorld();
        if (world == null || !destructive(result)) {
            return;
        }

        // Paper sees barriers. Expose damageable custom blocks to the event before protection
        // listeners run, without expanding Paper's list of vanilla blocks.
        for (BlockLocationKey key : ExplosionImpactCalculator.affectedByExplosion(origin, blocks)) {
            Block candidate = world.getBlockAt(key.x(), key.y(), key.z());
            if (!blocks.contains(candidate)) {
                blocks.add(candidate);
            }
        }
    }

    private void explode(List<Block> blocks, ExplosionResult result, float yield) {
        if (!destructive(result)) {
            return;
        }

        // The final list has passed protection checks. Take ownership of custom entries here
        // so Paper cannot also break the backing blocks or produce vanilla barrier drops.
        Map<Block, RuntimeBlockView> affected = new HashMap<>();
        blocks.removeIf(block -> {
            RuntimeBlockView view = block(block);
            if (view == null || block.getType() != Main.getBackingBlock()) {
                return false;
            }
            if (!view.storedBlock().unbreakable()) {
                affected.put(block, view);
            }
            return true;
        });

        for (Block block : blocks) {
            SudoBlockManager.getInstance().removePreviewIfMarker(block);
        }

        affected.forEach((block, view) -> {
            if (isCurrentBlock(block, view) && block.getType() == Main.getBackingBlock()) {
                boolean drop = java.util.concurrent.ThreadLocalRandom.current().nextFloat() < yield;
                if (BlockRemover.remove(block, view, drop, Material.AIR, false, BlockEngineBlockRemovedEvent.Reason.EXPLOSION)) {
                    queueGravityUpdateAround(block);
                }
            }
        });
    }

    static boolean destructive(ExplosionResult result) {
        return result == ExplosionResult.DESTROY || result == ExplosionResult.DESTROY_WITH_DECAY;
    }

    private RuntimeBlockView block(Block block) {
        return ChunkEngine.getBlock(new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
    }

    private @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
    }

    private void startMining(@NotNull Player player, @NotNull Block block, @NotNull RuntimeBlockView customBlock) {
        MiningSession previous = miningSessions.get(player.getUniqueId());
        if (previous != null && previous.sameBlock(block)) {
            return;
        }
        stopMining(player);

        MiningSession session = new MiningSession(player, block, customBlock);
        session.task(Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> tickMining(session), 1L, 1L));
        miningSessions.put(player.getUniqueId(), session);
    }

    private void tickMining(@NotNull MiningSession session) {
        Player player = session.player();
        if (!player.isOnline() || player.isDead() || player.getGameMode() != session.mode
                || !player.getInventory().getItemInMainHand().isSimilar(session.tool)) {
            stopMining(player);
            return;
        }

        RuntimeBlockView current = block(session.block());
        if (current == null
                || !sameStoredBlock(current, session.customBlock())
                || player.getWorld() != session.block().getWorld()
                || !stillTargeting(player, session.block())) {
            stopMining(player);
            return;
        }

        session.advance(VanillaMiningCalculator.progressPerTick(player, session.customBlock()));
        session.tick();
        if (session.ticks() % 4 == 0) {
            playCustomSound(session.customBlock(), "mining");
        }
        byte stage = stage(session.progress());
        if (session.stage(stage)) {
            sendBreakStage(session, stage);
        }
        if (session.progress() < 1.0f) {
            return;
        }

        stopMining(player);
        // Let Paper dispatch the ordinary break event so other plugins can veto it and control rewards.
        player.breakBlock(session.block());
    }

    private boolean stillTargeting(@NotNull Player player, @NotNull Block target) {
        var range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        double reach = range == null ? 4.5 : range.getValue();
        if (reach <= 0) {
            return false;
        }
        var hit = player.rayTraceBlocks(reach);
        return hit != null && target.equals(hit.getHitBlock());
    }

    private void stopMining(@NotNull Player player) {
        MiningSession session = miningSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.cancel();
        sendBreakStage(session, (byte) -1);
    }

    private void stopMining(@NotNull Player player, @NotNull Block block) {
        MiningSession session = miningSessions.get(player.getUniqueId());
        if (session != null && session.sameBlock(block)) {
            stopMining(player);
        }
    }

    private boolean breakCustomBlock(
            @NotNull Block block,
            @NotNull RuntimeBlockView customBlock,
            @NotNull Player player,
            boolean dropItems
    ) {
        BlockLocationKey location = location(block);
        if (customBlock.storedBlock().unbreakable() || !breakingBlocks.add(location)) {
            return false;
        }

        try {
            return finishBreak(block, customBlock, player, dropItems);
        } finally {
            breakingBlocks.remove(location);
        }
    }

    private boolean finishBreak(
            @NotNull Block block,
            @NotNull RuntimeBlockView customBlock,
            @NotNull Player player,
            boolean dropItems
    ) {
        if (!isCurrentBlock(block, customBlock)) {
            return false;
        }

        ItemStack tool = player.getInventory().getItemInMainHand().clone();
        boolean drop = dropItems && customBlock.storedBlock().dropsItem();
        BlockContext context = BlockDataManager.getInstance().context(block, customBlock, player);
        if (!isCurrentBlock(block, customBlock)) {
            return false;
        }

        if (context != null && BlockEngineEvents.callCancellable(new BlockEngineBlockBreakEvent(
                block,
                context,
                player
        ))) {
            saveCancelledBreak(block, customBlock, context);
            return false;
        }
        if (!isCurrentBlock(block, customBlock)) {
            return false;
        }

        if (context != null && !context.adapter().onBreak(context)) {
            saveCancelledBreak(block, customBlock, context);
            return false;
        }
        if (!isCurrentBlock(block, customBlock)) {
            return false;
        }

        List<ItemStack> drops = List.of();
        if (drop && context != null) {
            BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
            if (definition != null) {
                try {
                    drops = BlockDropResolver.resolve(context, tool, ItemManager.create(definition));
                } catch (RuntimeException error) {
                    Main.getInstance().getLogger().log(Level.SEVERE,
                            "Could not resolve drops for " + customBlock.storedBlock().blockId()
                                    + " at " + customBlock.location() + "; leaving the block intact", error);
                    return false;
                }
            }
        }

        String blockId = customBlock.storedBlock().blockId();
        String stateId = customBlock.storedBlock().stateId();
        BlockRemover.RemovalResult result = BlockRemover.removeWithDrops(
                block,
                customBlock,
                drops,
                Material.AIR,
                false,
                BlockEngineBlockRemovedEvent.Reason.PLAYER_BREAK
        );
        if (!result.removed()) {
            return false;
        }
        if (player.getGameMode() != GameMode.CREATIVE && customBlock.storedBlock().hardness() > 0
                && player.getInventory().getItemInMainHand().isSimilar(tool)) {
            var toolSettings = tool.getData(DataComponentTypes.TOOL);
            if (toolSettings != null && toolSettings.damagePerBlock() > 0) {
                player.damageItemStack(EquipmentSlot.HAND, toolSettings.damagePerBlock());
            }
        }
        queueGravityUpdateAround(block);
        BlockEngineEvents.call(new BlockEngineBlockBrokenEvent(
                block,
                player,
                blockId,
                stateId,
                result.drops()
        ));
        return true;
    }

    private boolean isCurrentBlock(@NotNull Block block, @NotNull RuntimeBlockView expected) {
        RuntimeBlockView current = block(block);
        return current != null && current.storedBlock() == expected.storedBlock();
    }

    private void saveCancelledBreak(
            @NotNull Block block,
            @NotNull RuntimeBlockView expected,
            @NotNull BlockContext context
    ) {
        // A cancellation must not recreate a block already removed by a callback.
        if (isCurrentBlock(block, expected)) {
            BlockDataManager.getInstance().save(block, context);
        }
    }

    private void playCustomSound(@Nullable RuntimeBlockView customBlock, @NotNull String type) {
        String sound = customSound(customBlock, type);
        if (sound == null || customBlock == null) {
            return;
        }
        World world = Bukkit.getWorld(customBlock.location().worldId());
        if (world == null) {
            return;
        }
        world.playSound(
                new Location(world, customBlock.location().x() + 0.5, customBlock.location().y() + 0.5, customBlock.location().z() + 0.5),
                sound,
                SoundCategory.BLOCKS,
                0.6f,
                1.0f
        );
    }

    private @Nullable String customSound(@Nullable RuntimeBlockView customBlock, @NotNull String type) {
        if (customBlock == null) {
            return null;
        }
        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return null;
        }

        try {
            var sounds = definition.apiDefinition()
                    .state(customBlock.storedBlock().stateId())
                    .sounds();
            String sound = switch (type) {
                case "hit" -> sounds.hit();
                case "mining" -> sounds.mining();
                case "step" -> sounds.step();
                case "fall" -> sounds.fall();
                default -> null;
            };
            return sound == null || NamespacedKey.fromString(sound) == null ? null : sound;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean sameStoredBlock(@NotNull RuntimeBlockView current, @NotNull RuntimeBlockView original) {
        return current.location().equals(original.location())
                && current.storedBlock().blockId().equals(original.storedBlock().blockId())
                && current.storedBlock().stateId().equals(original.storedBlock().stateId());
    }

    private byte stage(float progress) {
        if (progress >= 1.0f) {
            return 9;
        }
        return (byte) Math.clamp((int) Math.floor(progress * 10.0f), 0, 9);
    }

    private void sendBreakStage(@NotNull MiningSession session, byte stage) {
        Vector3i position = new Vector3i(
                session.block().getX(),
                session.block().getY(),
                session.block().getZ()
        );
        List<Player> viewers = viewers(session.block());
        for (Player viewer : viewers) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                    viewer,
                    new WrapperPlayServerBlockBreakAnimation(session.animationId(), position, stage)
            );
        }
        sendBreakOverlay(session, stage, viewers);
    }

    private void sendBreakOverlay(
            @NotNull MiningSession session,
            byte stage,
            @NotNull List<Player> viewers
    ) {
        VirtualItemDisplay overlay = session.overlay();
        if (stage < 0) {
            if (overlay != null) {
                overlay.destroyForAll();
                PacketEntityManager.release(overlay);
                session.overlay(null);
            }
            return;
        }

        if (overlay == null) {
            overlay = PacketEntityManager.itemDisplay()
                    .location(session.block().getLocation().add(0.5, 0.5, 0.5))
                    .itemStack(breakOverlay(stage))
                    .displayContext(VirtualItemDisplay.DISPLAY_CONTEXT_FIXED)
                    .scale(2.025f, 2.025f, 2.025f)
                    .viewRange(1.25f)
                    .brightness(15, 15)
                    .shadowRadius(0.0f)
                    .shadowStrength(0.0f);
            session.overlay(overlay);
            overlay.spawnFor(viewers);
            return;
        }

        overlay.itemStack(breakOverlay(stage));
        overlay.updateMetadataFor(viewers);
    }

    private @NotNull ItemStack breakOverlay(byte stage) {
        ItemStack stack = new ItemStack(Main.getBackingBlock());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey(Main.getInstance(), "break_stage/" + Math.clamp(stage, 0, 9)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private @NotNull List<Player> viewers(@NotNull Block block) {
        double maxDistanceSquared = Math.pow((Main.getInstance().getServer().getViewDistance() + 1) * 16.0, 2.0);
        return block.getWorld().getPlayers().stream()
                .filter(viewer -> viewer.getLocation().distanceSquared(block.getLocation()) <= maxDistanceSquared)
                .toList();
    }

    private void postVerify(Block block) {
        ChunkEngine.queueExposureAround(block);
        if (BlockIntegrityManager.getInstance().config().listenToBlockUpdates()) {
            BlockIntegrityManager.getInstance().verifyNextTick(block);
        }
        queueGravityUpdateAround(block);
    }

    private void queueGravityUpdateAround(@NotNull Block origin) {
        GravityManager.getInstance().queueCascadeAfterFlush(origin);
    }

    private @NotNull BlockFace dispenserFacing(@NotNull Block dispenser) {
        if (dispenser.getBlockData() instanceof Directional directional) {
            return directional.getFacing();
        }
        return BlockFace.NORTH;
    }

    private void placeHeld(PlayerInteractEvent event, Block clicked, BlockDefinition definition,
                           ItemStack item, EquipmentSlot hand) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR
                || player.getGameMode() == GameMode.ADVENTURE && !AdventurePlacement.allows(item, clicked)) {
            return;
        }

        BlockFace face = event.getBlockFace();
        Block target = clicked.isReplaceable() || clicked.isLiquid() ? clicked : clicked.getRelative(face);
        String stateId = ItemManager.stateId(item);
        ItemStack expected = item.clone();
        PlayerInventory inventory = player.getInventory();
        int slot = PlacementInventory.slot(inventory, hand);

        // Reserve before firing placement events, which can trigger another use from either hand.
        UUID playerId = player.getUniqueId();
        int tick = Bukkit.getCurrentTick();
        Integer previousTick = placementTicks.put(playerId, tick);
        if (previousTick != null && previousTick == tick) {
            return;
        }

        boolean placed = false;
        try {
            if (!PlacementVerificationEngine.verify(new PlacementVerificationEngine.Request(
                    target, definition, stateId, player, face, hand, PlacementVerificationEngine.Source.PLAYER
            )).allowed() || !bukkitAllowsPlacement(target, clicked, expected, player, hand)) {
                return;
            }

            placed = PlacementManager.getInstance().place(target, definition, player, face, stateId,
                    () -> player.getGameMode() == GameMode.CREATIVE || PlacementInventory.consumeOne(inventory, slot, expected));
        } finally {
            if (!placed) {
                placementTicks.remove(playerId);
            }
        }

        if (placed) {
            queueGravityUpdateAround(target);
            // Use the vanilla interaction swing only after placement and payment have succeeded.
            player.swingHand(hand, SwingAnimation.swingAnimation().build());
        }
    }

    private boolean bukkitAllowsPlacement(Block target, Block clicked, ItemStack item, Player player, EquipmentSlot hand) {
        // Bukkit placement listeners inspect the already placed block and its previous snapshot.
        if (!target.isReplaceable() && !target.isLiquid()) {
            return false;
        }
        BlockState replaced = target.getState();
        target.setType(Main.getBackingBlock(), false);
        if (target.getType() != Main.getBackingBlock()) {
            return false;
        }
        try {
            PlayerPlacementCheck check = new PlayerPlacementCheck(target, replaced, clicked, item, player, hand);
            return check.callEvent() && check.canBuild() && target.getType() == Main.getBackingBlock()
                    && block(target) == null;
        } finally {
            if (target.getType() == Main.getBackingBlock() && block(target) == null) {
                replaced.update(true, false);
            }
        }
    }

    private record MainHandClick(int tick, Block clicked, BlockFace face) {}

    private static final class PlayerPlacementCheck extends BlockPlaceEvent {
        PlayerPlacementCheck(Block target, BlockState replaced, Block clicked, ItemStack item, Player player, EquipmentSlot hand) {
            super(target, replaced, clicked, item, player, true, hand);
        }
    }

    private void pick(Player player, ItemStack stack, int targetSlot) {
        PlayerInventory inventory = player.getInventory();
        int slot = targetSlot >= 0 && targetSlot <= 8 ? targetSlot : inventory.getHeldItemSlot();
        inventory.setItem(slot, stack);
        inventory.setHeldItemSlot(slot);
        player.updateInventory();
    }

    private static final class MiningSession {
        private final @NotNull Player player;
        private final @NotNull GameMode mode;
        private final @NotNull ItemStack tool;
        private final @NotNull Block block;
        private final @NotNull RuntimeBlockView customBlock;
        private final int animationId;
        private byte stage = -1;
        private float progress;
        private int ticks;
        private @Nullable BukkitTask task;
        private @Nullable VirtualItemDisplay overlay;

        private MiningSession(
                @NotNull Player player,
                @NotNull Block block,
                @NotNull RuntimeBlockView customBlock
        ) {
            this.player = player;
            this.mode = player.getGameMode();
            this.tool = player.getInventory().getItemInMainHand().clone();
            this.block = block;
            this.customBlock = customBlock;
            this.animationId = animationId(player, block);
        }

        private @NotNull Player player() {
            return player;
        }

        private @NotNull Block block() {
            return block;
        }

        private @NotNull RuntimeBlockView customBlock() {
            return customBlock;
        }

        private int animationId() {
            return animationId;
        }

        private float progress() {
            return progress;
        }

        private boolean stage(byte stage) {
            if (this.stage == stage) {
                return false;
            }
            this.stage = stage;
            return true;
        }

        private void advance(float amount) {
            progress = Math.min(1.0f, progress + amount);
        }

        private void tick() {
            ticks++;
        }

        private int ticks() {
            return ticks;
        }

        private void task(@NotNull BukkitTask task) {
            this.task = task;
        }

        private void cancel() {
            if (task != null) {
                task.cancel();
            }
        }

        private @Nullable VirtualItemDisplay overlay() {
            return overlay;
        }

        private void overlay(@Nullable VirtualItemDisplay overlay) {
            this.overlay = overlay;
        }

        private boolean sameBlock(@NotNull Block other) {
            return block.getWorld().equals(other.getWorld())
                    && block.getX() == other.getX()
                    && block.getY() == other.getY()
                    && block.getZ() == other.getZ();
        }

        private static int animationId(@NotNull Player player, @NotNull Block block) {
            int result = player.getEntityId();
            result = 31 * result + block.getX();
            result = 31 * result + block.getY();
            result = 31 * result + block.getZ();
            return result;
        }
    }
}
