package dev.auto.turtle.registry;

import dev.auto.turtle.Main;
import dev.auto.turtle.api.CustomBlockSystem;
import dev.auto.turtle.api.blocks.BlockAdapter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DiscoverySystem {
    private DiscoverySystem() {
    }

    public static List<BlockAdapter> discoverBlocks() {
        int discoveredPlugins = 0;
        int registeredPlugins = 0;
        int registeredBlocks = 0;
        List<BlockAdapter> discoveredAdapters = new ArrayList<>();

        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (!plugin.isEnabled()) {
                continue;
            }

            discoveredPlugins++;

            if (!(plugin instanceof CustomBlockSystem system)) {
                continue;
            }

            registeredPlugins++;

            String namespace = system.getNamespace();

            List<BlockAdapter> adapters = system.registerAdapters();
            if (adapters == null || adapters.isEmpty()) {
                Main.getInstance().getLogger()
                        .warning("Plugin " + plugin.getName() + " did not provide any block adapters.");
                continue;
            }

            registeredBlocks += adapters.size();
            discoveredAdapters.addAll(adapters);

            for (BlockAdapter adapter : adapters) {
                Main.getInstance().getLogger().fine(
                        "Discovered block adapter " + adapter.getClass().getName() + " from plugin " + plugin.getName()
                );

                try {
                    BlockRegistry.registerBlock(adapter, namespace);
                } catch (Exception e) {
                    Main.getInstance().getLogger().severe("Failed to register block adapter " + adapter.getClass().getName() + " from plugin " + plugin.getName());
                    e.printStackTrace();
                }
            }
        }

        Main.getInstance().getLogger()
                .info("Discovered " + discoveredPlugins + " plugins, " + registeredPlugins + " registered, " + registeredBlocks + " blocks registered.");

        return Collections.unmodifiableList(discoveredAdapters);
    }
}
