package dev.auto.blockengine.api.blocks;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of a world block used by adapter queries.
 *
 * <p>This keeps neighbor inspection safe while still giving adapters enough
 * information to make placement, support, and environment decisions.</p>
 */
public interface BlockView {
    /**
     * Returns the block x coordinate.
     *
     * @return x coordinate
     */
    int x();

    /**
     * Returns the block y coordinate.
     *
     * @return y coordinate
     */
    int y();

    /**
     * Returns the block z coordinate.
     *
     * @return z coordinate
     */
    int z();

    /**
     * Returns the current vanilla material at this position.
     *
     * @return Bukkit material
     */
    @NotNull Material material();

    /**
     * Returns whether the block is air.
     *
     * @return true if this view represents air
     */
    boolean isAir();

    /**
     * Returns whether the block is solid according to Bukkit material data.
     *
     * @return true if the material is solid
     */
    boolean isSolid();

    /**
     * Returns whether the block is liquid according to Bukkit material data.
     *
     * @return true if the material is liquid
     */
    boolean isLiquid();

    /**
     * Returns whether this position can be replaced by placement logic.
     *
     * @return true if the block is replaceable
     */
    boolean isReplaceable();

    /**
     * Returns whether entities can pass through this block.
     *
     * @return true if the block is passable
     */
    boolean isPassable();

    /**
     * Returns whether BlockEngine has a custom block record at this position.
     *
     * @return true if this is a BlockEngine custom block
     */
    boolean isBlockEngineBlock();

    /**
     * Returns the custom block id at this position, if any.
     *
     * @return full BlockEngine block id, or null
     */
    @Nullable String blockEngineBlockId();

    /**
     * Returns the custom block state id at this position, if any.
     *
     * @return BlockEngine state id, or null
     */
    @Nullable String blockEngineStateId();
}
