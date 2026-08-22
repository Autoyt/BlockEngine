package dev.auto.blockengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.retrooper.packetevents.PacketEvents;
import dev.auto.blockengine.api.BlockEngine;
import dev.auto.blockengine.catalog.CatalogListeners;
import dev.auto.blockengine.commands.CatalogCommand;
import dev.auto.blockengine.commands.DebugCommands;
import dev.auto.blockengine.commands.OverideFillCommand;
import dev.auto.blockengine.defaultadapters.DebugBlocks;
import dev.auto.blockengine.entity.ManagedDisplayManager;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.listeners.GameListener;
import dev.auto.blockengine.registry.DiscoverySystem;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.visibility.VisibilityManager;
import dev.auto.blockengine.world.ManagedWorld;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
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
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();
        BlockEngine.setManagedDisplayService(ManagedDisplayManager.getInstance());
        BlockEngine.setManagedWorldFactory(ManagedWorld::new);
        configSave(false);
        DiscoverySystem.discoverBlocks();
        DebugBlocks.register();
        ResourcePackManager.getInstance().reload();
        VisibilityManager.getInstance().register(this);
        BlockIntegrityManager.getInstance().register(this);
        registerCommand("catalog", new CatalogCommand());
        DebugCommands debugCommands = new DebugCommands(this);
        registerCommand("debug", debugCommands);
        registerCommand("blockenginedebug", debugCommands);

        new CatalogListeners();
        new OverideFillCommand();
        new GameListener();

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                ChunkEngine.load(chunk, VisibilityManager.getInstance().config());
                BlockIntegrityManager.getInstance().enqueue(chunk);
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            VisibilityManager.getInstance().forceRecalculate(player);
        }
    }

    @Override
    public void onDisable() {
        ChunkEngine.flushNow();
        for (Player player : Bukkit.getOnlinePlayers()) {
            VisibilityManager.getInstance().cleanup(player);
        }
        CatalogListeners.cleanup();
        ResourcePackManager.getInstance().stop();
        BlockIntegrityManager.getInstance().stop();
        ManagedDisplayManager.getInstance().clear();
        BlockEngine.clearManagedDisplayService();
        BlockEngine.clearManagedWorldFactory();
        ChunkEngine.clear();
        PacketEvents.getAPI().terminate();
    }

    public void configSave(boolean overwriteFromJar) {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveResource("config.yml", overwriteFromJar);
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







