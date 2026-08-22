package dev.auto.blockengine.resourcepack;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.CustomBlockSystem;
import dev.auto.blockengine.registry.BlockRegistry;
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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ResourcePackManager {
    private static final int PACK_FORMAT = 55;
    private static final ResourcePackManager instance = new ResourcePackManager();
    private ResourcePackConfig config;
    private GeneratedPack pack;

    private ResourcePackManager() {
    }

    public static @NotNull ResourcePackManager getInstance() {
        return instance;
    }

    public void reload() {
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

    public GeneratedPack generate() {
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
            ObjectNode supportedFormats = packNode.putObject("supported_formats");
            supportedFormats.put("min_inclusive", 16);
            supportedFormats.put("max_inclusive", 99);
            Main.getJsonMapper().writeValue(root.resolve("pack.mcmeta").toFile(), rootNode);
            packLogo(root);
            ItemModelGenerator.generateBackingBlock(root);
            demoTextures(root);

            for (dev.auto.blockengine.types.BlockDefinition registered : BlockRegistry.getBlocks()) {
                ItemModelGenerator.generateBlock(root, registered.apiDefinition());
            }

            zip(root, zip);
            byte[] sha1 = sha1(zip);
            pack = new GeneratedPack(root, zip, sha1, url(config));
            Main.getInstance().getLogger().info("Generated BlockEngine resource pack at " + zip.toAbsolutePath()
                    + " sha1=" + HexFormat.of().formatHex(sha1));
            return pack;
        }

        catch (IOException exception) {
            Main.getInstance().getLogger().severe("Failed to generate BlockEngine resource pack.");
            exception.printStackTrace();
            pack = new GeneratedPack(root, zip, new byte[0], url(config));
            return pack;
        }
    }

    public void send(@NotNull Player player) {
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

    public void stop() {
        ResourcePackHost.stop();
    }

    private static void demoTextures(@NotNull Path root) throws IOException {
        texture(root, "transparent", 0x00000000);
        texture(root, "demo_inventory", 0xFF2F6BFF);
        texture(root, "demo_break", 0xFFFF8C1A);
        texture(root, "demo_state_red", 0xFFFF3355);
        texture(root, "demo_state_green", 0xFF33CC66);
        texture(root, "demo_state_purple", 0xFFB84DFF);
    }

    private static void texture(@NotNull Path root, @NotNull String name, int argb) throws IOException {
        Path output = root.resolve("assets")
                .resolve("blockengine_test")
                .resolve("textures")
                .resolve("block")
                .resolve(name + ".png");
        Files.createDirectories(output.getParent());
        if (argb == 0x00000000) {
            transparentTexture(output);
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

    private static void transparentTexture(@NotNull Path output) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", output.toFile());
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
        StringBuilder text = new StringBuilder("Generated BlockEngine custom block assets");
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
