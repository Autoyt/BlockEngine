package dev.auto.blockengine.resourcepack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.auto.blockengine.Main;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourcePackHost {
    private static HttpServer server;
    private static final Map<String, Path> downloads = new HashMap<>();
    private static final Map<String, Path> routes = new ConcurrentHashMap<>();

    private ResourcePackHost() {
    }

    public static void start(@NotNull ResourcePackConfig config, @NotNull Collection<GeneratedPack> packs) {
        stop();
        if (!config.enabled() || !config.hostingEnabled()) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
            routes.putAll(downloads);
            for (GeneratedPack pack : packs) {
                routes.put(path(pack.url()), pack.zip());
            }
            server.createContext("/", ResourcePackHost::serve);
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
            routes.put(path, zip);
        }
    }

    private static @NotNull String path(@NotNull String url) {
        String path = URI.create(url).getPath();
        return path == null || path.isBlank() ? "/" : path;
    }

    private static void serve(HttpExchange exchange) throws IOException {
        try (exchange) {
            Path zip = routes.get(exchange.getRequestURI().getPath());
            if (zip == null || !Files.isRegularFile(zip)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            try (var file = Files.newByteChannel(zip)) {
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + zip.getFileName() + "\"");
                exchange.getResponseHeaders().add("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, file.size());
                Channels.newInputStream(file).transferTo(exchange.getResponseBody());
            }
        }
    }

    public static void stop() {
        if (server == null) {
            return;
        }
        server.stop(0);
        server = null;
        routes.clear();
    }
}
