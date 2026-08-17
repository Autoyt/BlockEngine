package dev.auto.blockengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.retrooper.packetevents.PacketEvents;
import dev.auto.blockengine.catalog.CatelogListeners;
import dev.auto.blockengine.commands.CatelogCommand;
import dev.auto.blockengine.commands.DebugCommands;
import dev.auto.blockengine.defaultadapters.DebugBlocks;
import dev.auto.blockengine.listeners.BlockCommandOverrideListener;
import dev.auto.blockengine.listeners.ChunkListeners;
import dev.auto.blockengine.listeners.GameListener;
import dev.auto.blockengine.mining.BreakAnimationPacketBlocker;
import dev.auto.blockengine.mining.DebugBreakAnimationService;
import dev.auto.blockengine.mining.MiningManager;
import dev.auto.blockengine.registry.DiscoverySystem;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import dev.auto.blockengine.runtime.BlockEngineMutationBatcher;
import dev.auto.blockengine.visibility.VisibilityService;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import dev.auto.blockengine.runtime.BlockEngineChunkRuntime;

public final class Main extends JavaPlugin {
    @Getter
    public static Main instance;
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
        saveDefaultConfig();
        DiscoverySystem.discoverBlocks();
        DebugBlocks.register();
        ResourcePackManager.reload();
        VisibilityService.register(this);
        registerCommand("catelog", new CatelogCommand());
        registerCommand("blockenginedebug", new DebugCommands(this));
        registerCommand("tdebug", new DebugCommands(this));

        new CatelogListeners();
        new BlockCommandOverrideListener();
        new ChunkListeners();
        new GameListener();

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                BlockEngineChunkRuntime.loadChunk(chunk, VisibilityService.config());
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            VisibilityService.forceRecalculate(player);
        }
    }

    @Override
    public void onDisable() {
        BlockEngineMutationBatcher.flushNow();
        for (Player player : Bukkit.getOnlinePlayers()) {
            VisibilityService.cleanup(player);
        }
        MiningManager.cleanupAll();
        DebugBreakAnimationService.clearAll();
        BreakAnimationPacketBlocker.unregister();
        CatelogListeners.cleanup();
        ResourcePackManager.stop();
        BlockEngineMutationBatcher.clear();
        PacketEvents.getAPI().terminate();
    }
}
