package dev.auto.blockengine.runtime;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.types.BlockDefinition;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BlockTicker {
    private static final BlockTicker instance = new BlockTicker();

    private @Nullable BukkitTask task;

    private BlockTicker() {
    }

    public static @NotNull BlockTicker getInstance() {
        return instance;
    }

    public void register(@NotNull Main plugin) {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task == null) {
            return;
        }
        task.cancel();
        task = null;
    }

    private void tick() {
        List<RuntimeBlockView> blocks = new ArrayList<>();
        for (ChunkEngine.LoadedChunk chunk : ChunkEngine.chunks()) {
            blocks.addAll(chunk.blocks());
        }

        for (RuntimeBlockView view : blocks) {
            BlockDefinition definition = BlockRegistry.getBlock(view.storedBlock().blockId());
            if (definition == null || !definition.adapter().ticking()) {
                continue;
            }
            World world = Bukkit.getWorld(view.location().worldId());
            if (world == null) {
                continue;
            }
            Block block = world.getBlockAt(view.location().x(), view.location().y(), view.location().z());
            BlockContext context = BlockDataManager.getInstance().context(block, view, null);
            if (context == null) {
                continue;
            }
            try {
                definition.adapter().onTick(context);
            } catch (RuntimeException exception) {
                Main.getInstance().getLogger().warning("BlockEngine tick failed for "
                        + view.storedBlock().blockId() + " at " + view.location() + ": " + exception.getMessage());
                continue;
            }

            RuntimeBlockView current = ChunkEngine.getBlock(view.location());
            if (current != null && current.storedBlock().blockId().equals(context.blockId())) {
                BlockDataManager.getInstance().save(block, context);
            }
        }
    }
}
