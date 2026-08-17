package dev.auto.blockengine.listeners;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.items.BlockEngineItemManager;
import dev.auto.blockengine.mining.MiningManager;
import dev.auto.blockengine.placement.BlockEngineBackingBlock;
import dev.auto.blockengine.placement.BlockEnginePlacementService;
import dev.auto.blockengine.placement.BlockEngineVanillaRules;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import dev.auto.blockengine.runtime.LoadedBlockEngineChunk;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.runtime.BlockEngineBlockContext;
import dev.auto.blockengine.runtime.BlockEngineBlockDataService;
import dev.auto.blockengine.runtime.BlockEngineBlockRemover;
import dev.auto.blockengine.runtime.BlockEngineChunkRuntime;
import dev.auto.blockengine.runtime.BlockEngineMutationBatcher;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.visibility.VisibilityService;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vehicle;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
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
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.HashSet;
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
    public void onMove(PlayerMoveEvent event) {
        MiningManager.updateAim(event.getPlayer());
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
        BlockEngineMutationBatcher.flushNow();
        lastPlacementTicks.remove(event.getPlayer().getUniqueId());
        MiningManager.stop(event.getPlayer());
        VisibilityService.cleanup(event.getPlayer());
    }

    @EventHandler
    public void onStartBreaking(BlockDamageEvent event) {
        final Player player = event.getPlayer();
        RuntimeBlockView block = BlockEngineChunkRuntime.getBlock(new BlockLocationKey(
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
        RuntimeBlockView block = BlockEngineChunkRuntime.getBlock(new BlockLocationKey(
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
    public void onExplode(EntityExplodeEvent event) {
        explode(event.getLocation(), event.blockList());
    }

    @EventHandler
    public void onExplode(BlockExplodeEvent event) {
        explode(event.getBlock().getLocation(), event.blockList());
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        BlockEnginePlacementService.place(event);
    }

    @EventHandler
    public void onPickBlock(PlayerPickBlockEvent event) {
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

        ItemStack stack = BlockEngineItemManager.create(definition, customBlock.storedBlock().stateId());
        pick(event.getPlayer(), stack, event.getTargetSlot());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }

        RuntimeBlockView block = block(event.getClickedBlock());
        if (block == null) {
            placeHeld(event);
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

        BlockEngineBlockContext context = BlockEngineBlockDataService.context(event.getClickedBlock(), block, event.getPlayer());
        if (context != null && context.adapter().onInteract(context, event.getPlayer())) {
            event.setCancelled(true);
            BlockEngineBlockDataService.save(event.getClickedBlock(), context);
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

        Set<BlockLocationKey> affected = affectedByExplosion(origin, radius(origin, vanillaBlocks));
        for (BlockLocationKey location : affected) {
            RuntimeBlockView customBlock = BlockEngineChunkRuntime.getBlock(location);
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
            BlockEngineBlockRemover.remove(block, customBlock, customBlock.storedBlock().dropsItem());
        }
    }

    private double radius(Location origin, List<Block> blocks) {
        double radius = 4.0;
        for (Block block : blocks) {
            radius = Math.max(radius, block.getLocation().add(0.5, 0.5, 0.5).distance(origin));
        }
        return radius + 1.0;
    }

    private Set<BlockLocationKey> affectedByExplosion(Location origin, double radius) {
        Set<BlockLocationKey> affected = new HashSet<>();
        World world = origin.getWorld();
        if (world == null) {
            return affected;
        }

        double power = Math.max(0.5, radius / 2.0);
        int samples = 16;
        for (int x = 0; x < samples; x++) {
            for (int y = 0; y < samples; y++) {
                for (int z = 0; z < samples; z++) {
                    if (x != 0 && x != samples - 1
                            && y != 0 && y != samples - 1
                            && z != 0 && z != samples - 1) {
                        continue;
                    }
                    ray(origin, world, affected, power, dir(x, samples), dir(y, samples), dir(z, samples));
                }
            }
        }
        return affected;
    }

    private void ray(
            Location origin,
            World world,
            Set<BlockLocationKey> affected,
            double explosionPower,
            double dx,
            double dy,
            double dz
    ) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= length;
        dy /= length;
        dz /= length;

        double power = explosionPower * (0.7 + Math.random() * 0.6);
        double x = origin.getX();
        double y = origin.getY();
        double z = origin.getZ();

        while (power > 0.0) {
            int blockX = floor(x);
            int blockY = floor(y);
            int blockZ = floor(z);
            Block block = world.getBlockAt(blockX, blockY, blockZ);
            if (protectedByWater(block)) {
                return;
            }

            BlockLocationKey location = new BlockLocationKey(world.getUID(), blockX, blockY, blockZ);
            RuntimeBlockView customBlock = BlockEngineChunkRuntime.getBlock(location);
            float resistance = customBlock == null
                    ? block.getType().getBlastResistance()
                    : Math.max(0.05f, customBlock.storedBlock().hardness());
            if (!block.getType().isAir() || customBlock != null) {
                power -= (resistance + 0.3f) * 0.3f;
            }

            if (power > 0.0) {
                affected.add(location);
            }

            x += dx * 0.3;
            y += dy * 0.3;
            z += dz * 0.3;
            power -= 0.225;
        }
    }

    private boolean protectedByWater(Block block) {
        return block.getType() == Material.WATER
                || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private double dir(int value, int samples) {
        return (value / (double) (samples - 1)) * 2.0 - 1.0;
    }

    private int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private RuntimeBlockView block(Block block) {
        return BlockEngineChunkRuntime.getBlock(new BlockLocationKey(
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
        String blockId = BlockEngineItemManager.blockId(item);
        if (blockId == null || !BlockEngineItemManager.placeable(item)) {
            return false;
        }

        Player player = event.getPlayer();
        String namespace = BlockEngineItemManager.namespace(item);
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
        String stateId = BlockEngineItemManager.stateId(item);
        BlockFace placedAgainst = event.getBlockFace().getOppositeFace();
        if (!canPlace(target, definition, stateId, player, placedAgainst, event.getHand())) {
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
        if (!BlockEnginePlacementService.place(target, definition, player, placedAgainst, stateId)) {
            return true;
        }
        lastPlacementTicks.put(playerId, tick);
        player.swingHand(event.getHand());

        if (player.getGameMode() != GameMode.CREATIVE && item != null) {
            item.subtract();
        }
        return true;
    }

    private boolean holdingPlaceableBlockEngineBlock(ItemStack item) {
        return BlockEngineItemManager.blockId(item) != null && BlockEngineItemManager.placeable(item);
    }

    private boolean canPlace(
            Block target,
            BlockDefinition definition,
            String stateId,
            Player player,
            BlockFace placedAgainst,
            EquipmentSlot hand
    ) {
        org.bukkit.block.data.BlockData placementData = BlockEngineVanillaRules.placementData(
                definition,
                stateId,
                player,
                placedAgainst
        );
        BlockCanBuildEvent buildEvent = new BlockCanBuildEvent(
                target,
                player,
                placementData,
                BlockEngineVanillaRules.canPlace(target, definition, stateId, player, placedAgainst),
                hand
        );
        Bukkit.getPluginManager().callEvent(buildEvent);
        return buildEvent.isBuildable() && !occupied(target, player);
    }

    private boolean occupied(Block target, Player player) {
        BoundingBox blockBox = new BoundingBox(
                target.getX(),
                target.getY(),
                target.getZ(),
                target.getX() + 1.0,
                target.getY() + 1.0,
                target.getZ() + 1.0
        );

        if (player.getBoundingBox().overlaps(blockBox)) {
            return true;
        }

        for (Entity entity : target.getWorld().getNearbyEntities(blockBox)) {
            if (!blocksPlacement(entity)) {
                continue;
            }
            if (entity.getBoundingBox().overlaps(blockBox)) {
                return true;
            }
        }
        return false;
    }

    private boolean blocksPlacement(Entity entity) {
        if (entity.isDead()) {
            return false;
        }
        if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return entity instanceof LivingEntity
                || entity instanceof Vehicle
                || entity.getType() == EntityType.ARMOR_STAND
                || entity.getType() == EntityType.END_CRYSTAL;
    }

    private void useHeldItem(PlayerInteractEvent event) {
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.ALLOW);
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
