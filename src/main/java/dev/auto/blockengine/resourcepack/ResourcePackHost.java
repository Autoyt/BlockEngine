package dev.auto.blockengine.resourcepack;

import com.sun.net.httpserver.HttpServer;
import dev.auto.blockengine.Main;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class ResourcePackHost {
    private static HttpServer server;
    private static final Map<String, Path> downloads = new HashMap<>();

    private ResourcePackHost() {
    }

    public static void start(@NotNull ResourcePackConfig config, @NotNull Collection<GeneratedPack> packs) {
        stop();
        if (!config.enabled() || !config.hostingEnabled()) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
            for (GeneratedPack pack : packs) {
                createFileContext(path(pack), pack.zip());
            }
            for (Map.Entry<String, Path> download : downloads.entrySet()) {
                createFileContext(download.getKey(), download.getValue());
            }
            server.start();
            Main.getInstance().getLogger().info("Serving " + packs.size() + " BlockEngine resource pack(s).");
        } catch (IOException exception) {
            Main.getInstance().getLogger().severe("Failed to start BlockEngine resource pack host.");
            Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Resource-pack host startup failure", exception);
        }
    }

    public static void publish(@NotNull String url, @NotNull Path zip) {
        String path = path(url);
        downloads.put(path, zip);
        if (server != null) {
            createFileContext(path, zip);
        }
    }

    private static @NotNull String path(@NotNull GeneratedPack pack) {
        return path(pack.url());
    }

    private static @NotNull String path(@NotNull String url) {
        String path = URI.create(url).getPath();
        return path == null || path.isBlank() ? "/" : path;
    }

    private static void createFileContext(@NotNull String path, @NotNull Path zip) {
        try {
            server.createContext(path, exchange -> {
                if (!Files.isRegularFile(zip)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                byte[] bytes = Files.readAllBytes(zip);
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + zip.getFileName() + "\"");
                exchange.getResponseHeaders().add("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            });
        } catch (IllegalArgumentException ignored) {
            // Context already exists for this path; keep serving the updated file path captured above if unchanged.
        }
    }

    public static void stop() {
        if (server == null) {
            return;
        }
        server.stop(0);
        server = null;
    }
}
