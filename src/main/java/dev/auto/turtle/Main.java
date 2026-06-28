package dev.auto.turtle;

import com.github.retrooper.packetevents.PacketEvents;
import dev.auto.turtle.api.CustomBlockSystem;
import dev.auto.turtle.commands.DebugCommands;
import dev.auto.turtle.listeners.BlockBreakListeners;
import dev.auto.turtle.listeners.GameListener;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    @Getter
    public static Main instance;

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
        registerCommand("turtledebug", new DebugCommands(this));
        registerCommand("tdebug", new DebugCommands(this));

        new BlockBreakListeners();
        new GameListener();
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
    }
}
