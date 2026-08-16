package dev.auto.turtle.resourcepack;

import com.sun.net.httpserver.HttpServer;
import dev.auto.turtle.Main;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResourcePackHost {
    private static HttpServer server;

    private ResourcePackHost() {
    }

    public static void start(@NotNull ResourcePackConfig config, @NotNull GeneratedPack pack) {
        stop();
        if (!config.enabled() || !config.hostingEnabled()) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
            server.createContext("/" + config.fileName(), exchange -> {
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
            server.start();
            Main.getInstance().getLogger().info("Serving Turtle resource pack at " + pack.url());
        } catch (IOException exception) {
            Main.getInstance().getLogger().severe("Failed to start Turtle resource pack host.");
            exception.printStackTrace();
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
