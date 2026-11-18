package dev.auto.blockengine.api.inspection;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Read-only inspection on the server thread. It does not load chunks or run adapter callbacks. */
public interface BlockInspectionService {
    @NotNull BlockInspection inspect(@NotNull Block block);

    boolean isWand(@Nullable ItemStack item);
}
