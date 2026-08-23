package dev.auto.blockengine.runtime;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockDefinition.Movement;
import dev.auto.blockengine.api.event.BlockEngineGravityEvent;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.visibility.VisibilityManager;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class GravityManager {
    private static final int FALL_ANIMATION_TICKS = 8;
    private static final GravityManager instance = new GravityManager();
    private final Set<BlockLocationKey> movedThisTick = new HashSet<>();
    private int movementTick = Integer.MIN_VALUE;

    private GravityManager() {
    }

    public static @NotNull GravityManager getInstance() {
        return instance;
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

        if (breaksOnPartialSupport) {
            if (!BlockRemover.remove(block, customBlock, false)) {
                return false;
            }
            if (customBlock.storedBlock().dropsItem()) {
                target.getWorld().dropItemNaturally(
                        target.getLocation().add(0.5, 0.5, 0.5),
                        ItemManager.create(definition, customBlock.storedBlock().stateId())
                );
            }
            movedThisTick.add(to);
            updateLocalGravity(block);
            updateLocalGravity(target);
            return true;
        }

        if (!BlockMover.move(block, customBlock, target, BlockAdapter.MoveCause.GRAVITY)) {
            return false;
        }

        movedThisTick.add(to);
        ChunkEngine.afterFlush(() -> VisibilityManager.getInstance()
                .animateBlockMove(from, to, displayItem, FALL_ANIMATION_TICKS));
        updateLocalGravity(block);
        updateLocalGravity(target);
        return true;
    }

    private Block stopBlock(@NotNull Block start, @NotNull RuntimeBlockView customBlock) {
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

    private void updateLocalGravity(@NotNull Block origin) {
        BlockUpdates.update(origin);
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            check(origin);
            check(origin.getRelative(BlockFace.UP));
            for (BlockFace face : BlockFace.values()) {
                if (!face.isCartesian()) {
                    continue;
                }
                check(origin.getRelative(face));
                check(origin.getRelative(face).getRelative(BlockFace.UP));
            }
        });
    }

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
