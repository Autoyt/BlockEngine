package dev.auto.blockengine.resourcepack;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

public record BlockModelDefinition(String namespace, String name, TexturePaths textures) {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");

    public BlockModelDefinition {
        namespace = Objects.requireNonNull(namespace, "namespace");
        name = Objects.requireNonNull(name, "name");
        textures = Objects.requireNonNull(textures, "textures");

        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid namespace: " + namespace);
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Model name cannot be blank.");
        }
    }

    public record TexturePaths(
            Path all,
            Path side,
            Path front,
            Path top,
            Path bottom,
            Path north,
            Path south,
            Path east,
            Path west
    ) {
        public TexturePaths {
            all = normalize(all);
            side = normalize(side);
            front = normalize(front);
            top = normalize(top);
            bottom = normalize(bottom);
            north = normalize(north);
            south = normalize(south);
            east = normalize(east);
            west = normalize(west);

            if (all == null
                    && side == null
                    && front == null
                    && top == null
                    && bottom == null
                    && north == null
                    && south == null
                    && east == null
                    && west == null) {
                throw new IllegalArgumentException("At least one texture path must be provided.");
            }
        }

        private static Path normalize(Path path) {
            return path == null ? null : path.normalize();
        }
    }
}
