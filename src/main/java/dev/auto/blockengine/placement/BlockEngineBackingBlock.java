package dev.auto.blockengine.placement;

import dev.auto.blockengine.Main;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public final class BlockEngineBackingBlock {
    private static final @NotNull Material DEFAULT_MATERIAL = Material.BARRIER;

    private BlockEngineBackingBlock() {
    }

    public static @NotNull Material material() {
        String configured = Main.getInstance().getConfig().getString("custom-blocks.backing-block", DEFAULT_MATERIAL.name());
        Material material = configured == null ? null : Material.matchMaterial(configured);
        if (material == null || !material.isBlock() || !material.isItem()) {
            return DEFAULT_MATERIAL;
        }
        return material;
    }

    public static @NotNull String assetName() {
        return material().name().toLowerCase(java.util.Locale.ROOT);
    }
}
