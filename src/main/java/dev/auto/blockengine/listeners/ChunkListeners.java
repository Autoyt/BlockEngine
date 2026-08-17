package dev.auto.blockengine.listeners;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.runtime.BlockEngineChunkRuntime;
import dev.auto.blockengine.runtime.BlockEngineMutationBatcher;
import dev.auto.blockengine.types.ChunkKey;
import dev.auto.blockengine.visibility.VisibilityService;
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
        BlockEngineChunkRuntime.loadChunk(chunk, VisibilityService.config());
        VisibilityService.refreshPlayersNear(ChunkKey.from(chunk));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        BlockEngineMutationBatcher.flushNow();
        ChunkKey key = ChunkKey.from(event.getChunk());
        BlockEngineChunkRuntime.unloadChunk(event.getChunk());
        VisibilityService.removeChunkDisplays(key);
    }
}
