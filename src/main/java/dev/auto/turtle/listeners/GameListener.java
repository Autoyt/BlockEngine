package dev.auto.turtle.listeners;

import dev.auto.turtle.Main;
import dev.auto.turtle.entity.TurtleBlockOrchestrator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

public class GameListener implements Listener {
    public GameListener() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    // TODO chunk pdc handling. While moving when the player slows down below certain point get their chunks around them and start loading the blocks in.
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final UUID id = player.getUniqueId();

        TurtleBlockOrchestrator.updateVisibility(player);
    }
}
