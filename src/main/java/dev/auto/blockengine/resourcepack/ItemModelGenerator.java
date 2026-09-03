package dev.auto.blockengine.resourcepack;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import dev.auto.blockengine.api.resourcepack.GeneratedItemModel;
import dev.auto.blockengine.creative.CreativeInventoryManager;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ItemModelGenerator {
    private ItemModelGenerator() {
    }

    public static void generateBlock(@NotNull Path root, @NotNull BlockDefinition definition) throws IOException {
        for (var entry : definition.states().entrySet()) {
            blockStateModel(root, definition, entry.getKey(), entry.getValue());
        }
        blockItemModel(root, definition);
    }

    public static void generateCreativeEnchantmentItemModel(
            @NotNull Path root,
            @NotNull BlockDefinition definition
    ) throws IOException {
        assert definition.namespace() != null;
        modernItem(root, definition, definition.defaultState(), CreativeInventoryManager.itemModelKey(definition.id()).getKey());
    }

    public static void generateCreativeEnchantedBookModel(
            @NotNull Path root,
            @NotNull Collection<dev.auto.blockengine.types.BlockDefinition> blocks
    ) throws IOException {
        List<dev.auto.blockengine.types.BlockDefinition> creativeBlocks = blocks.stream()
                .filter(block -> block.apiDefinition().creativeMenu())
                .sorted(Comparator.comparing(dev.auto.blockengine.types.BlockDefinition::id))
                .toList();
        if (creativeBlocks.isEmpty()) {
            return;
        }

        ObjectNode model = itemModel("minecraft:item/enchanted_book");
        for (int index = creativeBlocks.size() - 1; index >= 0; index--) {
            dev.auto.blockengine.types.BlockDefinition block = creativeBlocks.get(index);
            model = storedEnchantmentCondition(
                    block,
                    itemModel(block.name().namespace() + ":item/" + block.name().name()),
                    model
            );
        }

        Path output = root.resolve("assets")
                .resolve("minecraft")
                .resolve("items")
                .resolve("enchanted_book.json");
        Files.createDirectories(output.getParent());

        ObjectNode rootNode = Main.getJsonMapper().createObjectNode();
        rootNode.set("model", model);
        Main.getJsonMapper().writeValue(output.toFile(), rootNode);
    }

    public static void generateBackingBlock(@NotNull Path root) throws IOException {
        String assetName = backingBlockAssetName();
        Path texture = root.resolve("assets")
                .resolve("minecraft")
                .resolve("textures")
                .resolve("block")
                .resolve(assetName + ".png");
        Files.createDirectories(texture.getParent());

        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", texture.toFile());

        Path modelPath = root.resolve("assets")
                .resolve("minecraft")
                .resolve("models")
                .resolve("block")
                .resolve(assetName + ".json");
        Files.createDirectories(modelPath.getParent());

        ObjectNode model = Main.getJsonMapper().createObjectNode();
        model.put("parent", "minecraft:block/cube_all");
        ObjectNode textures = model.putObject("textures");
        textures.put("all", "minecraft:block/" + assetName);
        textures.put("particle", "minecraft:block/" + assetName);
        Main.getJsonMapper().writeValue(modelPath.toFile(), model);

        Path blockStatePath = root.resolve("assets")
                .resolve("minecraft")
                .resolve("blockstates")
                .resolve(assetName + ".json");
        Files.createDirectories(blockStatePath.getParent());

        ObjectNode blockState = Main.getJsonMapper().createObjectNode();
        blockState.putObject("variants")
                .putArray("")
                .addObject()
                .put("model", "minecraft:block/" + assetName);
        Main.getJsonMapper().writeValue(blockStatePath.toFile(), blockState);

        breakOverlays(root);
    }

    public static void generateWand(@NotNull Path root) throws IOException {
        String namespace = Main.getInstance().getName().toLowerCase(Locale.ROOT);
        Path texture = root.resolve("assets")
                .resolve(namespace)
                .resolve("textures")
                .resolve("item")
                .resolve("block_engine_wand.png");
        Files.createDirectories(texture.getParent());
        try (var input = Main.getInstance().getResource("resource/wand.png")) {
            if (input != null) {
                Files.copy(input, texture, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                for (int x = 0; x < image.getWidth(); x++) {
                    for (int y = 0; y < image.getHeight(); y++) {
                        image.setRGB(x, y, x == y || x == image.getWidth() - y - 1 ? 0xFFFFD54F : 0x00000000);
                    }
                }
                ImageIO.write(image, "png", texture.toFile());
            }
        }

        Path modelPath = root.resolve("assets")
                .resolve(namespace)
                .resolve("models")
                .resolve("item")
                .resolve("block_engine_wand.json");
        Files.createDirectories(modelPath.getParent());

        ObjectNode model = Main.getJsonMapper().createObjectNode();
        model.put("parent", "minecraft:item/handheld");
        model.putObject("textures").put("layer0", namespace + ":item/block_engine_wand");
        Main.getJsonMapper().writeValue(modelPath.toFile(), model);

        Path itemPath = root.resolve("assets")
                .resolve(namespace)
                .resolve("items")
                .resolve("block_engine_wand.json");
        Files.createDirectories(itemPath.getParent());

        ObjectNode item = Main.getJsonMapper().createObjectNode();
        ObjectNode itemModel = item.putObject("model");
        itemModel.put("type", "minecraft:model");
        itemModel.put("model", namespace + ":item/block_engine_wand");
        Main.getJsonMapper().writeValue(itemPath.toFile(), item);
    }

    public static void generateWandFeedback(@NotNull Path root) throws IOException {
        generateWandFeedback(root, "yes");
        generateWandFeedback(root, "no");
    }

    private static void generateWandFeedback(@NotNull Path root, @NotNull String name) throws IOException {
        String namespace = Main.getInstance().getName().toLowerCase(Locale.ROOT);
        String key = "wand_feedback_" + name;
        Path texture = root.resolve("assets")
                .resolve(namespace)
                .resolve("textures")
                .resolve("item")
                .resolve(key + ".png");
        Files.createDirectories(texture.getParent());
        try (InputStream input = Main.getInstance().getResource("resource/" + name + ".png")) {
            BufferedImage icon = input == null ? fallbackFeedback(name.equals("yes")) : ImageIO.read(input);
            if (icon == null) {
                icon = fallbackFeedback(name.equals("yes"));
            }
            BufferedImage centered = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            int x = Math.max(0, (centered.getWidth() - icon.getWidth()) / 2);
            int y = Math.max(0, (centered.getHeight() - icon.getHeight()) / 2);
            Graphics2D graphics = centered.createGraphics();
            graphics.drawImage(icon, x, y, null);
            graphics.dispose();
            ImageIO.write(centered, "png", texture.toFile());
        }

        Path modelPath = root.resolve("assets")
                .resolve(namespace)
                .resolve("models")
                .resolve("item")
                .resolve(key + ".json");
        Files.createDirectories(modelPath.getParent());

        ObjectNode model = Main.getJsonMapper().createObjectNode();
        model.put("parent", "minecraft:item/generated");
        model.putObject("textures").put("layer0", namespace + ":item/" + key);
        Main.getJsonMapper().writeValue(modelPath.toFile(), model);

        Path itemPath = root.resolve("assets")
                .resolve(namespace)
                .resolve("items")
                .resolve(key + ".json");
        Files.createDirectories(itemPath.getParent());

        ObjectNode item = Main.getJsonMapper().createObjectNode();
        ObjectNode itemModel = item.putObject("model");
        itemModel.put("type", "minecraft:model");
        itemModel.put("model", namespace + ":item/" + key);
        Main.getJsonMapper().writeValue(itemPath.toFile(), item);
    }

    private static @NotNull BufferedImage fallbackFeedback(boolean success) {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        int color = success ? 0xFF35D66B : 0xFFFF3355;
        for (int index = 0; index < image.getWidth(); index++) {
            if (success) {
                image.setRGB(index, Math.min(image.getHeight() - 1, 6 + index / 3), color);
                image.setRGB(Math.min(image.getWidth() - 1, index + 3), Math.max(0, 8 - index), color);
            } else {
                image.setRGB(index, index, color);
                image.setRGB(index, image.getHeight() - index - 1, color);
            }
        }
        return image;
    }

    public static void breakOverlays(@NotNull Path root) throws IOException {
        String namespace = Main.getInstance().getName().toLowerCase(Locale.ROOT);
        for (int stage = 0; stage <= 9; stage++) {
            Path modelPath = root.resolve("assets")
                    .resolve(namespace)
                    .resolve("models")
                    .resolve("block")
                    .resolve("break_stage")
                    .resolve(stage + ".json");
            Files.createDirectories(modelPath.getParent());

            ObjectNode model = Main.getJsonMapper().createObjectNode();
            model.put("parent", "minecraft:block/cube_all");
            ObjectNode textures = model.putObject("textures");
            textures.put("all", "minecraft:block/destroy_stage_" + stage);
            textures.put("particle", "minecraft:block/destroy_stage_" + stage);
            Main.getJsonMapper().writeValue(modelPath.toFile(), model);

            Path itemPath = root.resolve("assets")
                    .resolve(namespace)
                    .resolve("items")
                    .resolve("break_stage")
                    .resolve(stage + ".json");
            Files.createDirectories(itemPath.getParent());

            ObjectNode item = Main.getJsonMapper().createObjectNode();
            ObjectNode itemModel = item.putObject("model");
            itemModel.put("type", "minecraft:model");
            itemModel.put("model", namespace + ":block/break_stage/" + stage);
            Main.getJsonMapper().writeValue(itemPath.toFile(), item);
        }
    }

    public static void generateItemModel(@NotNull Path root, @NotNull GeneratedItemModel generated) throws IOException {
        Path modelPath = root.resolve("assets")
                .resolve(generated.key().getNamespace())
                .resolve("models")
                .resolve("item")
                .resolve(generated.key().getKey() + ".json");
        Files.createDirectories(modelPath.getParent());

        ObjectNode model = Main.getJsonMapper().createObjectNode();
        model.put("parent", generated.parent());
        if (!generated.textures().isEmpty()) {
            ObjectNode textures = model.putObject("textures");
            for (var entry : generated.textures().entrySet()) {
                textures.put(entry.getKey(), normalizeTexture(generated.key(), entry.getValue()));
            }
        }
        Main.getJsonMapper().writeValue(modelPath.toFile(), model);

        Path itemPath = root.resolve("assets")
                .resolve(generated.key().getNamespace())
                .resolve("items")
                .resolve(generated.key().getKey() + ".json");
        Files.createDirectories(itemPath.getParent());

        ObjectNode rootNode = Main.getJsonMapper().createObjectNode();
        ObjectNode itemModel = rootNode.putObject("model");
        itemModel.put("type", "minecraft:model");
        itemModel.put("model", generated.key().getNamespace() + ":item/" + generated.key().getKey());
        Main.getJsonMapper().writeValue(itemPath.toFile(), rootNode);
    }

    static @NotNull String backingBlockAssetName() {
        Material backingBlock = Main.getBackingBlock();
        return backingBlock.name().toLowerCase(Locale.ROOT);
    }

    private static void blockStateModel(
            @NotNull Path root,
            @NotNull BlockDefinition definition,
            @NotNull String stateId,
            @NotNull BlockDefinition.State state
    ) throws IOException {
        assert definition.namespace() != null;
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
        ObjectNode textures = model.putObject("textures");
        textures.put("particle", texture(definition, source.all(), source.side(), source.front(), source.top(), source.bottom()));
        textures.put("north", texture(definition, source.north(), source.front(), source.side(), source.all()));
        textures.put("east", texture(definition, source.east(), source.side(), source.all()));
        textures.put("south", texture(definition, source.south(), source.side(), source.all()));
        textures.put("west", texture(definition, source.west(), source.side(), source.all()));
        textures.put("up", texture(definition, source.top(), source.all()));
        textures.put("down", texture(definition, source.bottom(), source.all()));

        Main.getJsonMapper().writeValue(output.toFile(), model);
    }

    private static void blockItemModel(@NotNull Path root, @NotNull BlockDefinition definition) throws IOException {
        assert definition.namespace() != null;
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

    private static @NotNull ObjectNode storedEnchantmentCondition(
            @NotNull dev.auto.blockengine.types.BlockDefinition block,
            @NotNull ObjectNode onTrue,
            @NotNull ObjectNode onFalse
    ) {
        ObjectNode condition = Main.getJsonMapper().createObjectNode();
        condition.put("type", "minecraft:condition");
        condition.put("property", "minecraft:component");
        condition.put("predicate", "minecraft:stored_enchantments");

        ArrayNode value = condition.putArray("value");
        ObjectNode enchantment = value.addObject();
        enchantment.put("enchantments", CreativeInventoryManager.enchantmentKey(block.id()).asString());
        enchantment.putObject("levels").put("min", 1);

        condition.set("on_true", onTrue);
        condition.set("on_false", onFalse);
        return condition;
    }

    private static @NotNull ObjectNode itemModel(@NotNull String modelPath) {
        ObjectNode model = Main.getJsonMapper().createObjectNode();
        model.put("type", "minecraft:model");
        model.put("model", modelPath);
        return model;
    }

    private static @NotNull String texture(@NotNull BlockDefinition definition, String... paths) {
        String path = null;
        for (String candidate : paths) {
            if (candidate != null && !candidate.isBlank()) {
                path = candidate;
                break;
            }
        }
        if (path == null || path.isBlank()) {
            return "minecraft:block/stone";
        }
        if (path.contains(":")) {
            return path;
        }
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (!normalized.startsWith("block/") && !normalized.startsWith("item/")) {
            normalized = "block/" + normalized;
        }
        return definition.namespace() + ":" + normalized;
    }

    private static @NotNull String normalizeTexture(@NotNull org.bukkit.NamespacedKey key, @NotNull String path) {
        if (path.contains(":")) {
            return path;
        }
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (!normalized.startsWith("block/") && !normalized.startsWith("item/")) {
            normalized = "item/" + normalized;
        }
        return key.getNamespace() + ":" + normalized;
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
}
