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

public final class ResourcePackHost {
    private static HttpServer server;

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
                server.createContext(path(pack), exchange -> {
                    Path zip = pack.zip();
                    if (!Files.isRegularFile(zip)) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }

                    byte[] bytes = Files.readAllBytes(zip);
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.getResponseHeaders().add("Cache-Control", "no-cache");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(bytes);
                    }
                });
            }
            server.start();
            Main.getInstance().getLogger().info("Serving " + packs.size() + " BlockEngine resource pack(s).");
        } catch (IOException exception) {
            Main.getInstance().getLogger().severe("Failed to start BlockEngine resource pack host.");
            exception.printStackTrace();
        }
    }

    private static @NotNull String path(@NotNull GeneratedPack pack) {
        String path = URI.create(pack.url()).getPath();
        return path == null || path.isBlank() ? "/" : path;
    }

    public static void stop() {
        if (server == null) {
            return;
        }
        server.stop(0);
        server = null;
    }
}
