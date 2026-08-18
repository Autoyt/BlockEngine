package dev.auto.blockengine.placement;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public final class BlockEngineBackingBlock {
    private static final @NotNull Material MATERIAL = Material.BARRIER;

    private BlockEngineBackingBlock() {
    }

    public static @NotNull Material material() {
        return MATERIAL;
    }

    public static @NotNull String assetName() {
        return "barrier";
    }
}
