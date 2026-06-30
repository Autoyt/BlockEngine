package dev.auto.turtle.api;

import dev.auto.turtle.api.blocks.BlockAdapter;

import java.util.List;

public interface CustomBlockSystem {
    String pluginVersion();
    String getNamespace();
    // We can call getJsonfile etc on each one of these
    List<BlockAdapter> registerAdapters();

}
