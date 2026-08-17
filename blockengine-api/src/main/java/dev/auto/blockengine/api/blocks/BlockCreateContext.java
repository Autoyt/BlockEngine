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
    @NotNull Location location();

    @Nullable Player player();

    @Nullable BlockFace placedAgainst();

    @NotNull String blockId();

    @NotNull BlockData createData();
}
