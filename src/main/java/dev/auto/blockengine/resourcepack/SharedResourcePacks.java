package dev.auto.blockengine.resourcepack;

import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

interface SharedResourcePacks extends AutoCloseable {
    boolean available();

    boolean ready();

    void publish(List<GeneratedPack> packs) throws IOException;

    CompletionStage<?> resend(Player player);

    String hostDownload(String id, Path zip) throws IOException;

    @Override
    void close();
}
