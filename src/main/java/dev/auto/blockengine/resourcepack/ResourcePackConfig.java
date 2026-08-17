package dev.auto.blockengine.resourcepack;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public record ResourcePackConfig(
        boolean enabled,
        boolean generateOnStartup,
        boolean hostingEnabled,
        @NotNull String host,
        int port,
        @NotNull String publicUrl,
        @NotNull String fileName,
        boolean sendOnJoin,
        boolean required,
        @NotNull String prompt
) {
    public static @NotNull ResourcePackConfig load(@NotNull JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new ResourcePackConfig(
                config.getBoolean("resource-pack.enabled", true),
                config.getBoolean("resource-pack.generate-on-startup", true),
                config.getBoolean("resource-pack.hosting.enabled", true),
                config.getString("resource-pack.hosting.host", "0.0.0.0"),
                Math.clamp(config.getInt("resource-pack.hosting.port", 8123), 1, 65535),
                config.getString("resource-pack.hosting.public-url", ""),
                config.getString("resource-pack.hosting.file-name", "blockengine-resource-pack.zip"),
                config.getBoolean("resource-pack.send.on-join", true),
                config.getBoolean("resource-pack.send.required", false),
                config.getString("resource-pack.send.prompt", "This server uses BlockEngine custom block resources.")
        );
    }
}
