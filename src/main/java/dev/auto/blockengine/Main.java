package dev.auto.blockengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.auto.blockengine.api.BlockEngine;
import dev.auto.blockengine.catalog.CatalogListeners;
import dev.auto.blockengine.commands.BlockEngineCommand;
import dev.auto.blockengine.commands.CatalogCommand;
import dev.auto.blockengine.commands.DebugCommands;
import dev.auto.blockengine.commands.OverideFillCommand;
import dev.auto.blockengine.commands.PerfCommand;
import dev.auto.blockengine.creative.CreativeInventoryListeners;
import dev.auto.blockengine.creative.CreativeInventoryManager;
import dev.auto.blockengine.defaultadapters.DebugBlocks;
import dev.auto.blockengine.defaultadapters.DemoInventoryBlockAdapter;
import dev.auto.blockengine.entity.ManagedDisplayManager;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.inspection.RuntimeInspectionService;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.listeners.GameListener;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.DiscoverySystem;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import dev.auto.blockengine.runtime.BlockTicker;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.GravityManager;
import dev.auto.blockengine.structure.SudoBlockListeners;
import dev.auto.blockengine.structure.SudoBlockManager;
import dev.auto.blockengine.visibility.VisibilityManager;
import dev.auto.blockengine.world.ManagedWorld;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin implements Listener {
    private GameListener gameListener;

    @Getter
    private static Main instance;
    @Getter
    private static final ObjectMapper jsonMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Getter
    private static final Material backingBlock = Material.BARRIER;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        BlockEngine.setManagedDisplayService(ManagedDisplayManager.getInstance());
        BlockEngine.setManagedWorldFactory(ManagedWorld::new);
        BlockEngine.setInspectionService(new RuntimeInspectionService());

        configSave(false);
        ItemManager.initialize(this);
        getServer().getPluginManager().registerEvents(this, this);
        ResourcePackManager.getInstance().prepare();
        VisibilityManager.getInstance().register(this);
        BlockIntegrityManager.getInstance().register(this);
        BlockTicker.getInstance().register(this);

        DebugCommands debugCommands = new DebugCommands(this);
        BlockEngineCommand blockEngineCommand = new BlockEngineCommand(this, debugCommands);

        registerCommand("blockengine", blockEngineCommand);
        registerCommand("be", blockEngineCommand);
        registerCommand("perf", new PerfCommand(debugCommands));
        registerCommand("catalog", new CatalogCommand());

        new CatalogListeners();
        new CreativeInventoryListeners();
        new OverideFillCommand();
        new SudoBlockListeners();
        gameListener = new GameListener();

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                ChunkEngine.load(chunk, VisibilityManager.getInstance().config());
                BlockIntegrityManager.getInstance().enqueue(chunk);
            }
        }

        Bukkit.getScheduler().runTask(this, this::reloadDiscoveredSystems);
    }

    private void reloadDiscoveredSystems() {
        DiscoverySystem.discoverBlocks();
        DebugBlocks.register();

        // Spawn chunks can load before contributors register their ticking and display definitions.
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                ChunkEngine.Key key = ChunkEngine.Key.from(chunk);
                ChunkEngine.LoadedChunk loaded = ChunkEngine.get(key);
                if (loaded != null) {
                    BlockTicker.getInstance().loadChunk(key, loaded);
                    GravityManager.getInstance().queueLoaded(loaded);
                }
                SudoBlockManager.getInstance().convertGeneratedMarkers(chunk);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            VisibilityManager.getInstance().forceRecalculate(player);
        }

        CreativeInventoryManager.writeBootstrapManifest(BlockRegistry.getBlocks());
        ResourcePackManager.getInstance().reload();
    }

    @Override
    public void onDisable() {
        if (gameListener != null) {
            gameListener.stop();
        }
        GravityManager.getInstance().clear();
        SudoBlockManager.getInstance().flushPendingNow();
        ChunkEngine.flushNow();

        for (Player player : Bukkit.getOnlinePlayers()) {
            VisibilityManager.getInstance().cleanup(player);
        }

        CatalogListeners.cleanup();
        DemoInventoryBlockAdapter.closeMenus();
        BlockRegistry.clear();
        ItemManager.shutdown();
        ResourcePackManager.getInstance().stop();
        BlockIntegrityManager.getInstance().stop();
        BlockTicker.getInstance().stop();
        ManagedDisplayManager.getInstance().clear();
        BlockEngine.clearManagedDisplayService();
        BlockEngine.clearManagedWorldFactory();
        BlockEngine.clearInspectionService();
        ChunkEngine.clear();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (BlockRegistry.hasBlocks(event.getPlugin())) {
            // Pending structure conversions still need this contributor's definitions.
            SudoBlockManager.getInstance().flushPendingNow();
        }

        if (event.getPlugin().getName().equals("AutoLib")) {
            ItemManager.shutdown();
        }
        BlockRegistry.removeAll(event.getPlugin());
    }

    public void configSave(boolean overwriteFromJar) {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        if (overwriteFromJar || !new java.io.File(getDataFolder(), "config.yml").isFile()) {
            saveResource("config.yml", overwriteFromJar);
        }
        reloadConfig();

        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    public static void serverThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Managed world API must be used on the server thread.");
        }
    }

    public static boolean isServerThread() {
        return Bukkit.isPrimaryThread();
    }
}
