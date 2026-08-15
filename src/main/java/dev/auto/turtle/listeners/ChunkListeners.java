package dev.auto.turtle.listeners;

import dev.auto.turtle.Main;
import dev.auto.turtle.runtime.TurtleChunkRuntime;
import dev.auto.turtle.types.ChunkKey;
import dev.auto.turtle.visibility.VisibilityService;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkListeners implements Listener {
    public ChunkListeners() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        TurtleChunkRuntime.loadChunk(chunk, VisibilityService.config());
        VisibilityService.refreshPlayersNear(ChunkKey.from(chunk));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        ChunkKey key = ChunkKey.from(event.getChunk());
        TurtleChunkRuntime.unloadChunk(event.getChunk());
        VisibilityService.removeChunkDisplays(key);
    }
}
