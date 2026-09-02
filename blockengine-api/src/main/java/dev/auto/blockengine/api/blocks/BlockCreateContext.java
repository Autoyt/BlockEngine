package dev.auto.blockengine.api.blocks;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context passed to adapters when BlockEngine creates fresh placed-block data.
 */
public interface BlockCreateContext {
    /**
     * Returns the placement location for the new custom block.
     *
     * @return block location being placed
     */
    @NotNull Location location();

    /**
     * Returns the player responsible for placement, if one exists.
     *
     * @return placing player, or null for API/plugin placement
     */
    @Nullable Player player();

    /**
     * Returns the face this block was placed against, if known.
     *
     * @return placed-against face, or null
     */
    @Nullable BlockFace placedAgainst();

    /**
     * Returns the full custom block id being created.
     *
     * @return full block id
     */
    @NotNull String blockId();

    /**
     * Creates a fresh mutable data container for this placement.
     *
     * <p>Adapters normally call this from
     * {@link BlockAdapter#createDefaultData(BlockCreateContext)}, modify the
     * returned data, and return it to BlockEngine.</p>
     *
     * @return new block data container
     */
    @NotNull BlockData createData();
}
