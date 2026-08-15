package dev.auto.turtle.visibility;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public record VisibilityConfig(
        boolean enabled,
        int chunkRadius,
        boolean clampToServerViewDistance,
        boolean loadedChunksOnly,
        boolean recycleDisplays,
        boolean exposureEnabled,
        boolean treatLiquidAsExposed,
        boolean treatPassableAsExposed,
        boolean treatNonSolidAsExposed
) {
    public static @NotNull VisibilityConfig load(@NotNull JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new VisibilityConfig(
                config.getBoolean("visibility.enabled", true),
                Math.max(0, config.getInt("visibility.chunks.radius", 5)),
                config.getBoolean("visibility.chunks.clamp-to-server-view-distance", true),
                config.getBoolean("visibility.chunks.loaded-chunks-only", true),
                config.getBoolean("visibility.entities.recycle", true),
                config.getBoolean("visibility.exposure.enabled", true),
                config.getBoolean("visibility.exposure.treat-liquid-as-exposed", true),
                config.getBoolean("visibility.exposure.treat-passable-as-exposed", true),
                config.getBoolean("visibility.exposure.treat-non-solid-as-exposed", true)
        );
    }

    public int effectiveChunkRadius() {
        if (!clampToServerViewDistance) {
            return chunkRadius;
        }
        return Math.min(chunkRadius, Bukkit.getViewDistance());
    }
}
