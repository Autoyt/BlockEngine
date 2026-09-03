package dev.auto.blockengine.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.event.BlockEngineBlockBreakEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockBrokenEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockRemovedEvent;
import dev.auto.blockengine.entity.PacketEntityManager;
import dev.auto.blockengine.entity.VirtualItemDisplay;
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
import dev.auto.blockengine.runtime.GravityManager;
import dev.auto.blockengine.structure.SudoBlockListeners;
import dev.auto.blockengine.structure.SudoBlockManager;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.visibility.VisibilityManager;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDispenseEvent;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameListener implements Listener {
    private final Map<UUID, Integer> lastPlacementTicks = new HashMap<>();
    private final Map<UUID, MiningSession> miningSessions = new HashMap<>();

    public GameListener() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        ChunkEngine.load(event.getChunk(), VisibilityManager.getInstance().config());
        BlockIntegrityManager.getInstance().enqueue(event.getChunk());
        VisibilityManager.getInstance().refreshPlayersNear(ChunkEngine.Key.from(event.getChunk()));
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
        stopMining(event.getPlayer());
        VisibilityManager.getInstance().cleanup(event.getPlayer());
    }

    @EventHandler
    public void onDamage(BlockDamageEvent event) {
        if (ItemManager.wand(event.getItemInHand())) {
            event.setCancelled(true);
            return;
        }
        if (BlockIntegrityManager.getInstance().verifyInteraction(event.getBlock())) {
            return;
        }
        RuntimeBlockView block = block(event.getBlock());
        if (block == null || block.storedBlock().unbreakable()) {
            return;
        }
        event.setCancelled(true);
        playCustomSound(block, "hit");
        breakOrStartMining(event.getPlayer(), event.getBlock(), block);
    }

    @EventHandler
    public void onDamageAbort(BlockDamageAbortEvent event) {
        stopMining(event.getPlayer(), event.getBlock());
    }

    @EventHandler
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
            breakCustomBlock(
                    event.getBlock(),
                    block,
                    event.getPlayer(),
                    event.getPlayer().getGameMode() == GameMode.CREATIVE
                            || VanillaMiningCalculator.canHarvest(event.getPlayer(), block)
            );
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        playCustomSound(block(player.getLocation().clone().subtract(0.0, 0.1, 0.0).getBlock()), "fall");
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

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        String blockId = ItemManager.blockId(event.getItem());
        if (blockId == null || !ItemManager.placeable(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        Block dispenserBlock = event.getBlock();
        BlockFace facing = dispenserFacing(dispenserBlock);
        Block target = dispenserBlock.getRelative(facing);
        String namespace = ItemManager.namespace(event.getItem());
        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (namespace == null || !NamespaceRegistry.loaded(namespace) || definition == null) {
            return;
        }
        String stateId = ItemManager.stateId(event.getItem());
        if (!definition.apiDefinition().state(stateId == null ? definition.apiDefinition().defaultState() : stateId)
                .movement()
                .dispenserPlaceable()) {
            return;
        }

        PlacementVerificationEngine.Result verification = PlacementVerificationEngine.verify(
                new PlacementVerificationEngine.Request(
                        target,
                        definition,
                        stateId,
                        null,
                        facing.getOppositeFace(),
                        null,
                        PlacementVerificationEngine.Source.DISPENSER
                )
        );
        if (!verification.allowed() || !PlacementManager.getInstance().place(target, definition, null, facing.getOppositeFace(), stateId)) {
            return;
        }
        queueGravityUpdateAround(target);

        if (dispenserBlock.getState() instanceof Dispenser dispenser) {
            consumeDispenserItem(dispenser, event.getItem());
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

    @EventHandler
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
        if (!SudoBlockListeners.allowed(event.getPlayer())) {
            return;
        }

        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return;
        }

        ItemStack stack = ItemManager.createSudo(definition, customBlock.storedBlock().stateId());
        pick(event.getPlayer(), stack, event.getTargetSlot());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlaceHeldBeforeVanillaUse(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null
                && event.getAction() == Action.RIGHT_CLICK_AIR
                && event.getHand() == EquipmentSlot.HAND
                && ItemManager.placeable(event.getItem())) {
            denyVanillaPlacement(event);
            return;
        }

        if (event.getClickedBlock() == null
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || !ItemManager.placeable(event.getItem())
                || shouldDeferToVanillaUse(event)) {
            return;
        }
        placeHeld(event);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (ItemManager.wand(event.getItem())) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }

        if (shouldDeferToVanillaUse(event)) {
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
            event.setCancelled(true);
            if (!block.storedBlock().unbreakable()) {
                breakOrStartMining(event.getPlayer(), event.getClickedBlock(), block);
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        handleCustomRightClick(event, block);
    }

    private void handleCustomRightClick(PlayerInteractEvent event, RuntimeBlockView block) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getPlayer().isSneaking()) {
            placeHeld(event);
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
    }

    private void explode(Location origin, List<Block> vanillaBlocks) {
        // Sudo markers are vanilla chests, so they are destroyed through the
        // vanilla explosion list rather than BlockRemover. Clean up their
        // persistent preview displays before the blocks disappear.
        for (Block block : vanillaBlocks) {
            SudoBlockManager.getInstance().removePreviewIfMarker(block);
        }
        vanillaBlocks.removeIf(block -> block(block) != null);

        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        Set<BlockLocationKey> affected = ExplosionImpactCalculator.affectedByExplosion(origin, vanillaBlocks);
        for (BlockLocationKey location : affected) {
            RuntimeBlockView customBlock = ChunkEngine.getBlock(location);
            if (customBlock == null) {
                Block vanillaBlock = world.getBlockAt(location.x(), location.y(), location.z());
                if (!vanillaBlock.getType().isAir() && !vanillaBlocks.contains(vanillaBlock)) {
                    vanillaBlocks.add(vanillaBlock);
                }
                continue;
            }
            Block block = world.getBlockAt(
                    customBlock.location().x(),
                    customBlock.location().y(),
                    customBlock.location().z()
            );
            if (moveable(customBlock)) {
                queueGravityUpdateAround(block);
            }
            if (customBlock.storedBlock().unbreakable()) {
                continue;
            }
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

    private boolean moveable(@NotNull RuntimeBlockView customBlock) {
        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return false;
        }

        try {
            return definition.apiDefinition()
                    .state(customBlock.storedBlock().stateId())
                    .movement()
                    .gravity();
        } catch (IllegalArgumentException ignored) {
            return false;
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

    private void breakOrStartMining(@NotNull Player player, @NotNull Block block, @NotNull RuntimeBlockView customBlock) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (VanillaMiningCalculator.progressPerTick(player, customBlock) >= 1.0f) {
                stopMining(player, block);
                RuntimeBlockView current = block(block);
                if (current != null && sameStoredBlock(current, customBlock)) {
                    breakCustomBlock(block, current, player, VanillaMiningCalculator.canHarvest(player, current));
                }
                return;
            }
            startMining(player, block, customBlock);
            return;
        }

        stopMining(player, block);
        RuntimeBlockView current = block(block);
        if (current != null && sameStoredBlock(current, customBlock)) {
            breakCustomBlock(block, current, player);
        }
    }

    private void tickMining(@NotNull MiningSession session) {
        Player player = session.player();
        if (!player.isOnline()) {
            stopMining(player);
            return;
        }

        RuntimeBlockView current = block(session.block());
        if (current == null
                || !sameStoredBlock(current, session.customBlock())
                || player.getWorld() != session.block().getWorld()
                || player.getLocation().distanceSquared(session.block().getLocation().add(0.5, 0.5, 0.5)) > 36.0) {
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
        breakCustomBlock(session.block(), current, player, VanillaMiningCalculator.canHarvest(player, current));
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

    private boolean breakCustomBlock(@NotNull Block block, @NotNull RuntimeBlockView customBlock, @NotNull Player player) {
        return breakCustomBlock(block, customBlock, player, true);
    }

    private boolean breakCustomBlock(
            @NotNull Block block,
            @NotNull RuntimeBlockView customBlock,
            @NotNull Player player,
            boolean harvestable
    ) {
        BlockContext context = BlockDataManager.getInstance().context(block, customBlock, player);
        if (context != null && BlockEngineEvents.callCancellable(new BlockEngineBlockBreakEvent(
                block,
                context,
                player
        ))) {
            BlockDataManager.getInstance().save(block, context);
            return false;
        }
        if (context != null && !context.adapter().onBreak(context)) {
            BlockDataManager.getInstance().save(block, context);
            return false;
        }

        boolean drop = player.getGameMode() == GameMode.CREATIVE
                ? customBlock.storedBlock().dropInCreative()
                : harvestable;
        String blockId = customBlock.storedBlock().blockId();
        String stateId = customBlock.storedBlock().stateId();
        if (!BlockRemover.remove(
                block,
                customBlock,
                drop,
                Material.AIR,
                false,
                BlockEngineBlockRemovedEvent.Reason.PLAYER_BREAK
        )) {
            return false;
        }
        queueGravityUpdateAround(block);
        BlockEngineEvents.call(new BlockEngineBlockBrokenEvent(
                block,
                player,
                blockId,
                stateId,
                drop && customBlock.storedBlock().dropsItem()
        ));
        return true;
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

    private void consumeDispenserItem(@NotNull Dispenser dispenser, @NotNull ItemStack dispensed) {
        for (ItemStack stack : dispenser.getInventory().getContents()) {
            if (stack != null && stack.isSimilar(dispensed)) {
                stack.subtract();
                dispenser.update();
                return;
            }
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
        denyVanillaPlacement(event);

        Player player = event.getPlayer();
        int tick = Bukkit.getCurrentTick();
        UUID playerId = player.getUniqueId();
        if (lastPlacementTicks.getOrDefault(playerId, -1) == tick) {
            return true;
        }
        lastPlacementTicks.put(playerId, tick);

        String namespace = ItemManager.namespace(item);
        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (namespace == null || !NamespaceRegistry.loaded(namespace) || definition == null) {
            return true;
        }

        Block clicked = event.getClickedBlock();
        Block target = replaceableTarget(clicked)
                ? clicked
                : clicked.getRelative(event.getBlockFace());
        String stateId = ItemManager.stateId(item);
        BlockFace placedAgainst = event.getBlockFace();
        PlacementVerificationEngine.Result verification = PlacementVerificationEngine.verify(
                new PlacementVerificationEngine.Request(
                        target,
                        definition,
                        stateId,
                        player,
                        placedAgainst,
                        event.getHand(),
                        PlacementVerificationEngine.Source.PLAYER
                )
        );
        if (!verification.allowed()) {
            return true;
        }

        boolean placed = PlacementManager.getInstance().place(target, definition, player, placedAgainst, stateId);
        if (!placed) {
            return true;
        }
        queueGravityUpdateAround(target);

        player.swingHand(event.getHand());

        if (player.getGameMode() != GameMode.CREATIVE && item != null) {
            item.subtract();
        }
        return true;
    }

    private boolean replaceableTarget(@NotNull Block block) {
        return block.isReplaceable() || block.isLiquid();
    }

    private boolean shouldDeferToVanillaUse(@NotNull PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        return clicked != null
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getHand() == EquipmentSlot.HAND
                && !event.getPlayer().isSneaking()
                && ItemManager.placeable(event.getItem())
                && clicked.getType().isInteractable();
    }

    private void denyVanillaPlacement(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
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
