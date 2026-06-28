package dev.auto.turtle.api.blocks;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Safe adapter-facing view of a placed block interaction.
 *
 * <p>The runtime is expected to provide a concrete implementation that knows
 * how to query the surrounding world and mutate the placed block state through
 * approved operations only.</p>
 */
public abstract class BlockContext {
    private final @NotNull BlockAdapter adapter;
    private final @NotNull BlockData data;

    protected BlockContext(@NotNull BlockAdapter adapter, @NotNull BlockData data) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.data = Objects.requireNonNull(data, "data");
    }

    public final @NotNull BlockAdapter adapter() {
        return adapter;
    }

    public final @NotNull BlockDefinition definition() {
        return adapter.definition();
    }

    public final @NotNull BlockData data() {
        return data;
    }

    public final @NotNull String blockId() {
        return data.blockId();
    }

    public final @NotNull String stateId() {
        return data.stateId();
    }

    public final void stateId(@NotNull String stateId) {
        data.stateId(stateId);
    }

    /**
     * Convenience alias for changing the current Turtle blockstate.
     */
    public final void setState(@NotNull String stateId) {
        data.stateId(stateId);
    }

    /**
     * @return the effective light level at the current block position
     */
    public abstract int lightLevel();

    /**
     * @return the effective incoming redstone power amount at the current block position
     */
    public abstract int powerLevel();

    /**
     * @return true if the block is receiving any redstone power
     */
    public boolean isPowered() {
        return powerLevel() > 0;
    }

    /**
     * @return the player associated with the current interaction, if any
     */
    public abstract @Nullable Player player();

    /**
     * @return a read-only view of the block at the given relative offset
     */
    public abstract @NotNull BlockView relative(int dx, int dy, int dz);

    /**
     * @return a read-only view of the neighboring block on the given face
     */
    public abstract @NotNull BlockView neighbor(@NotNull BlockFace face);

    /**
     * @return read-only views of all six neighboring blocks
     */
    public abstract @NotNull List<BlockView> neighbors();

    /**
     * @return read-only views of the four horizontal neighboring blocks
     */
    public abstract @NotNull List<BlockView> horizontalNeighbors();
}
