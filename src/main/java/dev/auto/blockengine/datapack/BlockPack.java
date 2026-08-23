package dev.auto.blockengine.datapack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record BlockPack(
        @NotNull Path folder,
        @NotNull String namespace,
        @NotNull String title,
        @NotNull String description,
        @NotNull String prompt,
        @NotNull String urlEnding,
        boolean required,
        boolean catalog,
        @Nullable Path icon,
        @NotNull List<Path> assetRoots,
        @NotNull List<BlockPackBlock> blocks
) {
    public BlockPack {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(urlEnding, "urlEnding");
        assetRoots = List.copyOf(Objects.requireNonNull(assetRoots, "assetRoots"));
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }
}
