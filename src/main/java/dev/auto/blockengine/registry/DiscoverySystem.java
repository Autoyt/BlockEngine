package dev.auto.blockengine.registry;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.CustomBlockSystem;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.datapack.DataBlockPacks;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class DiscoverySystem {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");

    private DiscoverySystem() {
    }

    public static List<BlockAdapter> discoverBlocks() {
        int discoveredPlugins = 0;
        int registeredPlugins = 0;
        int registeredBlocks = 0;
        List<BlockAdapter> discoveredAdapters = new ArrayList<>();
        List<SystemEntry> systems = new ArrayList<>();
        NamespaceRegistry.clear();
        BlockRegistry.clear();

        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (!plugin.isEnabled()) {
                continue;
            }

            discoveredPlugins++;

            if (!(plugin instanceof CustomBlockSystem system)) {
                continue;
            }

            String namespace = system.getNamespace();
            if (namespace == null || !NAMESPACE_PATTERN.matcher(namespace).matches()) {
                Main.getInstance().getLogger().severe("Plugin " + plugin.getName()
                        + " declared invalid BlockEngine namespace: " + namespace);
                continue;
            }
            List<String> dependencies = dependencies(plugin, system);
            if (dependencies == null) {
                continue;
            }
            systems.add(new SystemEntry(plugin, system, namespace, dependencies));
        }

        Set<String> validNamespaces = validNamespaces(systems);
        for (SystemEntry entry : systems) {
            if (!validNamespaces.contains(entry.namespace())) {
                Main.getInstance().getLogger().severe("Skipping BlockEngine plugin " + entry.plugin().getName()
                        + " because required namespaces are missing: "
                        + missing(entry.dependencies(), validNamespaces));
                continue;
            }

            registeredPlugins++;
            NamespaceRegistry.load(entry.namespace());

            List<BlockAdapter> adapters = entry.system().registerAdapters();
            if (adapters == null || adapters.isEmpty()) {
                Main.getInstance().getLogger()
                        .warning("Plugin " + entry.plugin().getName() + " did not provide any block adapters.");
                continue;
            }

            registeredBlocks += adapters.size();
            discoveredAdapters.addAll(adapters);

            for (BlockAdapter adapter : adapters) {
                Main.getInstance().getLogger().fine(
                        "Discovered block adapter " + adapter.getClass().getName() + " from plugin " + entry.plugin().getName()
                );

                try {
                    BlockRegistry.registerBlock(adapter, entry.namespace());
                } catch (Exception e) {
                    Main.getInstance().getLogger().severe("Failed to register block adapter " + adapter.getClass().getName() + " from plugin " + entry.plugin().getName());
                    Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Adapter registration failure", e);
                }
            }
        }

        List<BlockAdapter> dataPackAdapters = DataBlockPacks.loadAndRegister();
        discoveredAdapters.addAll(dataPackAdapters);
        registeredBlocks += dataPackAdapters.size();

        Main.getInstance().getLogger()
                .info("Discovered " + discoveredPlugins + " plugins, " + registeredPlugins + " registered, " + registeredBlocks + " blocks registered.");

        return Collections.unmodifiableList(discoveredAdapters);
    }

    private static List<String> dependencies(@NotNull Plugin plugin, @NotNull CustomBlockSystem system) {
        List<String> dependencies;
        try {
            dependencies = system.requiredNamespaces();
        } catch (RuntimeException exception) {
            Main.getInstance().getLogger().severe("Plugin " + plugin.getName()
                    + " failed while declaring BlockEngine namespace dependencies.");
            Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "BlockEngine discovery failure", exception);
            return null;
        }
        if (dependencies == null || dependencies.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String dependency : dependencies) {
            if (dependency == null || !NAMESPACE_PATTERN.matcher(dependency).matches()) {
                Main.getInstance().getLogger().severe("Plugin " + plugin.getName()
                        + " declared invalid BlockEngine namespace dependency: " + dependency);
                return null;
            }
            normalized.add(dependency);
        }
        return List.copyOf(normalized);
    }

    private static @NotNull Set<String> validNamespaces(@NotNull List<SystemEntry> systems) {
        Set<String> valid = new HashSet<>();
        for (SystemEntry entry : systems) {
            valid.add(entry.namespace());
        }

        boolean changed;
        do {
            changed = false;
            for (SystemEntry entry : systems) {
                if (!valid.contains(entry.namespace())) {
                    continue;
                }
                if (!valid.containsAll(entry.dependencies())) {
                    valid.remove(entry.namespace());
                    changed = true;
                }
            }
        } while (changed);

        return valid;
    }

    private static @NotNull String missing(@NotNull List<String> dependencies, @NotNull Set<String> validNamespaces) {
        List<String> missing = dependencies.stream()
                .filter(dependency -> !validNamespaces.contains(dependency))
                .sorted()
                .toList();
        return missing.isEmpty() ? "none" : String.join(", ", missing);
    }

    private record SystemEntry(
            @NotNull Plugin plugin,
            @NotNull CustomBlockSystem system,
            @NotNull String namespace,
            @NotNull List<String> dependencies
    ) {
    }
}
