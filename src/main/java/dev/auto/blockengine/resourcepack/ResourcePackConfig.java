package dev.auto.blockengine.resourcepack;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public record ResourcePackConfig(
        boolean enabled,
        @NotNull String host,
        int port,
        @NotNull String publicUrl,
        @NotNull String fileName,
        boolean required,
        @NotNull String prompt
) {
    private static final boolean GENERATE_ON_STARTUP = true;
    private static final boolean HOSTING_ENABLED = true;
    private static final boolean SEND_ON_JOIN = true;
    private static final @NotNull String DEFAULT_HOST = "0.0.0.0";
    private static final @NotNull String DEFAULT_FILE_NAME = "blockengine-resource-pack.zip";
    private static final @NotNull String DEFAULT_PROMPT = "This server uses BlockEngine custom block resources.";

    public static @NotNull ResourcePackConfig load(@NotNull JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new ResourcePackConfig(
                config.getBoolean("resource-pack.enabled", true),
                string(config, "resource-pack.host", "resource-pack.hosting.host", DEFAULT_HOST),
                Math.clamp(integer(config, "resource-pack.port", "resource-pack.hosting.port", 8123), 1, 65535),
                string(config, "resource-pack.public-url", "resource-pack.hosting.public-url", ""),
                string(config, "resource-pack.file-name", "resource-pack.hosting.file-name", DEFAULT_FILE_NAME),
                bool(config, "resource-pack.required", "resource-pack.send.required", false),
                string(config, "resource-pack.prompt", "resource-pack.send.prompt", DEFAULT_PROMPT)
        );
    }

    public boolean generateOnStartup() {
        return GENERATE_ON_STARTUP;
    }

    public boolean hostingEnabled() {
        return HOSTING_ENABLED;
    }

    public boolean sendOnJoin() {
        return SEND_ON_JOIN;
    }

    private static boolean bool(@NotNull FileConfiguration config, @NotNull String key, @NotNull String legacyKey, boolean fallback) {
        if (config.contains(key)) {
            return config.getBoolean(key, fallback);
        }
        return config.getBoolean(legacyKey, fallback);
    }

    private static int integer(@NotNull FileConfiguration config, @NotNull String key, @NotNull String legacyKey, int fallback) {
        if (config.contains(key)) {
            return config.getInt(key, fallback);
        }
        return config.getInt(legacyKey, fallback);
    }

    private static @NotNull String string(
            @NotNull FileConfiguration config,
            @NotNull String key,
            @NotNull String legacyKey,
            @NotNull String fallback
    ) {
        if (config.contains(key)) {
            return config.getString(key, fallback);
        }
        return config.getString(legacyKey, fallback);
    }
}
