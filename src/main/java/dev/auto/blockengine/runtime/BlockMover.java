package dev.auto.blockengine.runtime;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.display.DisplaySpec;
import dev.auto.blockengine.api.event.BlockEngineModificationEvent;
import dev.auto.blockengine.event.BlockEngineEvents;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BlockMover {
    private BlockMover() {
    }

    public static boolean canMove(
            @NotNull Block from,
            @NotNull RuntimeBlockView customBlock,
            @NotNull Block to,
            @NotNull BlockAdapter.MoveCause cause
    ) {
        if (!isCurrentSource(from, customBlock) || !canOccupy(to)) {
            return false;
        }

        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return false;
        }

        BlockContext context = BlockDataManager.getInstance().context(from, customBlock, null);
        return context != null && context.adapter().canMove(context, from, to, cause)
                && isCurrentSource(from, customBlock) && canOccupy(to);
    }

    public static boolean move(
            @NotNull Block from,
            @NotNull RuntimeBlockView customBlock,
            @NotNull Block to,
            @NotNull BlockAdapter.MoveCause cause
    ) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(customBlock, "customBlock");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(cause, "cause");

        if (!isCurrentSource(from, customBlock) || !canOccupy(to)) {
            return false;
        }

        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return false;
        }

        BlockContext context = BlockDataManager.getInstance().context(from, customBlock, null);
        if (context == null || !context.adapter().canMove(context, from, to, cause)
                || !isCurrentSource(from, customBlock) || !canOccupy(to)) {
            return false;
        }

        byte[] payload = definition.adapter().save(context.data());
        ChunkEngine.StoredBlock moved = movedBlock(from, customBlock.storedBlock(), to, context.data(), definition, payload);

        if (!isCurrentSource(from, customBlock) || !canOccupy(to)) {
            return false;
        }
        ChunkEngine.Data sourceData = ChunkEngine.data(from.getChunk());
        ChunkEngine.Data targetData = ChunkEngine.data(to.getChunk());
        sourceData.removeBlock(from.getX() & 15, from.getY(), from.getZ() & 15);
        targetData.setBlock(moved);

        from.setType(Material.AIR, false);
        to.setType(Main.getBackingBlock(), false);
        ChunkEngine.changed(from);
        ChunkEngine.changed(to);
        BlockEngineEvents.modification(
                BlockEngineModificationEvent.Action.REMOVE_CUSTOM_BLOCK,
                from,
                customBlock.storedBlock().blockId(),
                customBlock.storedBlock().stateId(),
                null,
                null
        );
        RuntimeBlockView landed = ChunkEngine.getBlock(location(to));
        if (landed == null || landed.storedBlock().data() != moved.data()) {
            return true;
        }
        BlockIntegrityManager.getInstance().callEvent(
                BlockEngineModificationEvent.Action.SET_CUSTOM_BLOCK,
                to,
                null,
                null,
                moved.blockId(),
                moved.stateId()
        );

        // Notify the adapter at the destination after both mutations are visible.
        // A modification listener may already have replaced the landed block.
        BlockContext landedContext = new BlockContext(definition.adapter(), context.data(), to, null, moved);
        if (landedContext.matches(ChunkEngine.getBlock(location(to)))) {
            ChunkEngine.SimpleBlockData before = ChunkEngine.SimpleBlockData.copyOf(landedContext.data());
            try {
                definition.adapter().onMove(landedContext, from, to, cause);
                BlockDataManager.getInstance().saveIfChanged(to, landedContext, landed, before);
            } catch (RuntimeException exception) {
                Main.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                        "Movement callback failed for " + definition.id() + " at " + location(to), exception);
            }
        }
        return true;
    }

    public static boolean canOccupy(@NotNull Block target) {
        if (target.getY() < target.getWorld().getMinHeight() || target.getY() >= target.getWorld().getMaxHeight()
                || !target.getWorld().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)
                || ChunkEngine.getBlock(location(target)) != null) {
            return false;
        }
        return target.getType().isAir() || target.isReplaceable() || target.isLiquid();
    }

    private static boolean isCurrentSource(@NotNull Block from, @NotNull RuntimeBlockView expected) {
        RuntimeBlockView current = ChunkEngine.getBlock(location(from));
        return current != null && current.storedBlock() == expected.storedBlock()
                && from.getType() == Main.getBackingBlock();
    }

    public static @NotNull ChunkEngine.StoredBlock movedBlock(
            @NotNull Block from,
            @NotNull ChunkEngine.StoredBlock source,
            @NotNull Block to,
            @NotNull BlockData data,
            @NotNull BlockDefinition definition,
            byte @Nullable [] payload
    ) {
        return ChunkEngine.StoredBlock.from(
                to.getX() & 15,
                to.getY(),
                to.getZ() & 15,
                data,
                definition.apiDefinition(),
                payload,
                movedDisplays(from, source.displays(), to),
                source.orientation()
        );
    }

    private static @NotNull List<ChunkEngine.StoredDisplay> movedDisplays(
            @NotNull Block from,
            @NotNull List<ChunkEngine.StoredDisplay> displays,
            @NotNull Block to
    ) {
        if (displays.isEmpty()) {
            return List.of();
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        BlockLocationKey owner = location(to);
        List<ChunkEngine.StoredDisplay> moved = new ArrayList<>(displays.size());
        for (ChunkEngine.StoredDisplay display : displays) {
            DisplaySpec spec = display.spec();
            DisplaySpec shifted = spec.toBuilder()
                    .location(to.getWorld().getUID(), spec.x() + dx, spec.y() + dy, spec.z() + dz, spec.yaw(), spec.pitch())
                    .build();
            moved.add(new ChunkEngine.StoredDisplay(
                    display.id(),
                    display.persistence(),
                    owner,
                    display.ownerKey(),
                    shifted
            ));
        }
        return moved;
    }

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

}
