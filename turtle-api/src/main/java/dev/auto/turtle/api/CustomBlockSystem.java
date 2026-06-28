package dev.auto.turtle.api;

import dev.auto.turtle.api.blocks.BlockAdapter;

import java.util.List;

public interface CustomBlockSystem {
    String pluginVersion();
    String getNamespace();
    List<BlockAdapter> registerAdapters();

}
