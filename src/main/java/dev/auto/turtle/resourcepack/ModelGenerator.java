package dev.auto.turtle.resourcepack;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.auto.turtle.Main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class ModelGenerator {
    private static final String FORMAT_VERSION = "26.2";

    public static void generate(BlockModelDefinition bm) throws IOException {
        Path output = Main.getInstance().getDataFolder().toPath().resolve("assets").resolve(bm.namespace()).resolve("models").resolve(bm.name() + ".json");
        Files.createDirectories(output.getParent());

        ObjectNode root = Main.getJsonMapper().createObjectNode();
        root.put("format_version", FORMAT_VERSION);

        // TODO update this to allow for multiblock textures
        ArrayNode elements = root.putArray("elements");
        ObjectNode element = elements.addObject();
        element.set("from", createCoordinate(0, 0, 0));
        element.set("to", createCoordinate(16, 16, 16));
        element.put("color", 8);

        ObjectNode faces = element.putObject("faces");
        addFace(faces, "north", resolveTexture(bm, bm.textures().north(), bm.textures().front(), bm.textures().side(), bm.textures().all()));
        addFace(faces, "east", resolveTexture(bm, bm.textures().east(), bm.textures().side(), bm.textures().all()));
        addFace(faces, "south", resolveTexture(bm, bm.textures().south(), bm.textures().side(), bm.textures().all()));
        addFace(faces, "west", resolveTexture(bm, bm.textures().west(), bm.textures().side(), bm.textures().all()));
        addFace(faces, "up", resolveTexture(bm, bm.textures().top(), bm.textures().all()));
        addFace(faces, "down", resolveTexture(bm, bm.textures().bottom(), bm.textures().all()));

        Main.getJsonMapper().writeValue(output.toFile(), root);
    }

    private static ArrayNode createCoordinate(int x, int y, int z) {
        ArrayNode node = Main.getJsonMapper().createArrayNode();
        node.add(x);
        node.add(y);
        node.add(z);
        return node;
    }

    private static ArrayNode defaultUv() {
        ArrayNode node = Main.getJsonMapper().createArrayNode();
        node.add(0);
        node.add(0);
        node.add(16);
        node.add(16);
        return node;
    }

    private static void addFace(ObjectNode faces, String direction, String texture) {
        ObjectNode face = faces.putObject(direction);
        face.set("uv", defaultUv());
        face.put("texture", texture);
    }

    private static String resolveTexture(BlockModelDefinition bm, Path... candidates) {
        for (Path candidate : candidates) {
            if (candidate != null) {
                return toAssetId(bm.namespace(), candidate);
            }
        }
        return "#missing";
    }

    private static String toAssetId(String namespace, Path texturePath) {
        Path normalized = Objects.requireNonNull(texturePath, "texturePath").normalize();
        String slashPath = normalized.toString().replace('\\', '/');
        String prefix = "/assets/" + namespace + "/textures/";
        int assetsIndex = slashPath.indexOf(prefix);

        String relativeTexturePath;
        if (assetsIndex >= 0) {
            relativeTexturePath = slashPath.substring(assetsIndex + prefix.length());
        } else {
            relativeTexturePath = normalized.getFileName().toString();
        }

        if (relativeTexturePath.endsWith(".png")) {
            relativeTexturePath = relativeTexturePath.substring(0, relativeTexturePath.length() - 4);
        }

        return namespace + ":" + relativeTexturePath;
    }
}
