package dev.auto.turtle.listeners;

import dev.auto.turtle.Main;
import dev.auto.turtle.visibility.VisibilityService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class GameListener implements Listener {
    public GameListener() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        VisibilityService.handleMove(event);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        VisibilityService.forceRecalculate(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        VisibilityService.forceRecalculate(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        VisibilityService.cleanup(event.getPlayer());
    }
}
