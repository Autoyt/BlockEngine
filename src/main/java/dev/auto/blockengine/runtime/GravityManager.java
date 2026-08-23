package dev.auto.blockengine.runtime;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockDefinition.Movement;
import dev.auto.blockengine.api.event.BlockEngineGravityEvent;
import dev.auto.blockengine.entity.PacketEntityManager;
import dev.auto.blockengine.entity.VirtualItemDisplay;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public final class GravityManager {
    private static final double VANILLA_FALL_GRAVITY = 0.04D;
    private static final double VANILLA_FALL_DRAG = 0.98D;
    private static final int MAX_FALL_ANIMATION_TICKS = 80;
    private static final int MAX_QUEUED_GRAVITY_CHANGES = 100_000;
    private static final Set<BlockFace> HORIZONTAL_FACES = Set.of(
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    );
    private static final GravityManager instance = new GravityManager();
    private final Set<BlockLocationKey> movedThisTick = new HashSet<>();
    private final Map<ColumnKey, Set<Integer>> claimedLandingHeights = new HashMap<>();
    private final Queue<BlockLocationKey> gravityQueue = new ArrayDeque<>();
    private final Set<BlockLocationKey> queuedGravityChecks = new HashSet<>();
    private final Queue<BlockLocationKey> cascadeQueue = new ArrayDeque<>();
    private final Set<BlockLocationKey> queuedCascades = new HashSet<>();
    private int movementTick = Integer.MIN_VALUE;
    private boolean gravityDrainScheduled;

    private GravityManager() {
    }

    public static @NotNull GravityManager getInstance() {
        return instance;
    }

    public void queueCascadeAfterFlush(@NotNull Block origin) {
        queueCascadeAfterFlush(location(origin));
    }

    public boolean check(@NotNull Block block) {
        int tick = Bukkit.getCurrentTick();
        if (tick != movementTick) {
            movedThisTick.clear();
            movementTick = tick;
        }
        if (movedThisTick.contains(location(block))) {
            return false;
        }

        RuntimeBlockView customBlock = ChunkEngine.getBlock(location(block));
        if (customBlock == null || !gravityEnabled(customBlock)) {
            return false;
        }

        Block stopBlock = stopBlock(block, customBlock);
        if (stopBlock == null || stopBlock.equals(block)) {
            return false;
        }

        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return false;
        }

        BlockEngineGravityEvent event = new BlockEngineGravityEvent(
                block,
                stopBlock,
                definition.apiDefinition(),
                customBlock.storedBlock().stateId()
        );
        if (BlockEngineEvents.callCancellable(event)) {
            return false;
        }

        Block target = event.stopBlock();
        if (target.equals(block)
                || !target.getWorld().equals(block.getWorld())) {
            return false;
        }

        BlockLocationKey from = location(block);
        BlockLocationKey to = location(target);
        ItemStack displayItem = ItemManager.display(customBlock);
        boolean breaksOnPartialSupport = breaksOnPartialSupport(customBlock, target);
        if (!BlockMover.canMove(block, customBlock, target, BlockAdapter.MoveCause.GRAVITY)) {
            return false;
        }
        if (!claimLanding(to)) {
            return false;
        }

        if (breaksOnPartialSupport) {
            if (!BlockRemover.remove(block, customBlock, false)) {
                releaseLanding(to);
                return false;
            }
            if (customBlock.storedBlock().dropsItem()) {
                target.getWorld().dropItemNaturally(
                        target.getLocation().add(0.5, 0.5, 0.5),
                        ItemManager.create(definition, customBlock.storedBlock().stateId())
                );
            }
            movedThisTick.add(to);
            releaseLanding(to);
            queueCascadeAfterFlush(block);
            queueCascadeAfterFlush(target);
            return true;
        }

        BlockMover.PendingMove pendingMove = BlockMover.beginMove(block, customBlock, target, BlockAdapter.MoveCause.GRAVITY);
        if (pendingMove == null) {
            releaseLanding(to);
            return false;
        }

        movedThisTick.add(to);
        queueCascadeAfterFlush(block);
        ChunkEngine.afterFlush(() -> animateFallingBlock(pendingMove, from, to, displayItem, definition));
        return true;
    }

    private void animateFallingBlock(
            @NotNull BlockMover.PendingMove pendingMove,
            @NotNull BlockLocationKey from,
            @NotNull BlockLocationKey to,
            @NotNull ItemStack displayItem,
            @NotNull BlockDefinition definition
    ) {
        if (Bukkit.getWorld(from.worldId()) == null) {
            finishFallingBlock(pendingMove, null, definition);
            return;
        }
        VirtualItemDisplay display = fallingDisplay(from, to, displayItem);
        Collection<? extends org.bukkit.entity.Player> viewers = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getWorld().getUID().equals(from.worldId()))
                .toList();
        display.spawnFor(viewers);

        FallingAnimation animation = new FallingAnimation(pendingMove, from, to, display, definition, viewers);
        animation.task(Bukkit.getScheduler().runTaskTimer(Main.getInstance(), animation, 1L, 1L));
    }

    private void finishFallingBlock(
            @NotNull BlockMover.PendingMove pendingMove,
            @Nullable VirtualItemDisplay display,
            @NotNull BlockDefinition definition
    ) {
        boolean placed = BlockMover.finishMove(pendingMove);
        releaseLanding(location(pendingMove.to()));
        if (!placed) {
            pendingMove.to().getWorld().dropItemNaturally(
                    pendingMove.to().getLocation().add(0.5, 0.5, 0.5),
                    ItemManager.create(definition, pendingMove.moved().stateId())
            );
        }

        ChunkEngine.afterFlush(() -> {
            if (display != null) {
                display.destroyForAll();
                PacketEntityManager.release(display);
            }
        });
        queueCascadeAfterFlush(pendingMove.from());
        queueCascadeAfterFlush(pendingMove.to());
    }

    private @NotNull VirtualItemDisplay fallingDisplay(
            @NotNull BlockLocationKey from,
            @NotNull BlockLocationKey to,
            @NotNull ItemStack displayItem
    ) {
        Location location = new Location(
                Bukkit.getWorld(from.worldId()),
                from.x() + 0.5,
                from.y() + 0.5,
                from.z() + 0.5,
                0.0f,
                0.0f
        );
        float viewRange = (float) Math.clamp(distance(from, to) + 2.0D, 2.0D, 64.0D);
        return PacketEntityManager.itemDisplay()
                .location(location)
                .itemStack(displayItem)
                .displayContext(VirtualItemDisplay.DISPLAY_CONTEXT_FIXED)
                .scale(2.0f, 2.0f, 2.0f)
                .brightness(15, 15)
                .viewRange(viewRange)
                .shadowRadius(0.0f)
                .shadowStrength(0.0f)
                .transformationInterpolationDelay(0)
                .transformationInterpolationDuration(1);
    }

    private double distance(@NotNull BlockLocationKey from, @NotNull BlockLocationKey to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private Block stopBlock(@NotNull Block start, @NotNull RuntimeBlockView customBlock) {
        Block stop = start;
        int minY = start.getWorld().getMinHeight();
        while (stop.getY() > minY) {
            Block below = stop.getRelative(BlockFace.DOWN);
            if (!BlockMover.canOccupy(below) || claimedLanding(location(below))) {
                break;
            }
            stop = below;
        }
        return stop == start ? null : stop;
    }

    private boolean gravityEnabled(@NotNull RuntimeBlockView customBlock) {
        Movement movement = movement(customBlock);
        return movement != null && movement.gravity();
    }

    private boolean breaksOnPartialSupport(@NotNull RuntimeBlockView customBlock, @NotNull Block target) {
        Movement movement = movement(customBlock);
        return movement != null
                && movement.gravityBreaksOnPartialBlock()
                && !claimedLanding(location(target.getRelative(BlockFace.DOWN)))
                && partialCollision(target.getRelative(BlockFace.DOWN));
    }

    private Movement movement(@NotNull RuntimeBlockView customBlock) {
        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return null;
        }

        try {
            return definition.apiDefinition()
                    .state(customBlock.storedBlock().stateId())
                    .movement();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean partialCollision(@NotNull Block block) {
        if (block.getType().isAir() || block.isLiquid()) {
            return false;
        }

        return !isFullBlockCollision(block.getCollisionShape().getBoundingBoxes());
    }

    static boolean isFullBlockCollision(@NotNull Collection<BoundingBox> boxes) {
        if (boxes.size() != 1) {
            return false;
        }

        BoundingBox box = boxes.iterator().next();
        double epsilon = 1.0E-4;
        // VoxelShape boxes are local to the block, unlike Block#getBoundingBox.
        return Math.abs(box.getMinX()) <= epsilon
                && Math.abs(box.getMinY()) <= epsilon
                && Math.abs(box.getMinZ()) <= epsilon
                && Math.abs(box.getMaxX() - 1.0) <= epsilon
                && Math.abs(box.getMaxY() - 1.0) <= epsilon
                && Math.abs(box.getMaxZ() - 1.0) <= epsilon;
    }

    private void queueCascadeAfterFlush(@NotNull BlockLocationKey origin) {
        if (!queueCascadeSeed(origin)) {
            return;
        }
        scheduleGravityDrainAfterFlush();
    }

    private boolean queueCascadeSeed(@NotNull BlockLocationKey origin) {
        if (queuedCascades.contains(origin)) {
            return true;
        }
        if (queuedCascades.size() >= MAX_QUEUED_GRAVITY_CHANGES) {
            return false;
        }
        queuedCascades.add(origin);
        cascadeQueue.add(origin);
        return true;
    }

    private void queueCascade(@NotNull BlockLocationKey origin) {
        queueColumnSeed(origin);
        for (BlockFace face : HORIZONTAL_FACES) {
            queueColumnSeed(relative(origin, face));
        }
    }

    private void queueColumnSeed(@NotNull BlockLocationKey origin) {
        queueGravityCheck(origin);
        queueGravityCheck(relative(origin, BlockFace.UP));
        queueGravityCheck(relative(origin, BlockFace.DOWN));
    }

    private void queueGravityCheck(@NotNull BlockLocationKey key) {
        if (queuedGravityChecks.contains(key)) {
            return;
        }
        if (queuedGravityChecks.size() >= MAX_QUEUED_GRAVITY_CHANGES) {
            return;
        }
        queuedGravityChecks.add(key);
        gravityQueue.add(key);
    }

    private void scheduleGravityDrainAfterFlush() {
        if (gravityDrainScheduled) {
            return;
        }
        gravityDrainScheduled = true;
        ChunkEngine.afterFlush(this::processGravityDrain);
    }

    private void processGravityDrain() {
        gravityDrainScheduled = false;
        while (!cascadeQueue.isEmpty()) {
            BlockLocationKey origin = cascadeQueue.poll();
            queuedCascades.remove(origin);
            queueCascade(origin);
        }
        processGravityQueue();
    }

    private void processGravityQueue() {
        int processed = 0;
        Set<BlockLocationKey> processedThisPass = new HashSet<>();
        while (processed < MAX_QUEUED_GRAVITY_CHANGES) {
            BlockLocationKey key = gravityQueue.poll();
            if (key == null) {
                break;
            }
            queuedGravityChecks.remove(key);
            if (!processedThisPass.add(key)) {
                continue;
            }
            processed++;

            Block block = block(key);
            if (block == null) {
                continue;
            }

            RuntimeBlockView customBlock = ChunkEngine.getBlock(key);
            if (customBlock == null || !gravityEnabled(customBlock)) {
                continue;
            }

            check(block);
            BlockLocationKey above = relative(key, BlockFace.UP);
            BlockLocationKey below = relative(key, BlockFace.DOWN);
            if (!processedThisPass.contains(above)) {
                queueGravityCheck(above);
            }
            if (!processedThisPass.contains(below)) {
                queueGravityCheck(below);
            }
        }
        if (!gravityQueue.isEmpty()) {
            gravityQueue.clear();
            queuedGravityChecks.clear();
            Main.getInstance().getLogger().warning(
                    "Gravity queue hit the hard limit of " + MAX_QUEUED_GRAVITY_CHANGES + " checks; discarded remaining gravity work."
            );
        }
    }

    private @Nullable Block block(@NotNull BlockLocationKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
            return null;
        }
        return world.getBlockAt(key.x(), key.y(), key.z());
    }

    private @NotNull BlockLocationKey relative(@NotNull BlockLocationKey key, @NotNull BlockFace face) {
        return new BlockLocationKey(
                key.worldId(),
                key.x() + face.getModX(),
                key.y() + face.getModY(),
                key.z() + face.getModZ()
        );
    }

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private boolean claimLanding(@NotNull BlockLocationKey key) {
        return claimedLandingHeights
                .computeIfAbsent(ColumnKey.from(key), ignored -> new HashSet<>())
                .add(key.y());
    }

    private void releaseLanding(@NotNull BlockLocationKey key) {
        ColumnKey column = ColumnKey.from(key);
        Set<Integer> heights = claimedLandingHeights.get(column);
        if (heights == null) {
            return;
        }
        heights.remove(key.y());
        if (heights.isEmpty()) {
            claimedLandingHeights.remove(column);
        }
    }

    private boolean claimedLanding(@NotNull BlockLocationKey key) {
        Set<Integer> heights = claimedLandingHeights.get(ColumnKey.from(key));
        return heights != null && heights.contains(key.y());
    }

    private record ColumnKey(
            @NotNull UUID worldId,
            int x,
            int z
    ) {
        private static @NotNull ColumnKey from(@NotNull BlockLocationKey key) {
            return new ColumnKey(key.worldId(), key.x(), key.z());
        }
    }

    private final class FallingAnimation implements Runnable {
        private final @NotNull BlockMover.PendingMove pendingMove;
        private final @NotNull BlockLocationKey from;
        private final @NotNull BlockLocationKey to;
        private final @NotNull VirtualItemDisplay display;
        private final @NotNull BlockDefinition definition;
        private final @NotNull Collection<? extends org.bukkit.entity.Player> viewers;
        private final double totalDistance;
        private double velocity;
        private double fallen;
        private int ticks;
        private BukkitTask task;

        private FallingAnimation(
                @NotNull BlockMover.PendingMove pendingMove,
                @NotNull BlockLocationKey from,
                @NotNull BlockLocationKey to,
                @NotNull VirtualItemDisplay display,
                @NotNull BlockDefinition definition,
                @NotNull Collection<? extends org.bukkit.entity.Player> viewers
        ) {
            this.pendingMove = pendingMove;
            this.from = from;
            this.to = to;
            this.display = display;
            this.definition = definition;
            this.viewers = viewers;
            this.totalDistance = Math.max(1.0D, distance(from, to));
        }

        private void task(@NotNull BukkitTask task) {
            this.task = task;
        }

        @Override
        public void run() {
            ticks++;
            velocity = (velocity - VANILLA_FALL_GRAVITY) * VANILLA_FALL_DRAG;
            fallen = Math.min(totalDistance, fallen + -velocity);
            double progress = Math.clamp(fallen / totalDistance, 0.0D, 1.0D);

            display.translation(
                    (float) ((to.x() - from.x()) * progress),
                    (float) ((to.y() - from.y()) * progress),
                    (float) ((to.z() - from.z()) * progress)
            );
            display.transformationInterpolationDuration(1);
            display.updateMetadataFor(viewers);

            if (progress >= 1.0D || ticks >= MAX_FALL_ANIMATION_TICKS) {
                task.cancel();
                finishFallingBlock(pendingMove, display, definition);
            }
        }
    }
}
