package dev.auto.blockengine.api.blocks;

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

    /**
     * Returns the adapter that owns the placed block.
     *
     * @return owning block adapter
     */
    public final @NotNull BlockAdapter adapter() {
        return adapter;
    }

    /**
     * Returns mutable persisted data for the placed block.
     *
     * @return placed-block data
     */
    public final @NotNull BlockData data() {
        return data;
    }

    /**
     * Returns the full BlockEngine id for the placed block.
     *
     * @return full block id, such as {@code myplugin:lamp}
     */
    public final @NotNull String blockId() {
        return data.blockId();
    }

    /**
     * Returns the current state id for the placed block.
     *
     * @return state id
     */
    public final @NotNull String stateId() {
        return data.stateId();
    }

    /**
     * Changes the current BlockEngine block state.
     *
     * @param stateId new state id from the block definition
     */
    public final void stateId(@NotNull String stateId) {
        data.stateId(stateId);
    }

    /**
     * Convenience alias for changing the current BlockEngine blockstate.
     *
     * @param stateId new state id from the block definition
     */
    public final void setState(@NotNull String stateId) {
        data.stateId(stateId);
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
