package dev.auto.blockengine.flare;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** BlockEngine Inspector owns this host. It does not call Flare internals or use Flare's HTTP listener. */
final class ConsumerPackHost implements AutoCloseable {
    private final byte[] bytes;
    private final byte[] hash;
    private final String path;
    private final HttpServer server;
    private final ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor();

    ConsumerPackHost(InetSocketAddress address, byte[] source) throws IOException {
        bytes = source.clone();
        try {
            hash = MessageDigest.getInstance("SHA-1").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }

        String hashText = HexFormat.of().formatHex(hash);
        path = "/blockengine-inspector-" + hashText + ".zip";
        server = HttpServer.create(address, 16);
        server.setExecutor(requests);
        server.createContext("/", this::handle);
        server.start();
    }

    int port() { return server.getAddress().getPort(); }
    byte[] hash() { return hash.clone(); }

    URI downloadUri(String publicBaseUrl) {
        URI base = URI.create(publicBaseUrl);
        if ((!"http".equalsIgnoreCase(base.getScheme()) && !"https".equalsIgnoreCase(base.getScheme()))
            || base.getHost() == null || base.getRawUserInfo() != null || base.getRawQuery() != null
            || base.getRawFragment() != null || (!base.getRawPath().isEmpty() && !base.getRawPath().equals("/"))) {
            throw new IllegalArgumentException("public-url must be an HTTP(S) origin, for example http://localhost:8128");
        }
        return base.resolve(path);
    }

    private void handle(HttpExchange request) throws IOException {
        try (request) {
            if (!request.getRequestMethod().equals("GET") && !request.getRequestMethod().equals("HEAD")) {
                request.getResponseHeaders().set("Allow", "GET, HEAD");
                request.sendResponseHeaders(405, -1);
                return;
            }
            if (!request.getRequestURI().getRawPath().equals(path) || request.getRequestURI().getRawQuery() != null) {
                request.sendResponseHeaders(404, -1);
                return;
            }

            request.getResponseHeaders().set("Content-Type", "application/zip");
            request.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
            request.getResponseHeaders().set("Content-Length", Integer.toString(bytes.length));
            if (request.getRequestMethod().equals("HEAD")) {
                request.sendResponseHeaders(200, -1);
                return;
            }

            request.sendResponseHeaders(200, bytes.length);
            request.getResponseBody().write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        requests.close();
    }
}
