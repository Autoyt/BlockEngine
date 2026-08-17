package dev.auto.blockengine.mining;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class DebugBreakAnimationManager {
    private static final int TICKS = 20;
    private static final DebugBreakAnimationManager instance = new DebugBreakAnimationManager();
    private final Map<BlockLocationKey, DebugSession> sessions = new HashMap<>();

    private DebugBreakAnimationManager() {
    }

    public static @NotNull DebugBreakAnimationManager getInstance() {
        return instance;
    }

    public void show(@NotNull Player player, @NotNull Block block, byte stage) {
        BlockLocationKey key = key(block);
        DebugSession previous = sessions.remove(key);
        if (previous != null) {
            previous.task().cancel();
            clear(block, previous.animationId());
        }

        int animationId = animationId(player, block);
        DebugSession session = new DebugSession(animationId, null);
        BukkitTask task = Main.getInstance().getServer().getScheduler().runTaskTimer(
                Main.getInstance(),
                new Runnable() {
                    private int ticks;

                    @Override
                    public void run() {
                        if (ticks++ >= TICKS) {
                            DebugSession current = sessions.remove(key);
                            if (current != null) {
                                current.task().cancel();
                                clear(block, current.animationId());
                            }
                            return;
                        }
                        send(block, animationId, stage);
                    }
                },
                0L,
                1L
        );
        session.task(task);
        sessions.put(key, session);
    }

    public boolean blocksReset(@NotNull Vector3i position, byte stage) {
        if (stage >= 0) {
            return false;
        }
        for (BlockLocationKey key : sessions.keySet()) {
            if (key.x() == position.getX()
                    && key.y() == position.getY()
                    && key.z() == position.getZ()) {
                return true;
            }
        }
        return false;
    }

    public void clearAll() {
        for (Map.Entry<BlockLocationKey, DebugSession> entry : sessions.entrySet()) {
            entry.getValue().task().cancel();
        }
        sessions.clear();
    }

    private void send(@NotNull Block block, int animationId, byte stage) {
        Vector3i position = new Vector3i(block.getX(), block.getY(), block.getZ());
        double maxDistanceSquared = Math.pow((Main.getInstance().getServer().getViewDistance() + 1) * 16.0, 2.0);
        for (Player viewer : block.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(block.getLocation()) > maxDistanceSquared) {
                continue;
            }
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                    viewer,
                    new WrapperPlayServerBlockBreakAnimation(animationId, position, stage)
            );
        }
    }

    private void clear(@NotNull Block block, int animationId) {
        send(block, animationId, (byte) -1);
    }

    private int animationId(@NotNull Player player, @NotNull Block block) {
        int result = 17;
        result = 31 * result + player.getEntityId();
        result = 31 * result + block.getX();
        result = 31 * result + block.getY();
        result = 31 * result + block.getZ();
        return result;
    }

    private @NotNull BlockLocationKey key(@NotNull Block block) {
        return new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
    }

    private static final class DebugSession {
        private final int animationId;
        private BukkitTask task;

        private DebugSession(int animationId, BukkitTask task) {
            this.animationId = animationId;
            this.task = task;
        }

        private int animationId() {
            return animationId;
        }

        private BukkitTask task() {
            return task;
        }

        private void task(BukkitTask task) {
            this.task = task;
        }
    }
}

