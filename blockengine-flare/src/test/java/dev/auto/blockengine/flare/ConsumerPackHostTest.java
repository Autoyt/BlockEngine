package dev.auto.blockengine.flare;

import dev.auto.flare.api.widget.WidgetId;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerPackHostTest {
    @Test
    void servesOnlyTheImmutableConsumerPackAndSupportsHead() throws Exception {
        byte[] bytes = {1, 2, 3};
        try (var host = new ConsumerPackHost(new InetSocketAddress("127.0.0.1", 0), bytes);
             var client = HttpClient.newHttpClient()) {
            bytes[0] = 7;
            host.hash()[0] = 0;
            URI base = URI.create("http://127.0.0.1:" + host.port());
            URI download = host.downloadUri(base.toString());

            var response = client.send(HttpRequest.newBuilder(download).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertArrayEquals(new byte[]{1, 2, 3}, response.body());
            assertEquals("application/zip", response.headers().firstValue("Content-Type").orElseThrow());

            var head = client.send(HttpRequest.newBuilder(download).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, head.statusCode());
            assertEquals("3", head.headers().firstValue("Content-Length").orElseThrow());
            assertEquals(0, head.body().length);
            assertEquals(404, client.send(HttpRequest.newBuilder(base.resolve("/config.yml")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(download + "?query=1")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(405, client.send(HttpRequest.newBuilder(download).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
            assertThrows(IllegalArgumentException.class, () -> host.downloadUri("file:///tmp"));
        }
    }

    @Test
    void consumerPackOwnsOnlyItsShadersAndImportsFlaresPublicMethods() throws Exception {
        var names = new HashSet<String>();
        String chain = null;
        String shader = null;
        try (var zip = new ZipInputStream(getClass().getResourceAsStream("/blockengine-inspector.zip"))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory()) continue;
                names.add(entry.getName());
                if (entry.getName().equals("assets/blockengine_inspector/post_effect/hud.json")) {
                    chain = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                } else if (entry.getName().equals("assets/blockengine_inspector/shaders/post/hud.fsh")) {
                    shader = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }

        assertEquals(4, names.size());
        assertTrue(names.contains("pack.mcmeta"));
        assertTrue(names.stream().noneMatch(name -> name.startsWith("assets/flare/")));
        assertNotNull(chain);
        assertTrue(chain.contains("flare:post/capture_view"));
        assertTrue(chain.contains("flare:post/validate_view"));
        assertTrue(chain.contains("flare:post/present_view"));
        assertNotNull(shader);
        assertTrue(shader.contains("#include <flare:schemas/blockengine_inspector/target.glsl>"));
        var widgetId = new WidgetId(BlockEngineInspectorPlugin.ADDON, new InspectorWidget().id());
        String placementInclude = widgetId.placementInclude().replace(".glsl", "/scoped.glsl");
        assertTrue(shader.contains("#include <" + placementInclude + ">"));
        assertTrue(shader.contains("#include <flare:widget_bounds.glsl>"));
        assertTrue(shader.contains("_getTopLeft()"));
        assertTrue(shader.contains("_getBottomRight()"));
        assertTrue(shader.contains("getX()"));
        assertTrue(shader.contains("getBlockId("));
        assertTrue(shader.contains("getVisible()"));
    }
}
