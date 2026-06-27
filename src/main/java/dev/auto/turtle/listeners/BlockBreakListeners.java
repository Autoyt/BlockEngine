package dev.auto.turtle.listeners;

import dev.auto.turtle.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;

public class BlockBreakListeners implements Listener {
    public BlockBreakListeners() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    @EventHandler
    public void onStartBreaking(BlockDamageEvent event) {
        final Player player = event.getPlayer();
        player.sendMessage("Attempting to break block: " + event.getBlock().getType().name());
        event.setCancelled(true);
    }

    @EventHandler
    public void onStopBreaking(BlockDamageAbortEvent event) {}

    @EventHandler
    public void onBreak(BlockBreakEvent event) {}
}
