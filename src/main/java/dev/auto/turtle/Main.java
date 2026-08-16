package dev.auto.turtle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.retrooper.packetevents.PacketEvents;
import dev.auto.turtle.catalog.CatelogListeners;
import dev.auto.turtle.commands.CatelogCommand;
import dev.auto.turtle.commands.DebugCommands;
import dev.auto.turtle.defaultadapters.DebugBlocks;
import dev.auto.turtle.listeners.BlockCommandOverrideListener;
import dev.auto.turtle.listeners.ChunkListeners;
import dev.auto.turtle.listeners.GameListener;
import dev.auto.turtle.mining.BreakAnimationPacketBlocker;
import dev.auto.turtle.mining.MiningManager;
import dev.auto.turtle.registry.DiscoverySystem;
import dev.auto.turtle.resourcepack.ResourcePackManager;
import dev.auto.turtle.visibility.VisibilityService;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import dev.auto.turtle.runtime.TurtleChunkRuntime;

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
        registerCommand("turtledebug", new DebugCommands(this));
        registerCommand("tdebug", new DebugCommands(this));

        new CatelogListeners();
        new BlockCommandOverrideListener();
        new ChunkListeners();
        new GameListener();

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                TurtleChunkRuntime.loadChunk(chunk, VisibilityService.config());
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            VisibilityService.forceRecalculate(player);
        }
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            VisibilityService.cleanup(player);
        }
        MiningManager.cleanupAll();
        BreakAnimationPacketBlocker.unregister();
        CatelogListeners.cleanup();
        ResourcePackManager.stop();
        PacketEvents.getAPI().terminate();
    }
}
