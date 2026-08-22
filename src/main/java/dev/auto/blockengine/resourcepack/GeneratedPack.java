package dev.auto.blockengine.resourcepack;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.UUID;

public record GeneratedPack(
        @NotNull UUID id,
        @NotNull Path folder,
        @NotNull Path zip,
        @NotNull String fileName,
        byte @NotNull [] sha1,
        @NotNull String url,
        @NotNull Component title,
        @NotNull Component description,
        @NotNull Component prompt,
        boolean required
) {
    public GeneratedPack {
        sha1 = sha1.clone();
    }

    @Override
    public byte @NotNull [] sha1() {
        return sha1.clone();
    }
}
