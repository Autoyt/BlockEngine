package dev.auto.blockengine.runtime;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockDefinition.Movement;
import dev.auto.blockengine.api.event.BlockEngineGravityEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockRemovedEvent;
import dev.auto.blockengine.visibility.VisibilityManager;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public final class GravityManager {
    private static final double VANILLA_FALL_GRAVITY = 0.04D;
    private static final double VANILLA_FALL_DRAG = 0.98D;
    private static final int MAX_FALL_ANIMATION_TICKS = 80;
    private static final int CHECKS_PER_FLUSH = 512;
    private static final Set<BlockFace> HORIZONTAL_FACES = Set.of(
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    );
    private static final GravityManager instance = new GravityManager();
    private final Set<BlockLocationKey> movedThisTick = new HashSet<>();

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

    public void queueLoaded(@Nullable ChunkEngine.LoadedChunk chunk) {
        if (chunk == null) {
            return;
        }
        for (RuntimeBlockView block : chunk.blocks()) {
            if (gravityEnabled(block)) {
                queueGravityCheck(block.location());
            }
        }
        if (!gravityQueue.isEmpty()) {
            scheduleGravityDrainAfterFlush();
        }
    }

    public void clear() {
        movedThisTick.clear();
        gravityQueue.clear();
        queuedGravityChecks.clear();
        cascadeQueue.clear();
        queuedCascades.clear();
        gravityDrainScheduled = false;
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

        Block stopBlock = stopBlock(block);
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
        if (!BlockMover.canOccupy(target)) {
            return false;
        }
        if (breaksOnPartialSupport(customBlock, target)) {
            if (!BlockMover.canMove(block, customBlock, target, BlockAdapter.MoveCause.GRAVITY)) {
                return false;
            }
            List<ItemStack> drops = customBlock.storedBlock().dropsItem()
                    ? List.of(ItemManager.create(definition, customBlock.storedBlock().stateId()))
                    : List.of();
            if (!BlockRemover.removeWithDrops(block, customBlock, drops, Material.AIR, false,
                    BlockEngineBlockRemovedEvent.Reason.PLUGIN_REQUEST, target).removed()) {
                return false;
            }
        } else {
            // Persist the landing in the same operation as source removal. The animation
            // can then stop at any point without owning the only copy of the block.
            if (!BlockMover.move(block, customBlock, target, BlockAdapter.MoveCause.GRAVITY)) {
                return false;
            }

            RuntimeBlockView landed = ChunkEngine.getBlock(to);
            int duration = fallDuration(distance(from, to));
            if (landed != null) {
                ChunkEngine.afterFlush(() -> {
                    RuntimeBlockView current = ChunkEngine.getBlock(to);
                    if (current != null && current.storedBlock() == landed.storedBlock() && current.exposed()) {
                        VisibilityManager.getInstance().animateBlockMove(from, to, ItemManager.display(current), duration);
                    }
                });
            }
        }

        movedThisTick.add(to);
        queueCascadeAfterFlush(block);
        queueCascadeAfterFlush(target);
        return true;
    }

    static int fallDuration(double distance) {
        double speed = 0;
        double fallen = 0;
        int ticks = 0;
        do {
            speed = (speed + VANILLA_FALL_GRAVITY) * VANILLA_FALL_DRAG;
            fallen += speed;
            ticks++;
        } while (fallen < distance && ticks < MAX_FALL_ANIMATION_TICKS);
        return ticks;
    }

    private double distance(@NotNull BlockLocationKey from, @NotNull BlockLocationKey to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private Block stopBlock(@NotNull Block start) {
        Block stop = start;
        int minY = start.getWorld().getMinHeight();
        while (stop.getY() > minY) {
            Block below = stop.getRelative(BlockFace.DOWN);
            if (!BlockMover.canOccupy(below)) {
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
                && target.getY() > target.getWorld().getMinHeight()
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
        if (Main.getInstance().isEnabled() && queuedCascades.add(origin)) {
            cascadeQueue.add(origin);
            scheduleGravityDrainAfterFlush();
        }
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
        if (ChunkEngine.getBlock(key) != null && queuedGravityChecks.add(key)) {
            gravityQueue.add(key);
        }
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
        for (int i = 0; i < CHECKS_PER_FLUSH && !cascadeQueue.isEmpty(); i++) {
            BlockLocationKey origin = cascadeQueue.poll();
            queuedCascades.remove(origin);
            queueCascade(origin);
        }
        processGravityQueue();
        if (!gravityQueue.isEmpty() || !cascadeQueue.isEmpty()) {
            scheduleGravityDrainAfterFlush();
        }
    }

    private void processGravityQueue() {
        int processed = 0;
        while (processed < CHECKS_PER_FLUSH) {
            BlockLocationKey key = gravityQueue.poll();
            if (key == null) {
                break;
            }
            queuedGravityChecks.remove(key);
            processed++;

            Block block = block(key);
            if (block == null) {
                continue;
            }

            RuntimeBlockView customBlock = ChunkEngine.getBlock(key);
            if (customBlock == null || !gravityEnabled(customBlock)) {
                continue;
            }

            // A successful move already queues the neighbors of both ends for the next flush.
            check(block);
        }
    }

    private @Nullable Block block(@NotNull BlockLocationKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || key.y() < world.getMinHeight() || key.y() >= world.getMaxHeight()
                || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
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
}
