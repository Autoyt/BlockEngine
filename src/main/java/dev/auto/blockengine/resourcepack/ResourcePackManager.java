package dev.auto.blockengine.resourcepack;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.CustomBlockSystem;
import dev.auto.blockengine.api.resourcepack.GeneratedItemModel;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ResourcePackManager {
    private static final int PACK_FORMAT = 55;
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Set<String> ALLOWED_ASSET_EXTENSIONS = Set.of(
            "json",
            "mcmeta",
            "png",
            "ogg",
            "txt",
            "fsh",
            "vsh",
            "glsl"
    );
    private static final Set<String> BLOCKED_ASSET_EXTENSIONS = Set.of(
            "jar",
            "exe",
            "dll",
            "bat",
            "cmd",
            "ps1",
            "sh",
            "class",
            "java",
            "kt",
            "kts",
            "zip",
            "rar",
            "7z",
            "tar",
            "gz"
    );
    private static final ResourcePackManager instance = new ResourcePackManager();
    private ResourcePackConfig config;
    private GeneratedPack pack;
    private final List<GeneratedPack> hostedPacks = new ArrayList<>();

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
        if (!hostedPacks.isEmpty()) {
            ResourcePackHost.start(config, hostedPacks);
        }
    }

    public GeneratedPack generate() {
        config = ResourcePackConfig.load(Main.getInstance());
        Path root = Main.getInstance().getDataFolder().toPath().resolve("generated-resource-pack");
        Path zip = Main.getInstance().getDataFolder().toPath().resolve(config.fileName());

        try {
            delete(root);
            Files.createDirectories(root);

            Component title = Component.text("BlockEngine");
            Component description = Component.text("Generated BlockEngine custom block assets");
            packMeta(root, title, description);
            ItemModelGenerator.generateBackingBlock(root);
            demoTextures(root);

            for (dev.auto.blockengine.types.BlockDefinition registered : BlockRegistry.getBlocks()) {
                ItemModelGenerator.generateBlock(root, registered.apiDefinition());
            }
            for (GeneratedItemModel model : generatedItemModels()) {
                ItemModelGenerator.generateItemModel(root, model);
            }

            zip(root, zip);
            byte[] sha1 = sha1(zip);
            pack = new GeneratedPack(
                    packId(url(config)),
                    root,
                    zip,
                    config.fileName(),
                    sha1,
                    url(config),
                    title,
                    description,
                    rich(config.prompt()),
                    config.required()
            );
            hostedPacks.clear();
            hostedPacks.add(pack);
            hostedPacks.addAll(generateSystemPacks(config));
            Main.getInstance().getLogger().info("Generated BlockEngine resource pack at " + zip.toAbsolutePath()
                    + " sha1=" + HexFormat.of().formatHex(sha1));
            return pack;
        }

        catch (IOException exception) {
            Main.getInstance().getLogger().severe("Failed to generate BlockEngine resource pack.");
            exception.printStackTrace();
            pack = new GeneratedPack(
                    packId(url(config)),
                    root,
                    zip,
                    config.fileName(),
                    new byte[0],
                    url(config),
                    Component.text("BlockEngine"),
                    Component.text("Generated BlockEngine custom block assets"),
                    rich(config.prompt()),
                    config.required()
            );
            hostedPacks.clear();
            hostedPacks.add(pack);
            return pack;
        }
    }

    public void send(@NotNull Player player) {
        ResourcePackConfig loadedConfig = config == null ? ResourcePackConfig.load(Main.getInstance()) : config;
        if (!loadedConfig.enabled() || !loadedConfig.sendOnJoin() || pack == null || pack.sha1().length == 0) {
            return;
        }

        sendAll(player);
    }

    public @NotNull Collection<String> packIds() {
        return packsById().keySet();
    }

    public boolean send(@NotNull Player player, @NotNull String packId) {
        GeneratedPack generated = packsById().get(packId.toLowerCase(Locale.ROOT));
        if (generated == null || generated.sha1().length == 0) {
            return false;
        }
        sendSingle(player, generated);
        return true;
    }

    public int sendAll(@NotNull Player player) {
        int sent = 0;
        for (GeneratedPack generated : hostedPacks) {
            if (generated.sha1().length == 0) {
                continue;
            }
            sendSingle(player, generated);
            sent++;
        }
        return sent;
    }

    private @NotNull Map<String, GeneratedPack> packsById() {
        Map<String, GeneratedPack> packs = new LinkedHashMap<>();
        if (pack != null) {
            packs.put("blockengine", pack);
        }
        for (GeneratedPack generated : hostedPacks) {
            packs.putIfAbsent(packName(generated), generated);
        }
        return packs;
    }

    private void sendSingle(@NotNull Player player, @NotNull GeneratedPack generated) {
        if (sendAdventurePack(player, generated) || sendStack(player, generated)) {
            return;
        }
        sendLegacy(player, generated);
    }

    private void sendAllInternal(@NotNull Player player) {
        if (sendAdventureStack(player) || sendStack(player)) {
            return;
        }

        sendLegacy(player, pack);
    }

    private boolean sendAdventureStack(@NotNull Player player) {
        try {
            for (GeneratedPack generated : hostedPacks) {
                if (generated.sha1().length == 0) {
                    continue;
                }
                ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(
                        generated.id(),
                        URI.create(generated.url()),
                        HexFormat.of().formatHex(generated.sha1())
                );
                player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                        .required(generated.required())
                        .replace(false)
                        .prompt(generated.prompt())
                        .packs(info));
            }
            return true;
        } catch (LinkageError | IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean sendAdventurePack(@NotNull Player player, @NotNull GeneratedPack generated) {
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(
                    generated.id(),
                    URI.create(generated.url()),
                    HexFormat.of().formatHex(generated.sha1())
            );
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .required(generated.required())
                    .replace(false)
                    .prompt(generated.prompt())
                    .packs(info));
            return true;
        } catch (LinkageError | IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean sendStack(@NotNull Player player) {
        try {
            Method method = Player.class.getMethod("addResourcePack", UUID.class, String.class, byte[].class, String.class, boolean.class);
            for (GeneratedPack generated : hostedPacks) {
                if (generated.sha1().length == 0) {
                    continue;
                }
                method.invoke(player, generated.id(), generated.url(), generated.sha1(), plain(generated.prompt()), generated.required());
            }
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean sendStack(@NotNull Player player, @NotNull GeneratedPack generated) {
        try {
            Method method = Player.class.getMethod("addResourcePack", UUID.class, String.class, byte[].class, String.class, boolean.class);
            method.invoke(player, generated.id(), generated.url(), generated.sha1(), plain(generated.prompt()), generated.required());
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private void sendLegacy(@NotNull Player player, @NotNull GeneratedPack generated) {
        try {
            Method method = Player.class.getMethod("setResourcePack", String.class, byte[].class, String.class, boolean.class);
            method.invoke(player, generated.url(), generated.sha1(), plain(generated.prompt()), generated.required());
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method method = Player.class.getMethod("setResourcePack", String.class, byte[].class);
            method.invoke(player, generated.url(), generated.sha1());
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        player.setResourcePack(generated.url());
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

    private static @NotNull Collection<GeneratedPack> generateSystemPacks(@NotNull ResourcePackConfig config) throws IOException {
        List<GeneratedPack> packs = new ArrayList<>();
        for (Plugin plugin : Main.getInstance().getServer().getPluginManager().getPlugins()) {
            if (!(plugin instanceof CustomBlockSystem system)) {
                continue;
            }

            String namespace = system.getNamespace();
            CustomBlockSystem.PackDetails details = new CustomBlockSystem.PackDetails();
            details.urlEnding(namespace);
            system.setPackDetails(details);

            String ending = safeUrlEnding(details.urlEnding().isBlank() ? namespace : details.urlEnding());
            Path root = Main.getInstance().getDataFolder().toPath()
                    .resolve("generated-resource-pack")
                    .resolve("packs")
                    .resolve(ending);

            delete(root);
            Files.createDirectories(root);
            Component title = blank(details.title())
                    ? Component.text(plugin.getName())
                    : details.title();
            Component description = blank(details.description())
                    ? Component.text(plugin.getName() + " BlockEngine resources")
                    : details.description();
            String fileName = safeFileName(blank(title) ? ending : legacy(title));
            Path zip = Main.getInstance().getDataFolder().toPath()
                    .resolve("generated-packs")
                    .resolve(fileName);
            packMeta(root, title, description);
            copyIcon(root, details.icon());
            copyAssets(root, details.assets());

            Set<String> namespaces = new HashSet<>(details.assetNamespaces());
            namespaces.add(namespace);
            validateAssetNamespaces(plugin, namespace, namespaces);
            for (dev.auto.blockengine.types.BlockDefinition registered : BlockRegistry.getBlocks()) {
                if (namespaces.contains(registered.name().namespace())) {
                    ItemModelGenerator.generateBlock(root, registered.apiDefinition());
                }
            }
            system.onItemModelGeneration(model -> {
                try {
                    ItemModelGenerator.generateItemModel(root, model);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });

            zip(root, zip);
            byte[] sha1 = sha1(zip);
            String packUrl = packUrl(config, "/packs/" + ending);
            packs.add(new GeneratedPack(
                    packId(packUrl),
                    root,
                    zip,
                    fileName,
                    sha1,
                    packUrl,
                    title,
                    description,
                    blank(details.prompt()) ? prompt(title, description) : details.prompt(),
                    details.required()
            ));
        }
        return packs;
    }

    private static @NotNull List<GeneratedItemModel> generatedItemModels() {
        List<GeneratedItemModel> models = new ArrayList<>();
        for (Plugin plugin : Main.getInstance().getServer().getPluginManager().getPlugins()) {
            if (plugin instanceof CustomBlockSystem system) {
                system.onItemModelGeneration(models::add);
            }
        }
        return models;
    }

    private static void validateAssetNamespaces(
            @NotNull Plugin plugin,
            @NotNull String owningNamespace,
            @NotNull Set<String> namespaces
    ) {
        for (String namespace : namespaces) {
            if (NamespaceRegistry.loaded(namespace)) {
                continue;
            }
            throw new IllegalStateException("Plugin " + plugin.getName()
                    + " resource pack declares missing asset namespace '" + namespace
                    + "' for namespace '" + owningNamespace + "'.");
        }
    }

    private static void packMeta(
            @NotNull Path root,
            @NotNull Component title,
            @NotNull Component description
    ) throws IOException {
        ObjectNode rootNode = Main.getJsonMapper().createObjectNode();
        ObjectNode packNode = rootNode.putObject("pack");
        packNode.put("pack_format", PACK_FORMAT);
        if (!blank(title)) {
            packNode.set("title", Main.getJsonMapper().readTree(GSON.serialize(title)));
        }
        packNode.set("description", Main.getJsonMapper().readTree(GSON.serialize(description)));
        ObjectNode supportedFormats = packNode.putObject("supported_formats");
        supportedFormats.put("min_inclusive", 16);
        supportedFormats.put("max_inclusive", 99);
        Main.getJsonMapper().writeValue(root.resolve("pack.mcmeta").toFile(), rootNode);
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
        Files.createDirectories(output.getParent());
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

        return packUrl(config, "/" + config.fileName());
    }

    private static @NotNull String packUrl(@NotNull ResourcePackConfig config, @NotNull String path) {
        if (!config.publicUrl().isBlank()) {
            return baseUrl(config.publicUrl(), config.fileName()) + path;
        }

        String host = config.host();
        if (host.isBlank() || host.equals("0.0.0.0")) {
            String configuredIp = Main.getInstance().getServer().getIp();
            host = configuredIp == null || configuredIp.isBlank() ? "localhost" : configuredIp;
        }
        return "http://" + host + ":" + config.port() + path;
    }

    private static @NotNull String baseUrl(@NotNull String publicUrl, @NotNull String fileName) {
        String trimmed = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        if (trimmed.endsWith("/" + fileName)) {
            return trimmed.substring(0, trimmed.length() - fileName.length() - 1);
        }
        int slash = trimmed.lastIndexOf('/');
        if (slash > "https://".length() && trimmed.substring(slash + 1).contains(".")) {
            return trimmed.substring(0, slash);
        }
        return trimmed;
    }

    private static @NotNull UUID packId(@NotNull String url) {
        return UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
    }

    private static @NotNull Component rich(@NotNull String text) {
        return text.isBlank() ? Component.empty() : MINI.deserialize(text);
    }

    private static @NotNull Component prompt(@NotNull Component title, @NotNull Component description) {
        if (blank(title)) {
            return description;
        }
        if (blank(description)) {
            return title;
        }
        return title.append(Component.newline()).append(description);
    }

    private static boolean blank(@NotNull Component component) {
        return plain(component).isBlank();
    }

    private static @NotNull String plain(@NotNull Component component) {
        return PLAIN.serialize(component);
    }

    private static @NotNull String legacy(@NotNull Component component) {
        return LEGACY.serialize(component);
    }

    private static @NotNull String safeUrlEnding(@NotNull String ending) {
        String normalized = ending.replace('\\', '/').toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.replaceAll("[^a-z0-9._/-]", "_");
        return normalized.isBlank() ? "pack" : normalized;
    }

    private static @NotNull String safeFileName(@NotNull String fileName) {
        String normalized = fileName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        normalized = normalized
                .replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            normalized = "pack.zip";
        }
        return normalized.endsWith(".zip") ? normalized : normalized + ".zip";
    }

    private static @NotNull String packName(@NotNull GeneratedPack generated) {
        String name = generated.fileName();
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private static void copyIcon(@NotNull Path root, @Nullable Path icon) throws IOException {
        if (icon == null || !Files.isRegularFile(icon)) {
            return;
        }
        Files.copy(icon, root.resolve("pack.png"));
    }

    private static void copyAssets(@NotNull Path packRoot, @NotNull Collection<Path> roots) throws IOException {
        for (Path root : roots) {
            if (root == null || !Files.exists(root)) {
                continue;
            }
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalizedRoot)) {
                copyAssetFile(normalizedRoot.getParent(), normalizedRoot, packRoot);
                continue;
            }
            try (Stream<Path> files = Files.walk(normalizedRoot)) {
                for (Path file : files
                        .filter(Files::isRegularFile)
                        .sorted()
                        .toList()) {
                    copyAssetFile(normalizedRoot, file, packRoot);
                }
            }
        }
    }

    private static void copyAssetFile(@NotNull Path sourceRoot, @NotNull Path file, @NotNull Path packRoot) throws IOException {
        Path relative = sourceRoot.relativize(file.toAbsolutePath().normalize());
        if (!allowedAsset(relative)) {
            return;
        }

        Path output = packRoot.resolve(relative.toString()).normalize();
        if (!output.startsWith(packRoot.normalize())) {
            return;
        }
        Files.createDirectories(output.getParent());
        Files.copy(file, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean allowedAsset(@NotNull Path relative) {
        String path = relative.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (path.isBlank() || path.startsWith("/") || path.contains("../") || path.equals("..")) {
            return false;
        }
        if (!path.startsWith("assets/")) {
            return false;
        }

        String fileName = relative.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot == -1 || dot == fileName.length() - 1) {
            return false;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_ASSET_EXTENSIONS.contains(extension) && !BLOCKED_ASSET_EXTENSIONS.contains(extension);
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
