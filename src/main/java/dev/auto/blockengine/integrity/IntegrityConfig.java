package dev.auto.blockengine.integrity;

import dev.auto.blockengine.Main;
import org.jetbrains.annotations.NotNull;

public record IntegrityConfig(
        boolean reconcileOnChunkLoad,
        boolean reconcileOnInteraction,
        boolean postTickVerification,
        boolean listenToBlockUpdates,
        int chunksPerTick
) {
    public static @NotNull IntegrityConfig load(@NotNull Main plugin) {
        plugin.getConfig().addDefault("integrity.reconcile-on-chunk-load", true);
        plugin.getConfig().addDefault("integrity.reconcile-on-interaction", true);
        plugin.getConfig().addDefault("integrity.post-tick-verification", true);
        plugin.getConfig().addDefault("integrity.listen-to-block-updates", true);
        plugin.getConfig().addDefault("integrity.chunks-per-tick", 2);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        return new IntegrityConfig(
                plugin.getConfig().getBoolean("integrity.reconcile-on-chunk-load", true),
                plugin.getConfig().getBoolean("integrity.reconcile-on-interaction", true),
                plugin.getConfig().getBoolean("integrity.post-tick-verification", true),
                plugin.getConfig().getBoolean("integrity.listen-to-block-updates", true),
                Math.max(1, plugin.getConfig().getInt("integrity.chunks-per-tick", 2))
        );
    }
}
