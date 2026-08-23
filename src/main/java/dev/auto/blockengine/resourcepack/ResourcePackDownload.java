package dev.auto.blockengine.resourcepack;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public record ResourcePackDownload(
        @NotNull String packId,
        @NotNull String url,
        @NotNull Path zip,
        long bytes
) {
}
