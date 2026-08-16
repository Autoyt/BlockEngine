package dev.auto.turtle.api;

import dev.auto.turtle.api.blocks.BlockAdapter;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

public interface CustomBlockSystem {
    String pluginVersion();

    String getNamespace();

    List<BlockAdapter> registerAdapters();

    default @Nullable String resourcePackText() {
        return null;
    }

    default @Nullable Path resourcePackLogo() {
        return null;
    }
}
