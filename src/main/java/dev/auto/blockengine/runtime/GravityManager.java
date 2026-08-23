package dev.auto.blockengine.runtime;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.event.BlockEngineGravityEvent;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

public final class GravityManager {
    private static final GravityManager instance = new GravityManager();

    private GravityManager() {
    }

    public static @NotNull GravityManager getInstance() {
        return instance;
    }

    public boolean check(@NotNull Block block) {
        RuntimeBlockView customBlock = ChunkEngine.getBlock(location(block));
        if (customBlock == null || !gravityEnabled(customBlock)) {
            return false;
        }

        Block stopBlock = stopBlock(block, customBlock);
        if (stopBlock == null || stopBlock.equals(block)) {
            return false;
        }

        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null
                || !BlockMover.canMove(block, customBlock, stopBlock, BlockAdapter.MoveCause.GRAVITY)) {
            return false;
        }

        BlockEngineGravityEvent event = new BlockEngineGravityEvent(
                block,
                stopBlock,
                definition.apiDefinition(),
                customBlock.storedBlock().stateId()
        );
        return !BlockEngineEvents.callCancellable(event);
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

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
