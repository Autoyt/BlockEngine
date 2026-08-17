package dev.auto.blockengine.resourcepack;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public record GeneratedPack(
        @NotNull Path folder,
        @NotNull Path zip,
        byte @NotNull [] sha1,
        @NotNull String url
) {
    public GeneratedPack {
        sha1 = sha1.clone();
    }

    @Override
    public byte @NotNull [] sha1() {
        return sha1.clone();
    }
}
