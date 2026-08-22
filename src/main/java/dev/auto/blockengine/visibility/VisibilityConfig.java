package dev.auto.blockengine.visibility;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public record VisibilityConfig(
        boolean enabled,
        int chunkRadius
) {
    private static final boolean CLAMP_TO_SERVER_VIEW_DISTANCE = true;
    private static final boolean LOADED_CHUNKS_ONLY = true;
    private static final boolean RECYCLE_DISPLAYS = true;
    private static final boolean EXPOSURE_ENABLED = true;
    private static final boolean TREAT_LIQUID_AS_EXPOSED = true;
    private static final boolean TREAT_PASSABLE_AS_EXPOSED = true;
    private static final boolean TREAT_NON_SOLID_AS_EXPOSED = true;

    public static @NotNull VisibilityConfig load(@NotNull JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new VisibilityConfig(
                config.getBoolean("visibility.enabled", true),
                Math.max(0, radius(config))
        );
    }

    public int effectiveChunkRadius() {
        return Math.min(chunkRadius, Bukkit.getViewDistance());
    }

    public boolean loadedChunksOnly() {
        return LOADED_CHUNKS_ONLY;
    }

    public boolean recycleDisplays() {
        return RECYCLE_DISPLAYS;
    }

    public boolean exposureEnabled() {
        return EXPOSURE_ENABLED;
    }

    public boolean treatLiquidAsExposed() {
        return TREAT_LIQUID_AS_EXPOSED;
    }

    public boolean treatPassableAsExposed() {
        return TREAT_PASSABLE_AS_EXPOSED;
    }

    public boolean treatNonSolidAsExposed() {
        return TREAT_NON_SOLID_AS_EXPOSED;
    }

    private static int radius(@NotNull FileConfiguration config) {
        if (config.contains("visibility.chunk-radius")) {
            return config.getInt("visibility.chunk-radius", 5);
        }
        return config.getInt("visibility.chunks.radius", 5);
    }
}
