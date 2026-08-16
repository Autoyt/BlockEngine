package dev.auto.turtle.resourcepack;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.auto.turtle.Main;
import dev.auto.turtle.api.CustomBlockSystem;
import dev.auto.turtle.api.blocks.BlockDefinition;
import dev.auto.turtle.registry.BlockRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ResourcePackManager {
    private static final int PACK_FORMAT = 55;
    private static final byte[] TRANSPARENT_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADElEQVR42mNgYPgPAAEDAQBQPYv1AAAAAElFTkSuQmCC"
    );
    private static ResourcePackConfig config;
    private static GeneratedPack pack;

    private ResourcePackManager() {
    }

    public static void reload() {
        config = ResourcePackConfig.load(Main.getInstance());
        if (!config.enabled()) {
            ResourcePackHost.stop();
            return;
        }
        if (config.generateOnStartup()) {
            generate();
        }
        if (pack != null) {
            ResourcePackHost.start(config, pack);
        }
    }

    public static GeneratedPack generate() {
        config = ResourcePackConfig.load(Main.getInstance());
        Path root = Main.getInstance().getDataFolder().toPath().resolve("generated-resource-pack");
        Path zip = Main.getInstance().getDataFolder().toPath().resolve(config.fileName());

        try {
            delete(root);
            Files.createDirectories(root);

            ObjectNode rootNode = Main.getJsonMapper().createObjectNode();
            ObjectNode packNode = rootNode.putObject("pack");
            packNode.put("pack_format", PACK_FORMAT);
            packNode.put("description", packText());
            Main.getJsonMapper().writeValue(root.resolve("pack.mcmeta").toFile(), rootNode);
            packLogo(root);
            demoTextures(root);

            for (dev.auto.turtle.types.BlockDefinition registered : BlockRegistry.getBlocks()) {
                block(root, registered.apiDefinition());
            }

            zip(root, zip);
            byte[] sha1 = sha1(zip);
            pack = new GeneratedPack(root, zip, sha1, url(config));
            Main.getInstance().getLogger().info("Generated Turtle resource pack at " + zip.toAbsolutePath()
                    + " sha1=" + HexFormat.of().formatHex(sha1));
            return pack;
        }

        catch (IOException exception) {
            Main.getInstance().getLogger().severe("Failed to generate Turtle resource pack.");
            exception.printStackTrace();
            pack = new GeneratedPack(root, zip, new byte[0], url(config));
            return pack;
        }
    }

    public static void send(@NotNull Player player) {
        ResourcePackConfig loadedConfig = config == null ? ResourcePackConfig.load(Main.getInstance()) : config;
        if (!loadedConfig.enabled() || !loadedConfig.sendOnJoin() || pack == null || pack.sha1().length == 0) {
            return;
        }

        try {
            Method method = Player.class.getMethod("setResourcePack", String.class, byte[].class, String.class, boolean.class);
            method.invoke(player, pack.url(), pack.sha1(), loadedConfig.prompt(), loadedConfig.required());
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method method = Player.class.getMethod("setResourcePack", String.class, byte[].class);
            method.invoke(player, pack.url(), pack.sha1());
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        player.setResourcePack(pack.url());
    }

    public static void stop() {
        ResourcePackHost.stop();
    }

    private static void block(@NotNull Path root, @NotNull BlockDefinition definition) throws IOException {
        for (var entry : definition.states().entrySet()) {
            model(root, definition, entry.getKey(), entry.getValue());
        }
        item(root, definition);
    }

    private static void model(
            @NotNull Path root,
            @NotNull BlockDefinition definition,
            @NotNull String stateId,
            @NotNull BlockDefinition.State state
    ) throws IOException {
        Path output = root.resolve("assets")
                .resolve(definition.namespace())
                .resolve("models")
                .resolve("block")
                .resolve(definition.name())
                .resolve(stateId + ".json");
        Files.createDirectories(output.getParent());

        ObjectNode model = Main.getJsonMapper().createObjectNode();
        model.put("parent", "minecraft:block/cube");

        BlockDefinition.Textures source = state.textures();
        String particle = source.all();
        if (particle == null || particle.isBlank()) {
            particle = source.side();
        }
        if (particle == null || particle.isBlank()) {
            particle = source.front();
        }
        if (particle == null || particle.isBlank()) {
            particle = source.top();
        }
        if (particle == null || particle.isBlank()) {
            particle = source.bottom();
        }

        String north = source.north();
        if (north == null || north.isBlank()) {
            north = source.front();
        }
        if (north == null || north.isBlank()) {
            north = source.side();
        }
        if (north == null || north.isBlank()) {
            north = source.all();
        }

        String east = source.east();
        if (east == null || east.isBlank()) {
            east = source.side();
        }
        if (east == null || east.isBlank()) {
            east = source.all();
        }

        String south = source.south();
        if (south == null || south.isBlank()) {
            south = source.side();
        }
        if (south == null || south.isBlank()) {
            south = source.all();
        }

        String west = source.west();
        if (west == null || west.isBlank()) {
            west = source.side();
        }
        if (west == null || west.isBlank()) {
            west = source.all();
        }

        String up = source.top();
        if (up == null || up.isBlank()) {
            up = source.all();
        }

        String down = source.bottom();
        if (down == null || down.isBlank()) {
            down = source.all();
        }

        ObjectNode textures = model.putObject("textures");
        textures.put("particle", texture(definition, particle));
        textures.put("north", texture(definition, north));
        textures.put("east", texture(definition, east));
        textures.put("south", texture(definition, south));
        textures.put("west", texture(definition, west));
        textures.put("up", texture(definition, up));
        textures.put("down", texture(definition, down));

        Main.getJsonMapper().writeValue(output.toFile(), model);
    }

    private static void item(@NotNull Path root, @NotNull BlockDefinition definition) throws IOException {
        Path output = root.resolve("assets")
                .resolve(definition.namespace())
                .resolve("models")
                .resolve("item")
                .resolve(definition.name() + ".json");
        Files.createDirectories(output.getParent());

        ObjectNode model = Main.getJsonMapper().createObjectNode();
        model.put("parent", definition.namespace() + ":block/" + definition.name() + "/" + definition.defaultState());
        ObjectNode display = model.putObject("display");
        transform(display.putObject("thirdperson_righthand"), 75, 45, 0, 0, 2.5, 0, 0.375);
        transform(display.putObject("thirdperson_lefthand"), 75, 45, 0, 0, 2.5, 0, 0.375);
        transform(display.putObject("firstperson_righthand"), 0, 45, 0, 0, 0, 0, 0.4);
        transform(display.putObject("firstperson_lefthand"), 0, 225, 0, 0, 0, 0, 0.4);
        transform(display.putObject("gui"), 30, 225, 0, 0, 0, 0, 0.625);
        transform(display.putObject("ground"), 0, 0, 0, 0, 3, 0, 0.25);
        transform(display.putObject("fixed"), 0, 180, 0, 0, 0, 0, 0.5);

        Main.getJsonMapper().writeValue(output.toFile(), model);

        modernItem(root, definition, definition.defaultState(), definition.name());
        for (String stateId : definition.states().keySet()) {
            modernItem(root, definition, stateId, "block/" + definition.name() + "/" + stateId);
        }
    }

    private static void modernItem(
            @NotNull Path root,
            @NotNull BlockDefinition definition,
            @NotNull String stateId,
            @NotNull String outputPath
    ) throws IOException {
        Path output = root.resolve("assets")
                .resolve(definition.namespace())
                .resolve("items")
                .resolve(outputPath + ".json");
        Files.createDirectories(output.getParent());

        ObjectNode rootNode = Main.getJsonMapper().createObjectNode();
        ObjectNode model = rootNode.putObject("model");
        model.put("type", "minecraft:model");
        model.put("model", definition.namespace() + ":block/" + definition.name() + "/" + stateId);

        Main.getJsonMapper().writeValue(output.toFile(), rootNode);
    }

    private static @NotNull String texture(@NotNull BlockDefinition definition, String path) {
        if (path == null || path.isBlank()) {
            return "minecraft:block/stone";
        }
        if (path.contains(":")) {
            return path;
        }
        String normalized = path.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
        if (normalized.endsWith(".png")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (!normalized.startsWith("block/") && !normalized.startsWith("item/")) {
            normalized = "block/" + normalized;
        }
        return definition.namespace() + ":" + normalized;
    }

    private static void demoTextures(@NotNull Path root) throws IOException {
        texture(root, "transparent", 0x00000000);
        texture(root, "demo_inventory", 0xFF2F6BFF);
        texture(root, "demo_break", 0xFFFF8C1A);
        texture(root, "demo_washable", 0xFF00C8D7);
        texture(root, "demo_state_red", 0xFFFF3355);
        texture(root, "demo_state_green", 0xFF33CC66);
        texture(root, "demo_state_purple", 0xFFB84DFF);
        texture(root, "demo_mining", 0xFF7DFF42);
    }

    private static void texture(@NotNull Path root, @NotNull String name, int argb) throws IOException {
        Path output = root.resolve("assets")
                .resolve("turtle_test")
                .resolve("textures")
                .resolve("block")
                .resolve(name + ".png");
        Files.createDirectories(output.getParent());
        if (argb == 0x00000000) {
            Files.write(output, TRANSPARENT_PNG);
            return;
        }

        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, argb);
            }
        }
        ImageIO.write(image, "png", output.toFile());
    }

    private static void transform(ObjectNode node, double rx, double ry, double rz, double tx, double ty, double tz, double scale) {
        ArrayNode rotation = node.putArray("rotation");
        rotation.add(rx);
        rotation.add(ry);
        rotation.add(rz);

        ArrayNode translation = node.putArray("translation");
        translation.add(tx);
        translation.add(ty);
        translation.add(tz);

        ArrayNode scaleNode = node.putArray("scale");
        scaleNode.add(scale);
        scaleNode.add(scale);
        scaleNode.add(scale);
    }

    private static void zip(@NotNull Path root, @NotNull Path output) throws IOException {
        Files.deleteIfExists(output);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output));
             Stream<Path> files = Files.walk(root)) {
            for (Path file : files
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList()) {
                ZipEntry entry = new ZipEntry(root.relativize(file).toString().replace('\\', '/'));
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

    private static byte @NotNull [] sha1(@NotNull Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is not available.", exception);
        }
    }

    private static @NotNull String url(@NotNull ResourcePackConfig config) {
        if (!config.publicUrl().isBlank()) {
            return config.publicUrl();
        }

        String host = config.host();
        if (host.isBlank() || host.equals("0.0.0.0")) {
            String configuredIp = Main.getInstance().getServer().getIp();
            host = configuredIp == null || configuredIp.isBlank() ? "localhost" : configuredIp;
        }
        return "http://" + host + ":" + config.port() + "/" + config.fileName();
    }

    private static @NotNull String packText() {
        StringBuilder text = new StringBuilder("Generated Turtle custom block assets");
        for (Plugin plugin : Main.getInstance().getServer().getPluginManager().getPlugins()) {
            if (!(plugin instanceof CustomBlockSystem system)) {
                continue;
            }
            String line = system.resourcePackText();
            if (line == null || line.isBlank()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(line);
        }
        return text.toString();
    }

    private static void packLogo(@NotNull Path root) throws IOException {
        for (Plugin plugin : Main.getInstance().getServer().getPluginManager().getPlugins()) {
            if (!(plugin instanceof CustomBlockSystem system)) {
                continue;
            }
            Path logo = system.resourcePackLogo();
            if (logo == null || !Files.isRegularFile(logo)) {
                continue;
            }
            Files.copy(logo, root.resolve("pack.png"));
            return;
        }
    }

    private static void delete(@NotNull Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> files = Files.walk(path)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }
}
