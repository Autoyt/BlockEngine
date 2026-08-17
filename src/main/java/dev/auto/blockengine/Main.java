package dev.auto.blockengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.retrooper.packetevents.PacketEvents;
import dev.auto.blockengine.catalog.CatalogListeners;
import dev.auto.blockengine.commands.CatalogCommand;
import dev.auto.blockengine.commands.DebugCommands;
import dev.auto.blockengine.defaultadapters.DebugBlocks;
import dev.auto.blockengine.listeners.BlockCommandOverrideListener;
import dev.auto.blockengine.listeners.ChunkListeners;
import dev.auto.blockengine.listeners.GameListener;
import dev.auto.blockengine.mining.BreakAnimationPacketBlocker;
import dev.auto.blockengine.mining.DebugBreakAnimationManager;
import dev.auto.blockengine.mining.MiningManager;
import dev.auto.blockengine.registry.DiscoverySystem;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import dev.auto.blockengine.runtime.BlockEngineMutationBatcher;
import dev.auto.blockengine.visibility.VisibilityManager;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import dev.auto.blockengine.runtime.BlockEngineChunkRuntime;

public final class Main extends JavaPlugin {
    @Getter
    private static Main instance;
    @Getter
    private static final ObjectMapper jsonMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

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
        BreakAnimationPacketBlocker.register();
        configSave(false);
        DiscoverySystem.discoverBlocks();
        DebugBlocks.register();
        ResourcePackManager.getInstance().reload();
        VisibilityManager.getInstance().register(this);
        registerCommand("catalog", new CatalogCommand());
        DebugCommands debugCommands = new DebugCommands(this);
        registerCommand("debug", debugCommands);
        registerCommand("blockenginedebug", debugCommands);

        new CatalogListeners();
        new BlockCommandOverrideListener();
        new ChunkListeners();
        new GameListener();

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                BlockEngineChunkRuntime.loadChunk(chunk, VisibilityManager.getInstance().config());
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            VisibilityManager.getInstance().forceRecalculate(player);
        }
    }

    @Override
    public void onDisable() {
        BlockEngineMutationBatcher.flushNow();
        for (Player player : Bukkit.getOnlinePlayers()) {
            VisibilityManager.getInstance().cleanup(player);
        }
        MiningManager.getInstance().cleanupAll();
        DebugBreakAnimationManager.getInstance().clearAll();
        BreakAnimationPacketBlocker.unregister();
        CatalogListeners.cleanup();
        ResourcePackManager.getInstance().stop();
        BlockEngineMutationBatcher.clear();
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
}







