package dev.auto.blockengine.datapack;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DataBlockPacks {
    private static final List<BlockPack> loadedPacks = new ArrayList<>();
    private static final List<String> loadErrors = new ArrayList<>();

    private DataBlockPacks() {
    }

    public static @NotNull List<BlockAdapter> loadAndRegister() {
        loadedPacks.clear();
        loadErrors.clear();

        Path dataFolder = Main.getInstance().getDataFolder().toPath();
        Path packsRoot = dataFolder.resolve("expansion").resolve("packs");
        Path extractedRoot = dataFolder.resolve("generated-expansion-packs").resolve("extracted");
        try {
            java.nio.file.Files.createDirectories(packsRoot);
        } catch (IOException exception) {
            Main.getInstance().getLogger().warning("Failed to create BlockEngine expansion pack folder: "
                    + exception.getMessage());
        }
        BlockPackLoader.Result result = BlockPackLoader.load(packsRoot, extractedRoot);
        loadErrors.addAll(result.errors());
        for (String error : result.errors()) {
            Main.getInstance().getLogger().warning("Failed to load BlockEngine data pack: " + error);
        }

        List<BlockAdapter> adapters = new ArrayList<>();
        for (BlockPack pack : result.packs()) {
            try {
                validateNoDuplicateIds(pack);
                NamespaceRegistry.load(pack.namespace());
                for (BlockPackBlock block : pack.blocks()) {
                    DataBlockAdapter adapter = new DataBlockAdapter(block);
                    BlockRegistry.registerBlock(adapter, pack.namespace());
                    adapters.add(adapter);
                }
                loadedPacks.add(pack);
                Main.getInstance().getLogger().info("Loaded BlockEngine data pack " + pack.namespace()
                        + " with " + pack.blocks().size() + " blocks.");
            } catch (RuntimeException exception) {
                loadErrors.add(pack.folder() + ": " + exception.getMessage());
                Main.getInstance().getLogger().warning("Failed to register BlockEngine data pack "
                        + pack.namespace() + ": " + exception.getMessage());
            }
        }

        return Collections.unmodifiableList(adapters);
    }

    private static void validateNoDuplicateIds(@NotNull BlockPack pack) {
        Set<String> ids = new HashSet<>();
        for (BlockPackBlock block : pack.blocks()) {
            String id = pack.namespace() + ":" + block.name();
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate block id inside data pack: " + id);
            }
            if (BlockRegistry.getBlock(pack.namespace(), block.name()) != null) {
                throw new IllegalArgumentException("Duplicate BlockEngine block id: " + id);
            }
        }
    }

    public static @NotNull List<BlockPack> loadedPacks() {
        return Collections.unmodifiableList(loadedPacks);
    }

    public static @NotNull List<String> loadErrors() {
        return Collections.unmodifiableList(loadErrors);
    }
}
