package dev.auto.blockengine.api.blocks;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of a world block used by adapter queries.
 *
 * <p>This keeps neighbor inspection safe while still giving adapters enough
 * information to make placement, support, redstone, and environment decisions.</p>
 */
public interface BlockView {
    int x();

    int y();

    int z();

    @NotNull Material material();

    boolean isAir();

    boolean isSolid();

    boolean isLiquid();

    boolean isReplaceable();

    boolean isPassable();

    boolean isBlockEngineBlock();

    @Nullable String blockEngineBlockId();

    @Nullable String blockEngineStateId();
}
