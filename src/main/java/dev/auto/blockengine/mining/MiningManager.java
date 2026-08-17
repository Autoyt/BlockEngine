package dev.auto.blockengine.mining;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import dev.auto.blockengine.Main;
import dev.auto.blockengine.defaultadapters.DebugBlocks;
import dev.auto.blockengine.entity.BlockEngineBlockOrchestrator;
import dev.auto.blockengine.entity.VirtualItemDisplay;
import dev.auto.blockengine.placement.BlockEngineBackingBlock;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.runtime.BlockEngineBlockContext;
import dev.auto.blockengine.runtime.BlockDataManager;
import dev.auto.blockengine.runtime.BlockEngineBlockRemover;
import dev.auto.blockengine.runtime.BlockEngineChunkRuntime;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MiningManager {
    private static final int TARGET_DISTANCE = 7;
    private static final MiningManager instance = new MiningManager();
    private final Map<UUID, MiningSession> sessions = new HashMap<>();

    private MiningManager() {
    }

    public static @NotNull MiningManager getInstance() {
        return instance;
    }

    public void start(@NotNull Player player, @NotNull Block block, @NotNull RuntimeBlockView customBlock) {
        MiningSession current = sessions.get(player.getUniqueId());
        if (current != null && sameBlock(current.block(), block)) {
            return;
        }
        stop(player);

        if (customBlock.storedBlock().unbreakable() || customBlock.storedBlock().blockId().equals(DebugBlocks.INVISIBLE_BLOCK_ID)) {
            return;
        }

        int animationId = animationId(player, block);

        MiningSession session = new MiningSession(player.getUniqueId(), block, animationId);
        sendStage(session, (byte) session.stage());
        BukkitTask task = Main.getInstance().getServer().getScheduler().runTaskTimer(
                Main.getInstance(),
                () -> tick(player, session, customBlock),
                1L,
                1L
        );
        session.task(task);
        sessions.put(player.getUniqueId(), session);
    }

    public void stop(@NotNull Player player) {
        MiningSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        clear(session);
    }

    public void abort(@NotNull Player player) {
        stop(player);
    }

    public boolean active(@NotNull Player player, @NotNull Block block) {
        MiningSession session = sessions.get(player.getUniqueId());
        return session != null && sameBlock(session.block(), block);
    }

    public void updateAim(@NotNull Player player) {
        MiningSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        Block target = player.getTargetBlockExact(TARGET_DISTANCE);
        if (target == null) {
            stop(player);
            return;
        }

        RuntimeBlockView customBlock = BlockEngineChunkRuntime.getBlock(location(target));
        if (customBlock == null) {
            stop(player);
            return;
        }
        if (sameBlock(session.block(), target)) {
            return;
        }
        start(player, target, customBlock);
    }

    public boolean blocksExternalClear(@NotNull Vector3i position, int animationId, byte stage) {
        if (stage >= 0) {
            return false;
        }
        for (MiningSession session : sessions.values()) {
            if (session.animationId() == animationId) {
                continue;
            }
            Block block = session.block();
            if (block.getX() == position.getX()
                    && block.getY() == position.getY()
                    && block.getZ() == position.getZ()) {
                return true;
            }
        }
        return false;
    }

    public void breakNow(@NotNull Player player, @NotNull Block block, @NotNull RuntimeBlockView customBlock) {
        if (customBlock.storedBlock().blockId().equals(DebugBlocks.INVISIBLE_BLOCK_ID)) {
            return;
        }
        stop(player);
        finish(player, block, customBlock, customBlock.storedBlock().dropInCreative());
    }

    public void cleanupAll() {
        for (UUID playerId : sessions.keySet().toArray(UUID[]::new)) {
            Player player = Main.getInstance().getServer().getPlayer(playerId);
            if (player != null) {
                stop(player);
            }
        }
    }

    private void tick(@NotNull Player player, @NotNull MiningSession session, @NotNull RuntimeBlockView customBlock) {
        if (!player.isOnline() || !near(player, session.block())) {
            stop(player);
            return;
        }

        int elapsed = session.tickAndGet();
        float progress = session.addProgress(MiningSpeedResolver.progressPerTick(player, customBlock));
        byte stage = stage(progress);
        if (session.stage(stage)) {
            sendStage(session, stage);
        }

        if (elapsed % 4 == 0) {
            playMiningSound(player, customBlock);
        }

        if (progress >= 1.0f) {
            sessions.remove(player.getUniqueId());
            clear(session);
            finish(player, session.block(), customBlock, MiningSpeedResolver.shouldDrop(player, customBlock));
        }
    }

    private void clear(@NotNull MiningSession session) {
        if (session.task() != null) {
            session.task().cancel();
        }
        sendStage(session, (byte) -1);
    }

    private void finish(@NotNull Player player, @NotNull Block block, @NotNull RuntimeBlockView customBlock, boolean drop) {
        BlockEngineBlockContext context = BlockDataManager.getInstance().context(block, customBlock, player);
        if (context != null && !context.adapter().onBreak(context)) {
            BlockDataManager.getInstance().save(block, context);
            return;
        }
        BlockEngineBlockRemover.remove(block, customBlock, drop);
    }

    private void sendStage(@NotNull MiningSession session, byte stage) {
        Vector3i position = new Vector3i(
                session.block().getX(),
                session.block().getY(),
                session.block().getZ()
        );
        List<Player> viewers = viewers(session.block());
        for (Player viewer : viewers) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                    viewer,
                    new WrapperPlayServerBlockBreakAnimation(session.animationId(), position, stage)
            );
        }
        sendOverlay(session, stage, viewers);
    }

    private void sendOverlay(
            @NotNull MiningSession session,
            byte stage,
            @NotNull List<Player> viewers
    ) {
        VirtualItemDisplay overlay = session.overlay();
        if (stage < 0) {
            if (overlay != null) {
                overlay.destroyForAll();
                BlockEngineBlockOrchestrator.freeId(overlay.getId());
                session.overlay(null);
            }
            return;
        }

        if (overlay == null) {
            overlay = new VirtualItemDisplay(BlockEngineBlockOrchestrator.nextId())
                    .location(session.block().getLocation().add(0.5, 0.5, 0.5))
                    .itemStack(breakOverlay(stage))
                    .displayContext(VirtualItemDisplay.DISPLAY_CONTEXT_FIXED)
                    .scale(2.025f, 2.025f, 2.025f)
                    .viewRange(1.25f)
                    .brightness(15, 15)
                    .shadowRadius(0.0f)
                    .shadowStrength(0.0f);
            session.overlay(overlay);
            overlay.spawnFor(viewers);
            return;
        }

        overlay.itemStack(breakOverlay(stage));
        overlay.updateMetadataFor(viewers);
    }

    private @NotNull ItemStack breakOverlay(byte stage) {
        ItemStack stack = new ItemStack(BlockEngineBackingBlock.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey(Main.getInstance(), "break_stage/" + Math.clamp(stage, 0, 9)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private @NotNull List<Player> viewers(@NotNull Block block) {
        double maxDistanceSquared = Math.pow((Main.getInstance().getServer().getViewDistance() + 1) * 16.0, 2.0);
        return block.getWorld().getPlayers().stream()
                .filter(viewer -> viewer.getLocation().distanceSquared(block.getLocation()) <= maxDistanceSquared)
                .toList();
    }

    private void playMiningSound(@NotNull Player player, @NotNull RuntimeBlockView customBlock) {
        String sound = "minecraft:block.stone.hit";
        BlockDefinition registered = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (registered != null) {
            try {
                sound = registered.apiDefinition()
                        .state(customBlock.storedBlock().stateId())
                        .sounds()
                        .mining();
            } catch (IllegalArgumentException ignored) {
                sound = "minecraft:block.stone.hit";
            }
        }

        player.playSound(
                new Location(
                        player.getWorld(),
                        customBlock.location().x() + 0.5,
                        customBlock.location().y() + 0.5,
                        customBlock.location().z() + 0.5
                ),
                NamespacedKey.fromString(sound) == null ? "minecraft:block.stone.hit" : sound,
                SoundCategory.BLOCKS,
                0.35f,
                1.0f
        );
    }

    private int animationId(@NotNull Player player, @NotNull Block block) {
        int result = player.getEntityId();
        result = 31 * result + block.getX();
        result = 31 * result + block.getY();
        result = 31 * result + block.getZ();
        return result;
    }

    private @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
    }

    private byte stage(float progress) {
        if (progress >= 1.0f) {
            return 9;
        }
        return (byte) Math.clamp((int) Math.floor(progress * 10.0f), 0, 9);
    }

    private boolean sameBlock(Block current, @NotNull Block expected) {
        return current != null
                && current.getWorld().equals(expected.getWorld())
                && current.getX() == expected.getX()
                && current.getY() == expected.getY()
                && current.getZ() == expected.getZ();
    }

    private boolean near(@NotNull Player player, @NotNull Block block) {
        if (!player.getWorld().equals(block.getWorld())) {
            return false;
        }
        return player.getEyeLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5)) <= 49.0;
    }

    private static final class MiningSession {
        private final @NotNull UUID playerId;
        private final @NotNull Block block;
        private final int animationId;
        private byte stage;
        private int elapsedTicks;
        private float progress;
        private BukkitTask task;
        private VirtualItemDisplay overlay;

        private MiningSession(@NotNull UUID playerId, @NotNull Block block, int animationId) {
            this.playerId = playerId;
            this.block = block;
            this.animationId = animationId;
            this.stage = 0;
        }

        private @NotNull Block block() {
            return block;
        }

        private int animationId() {
            return animationId;
        }

        private byte stage() {
            return stage;
        }

        private boolean stage(byte stage) {
            if (this.stage == stage) {
                return false;
            }
            this.stage = stage;
            return true;
        }

        private BukkitTask task() {
            return task;
        }

        private void task(BukkitTask task) {
            this.task = task;
        }

        private VirtualItemDisplay overlay() {
            return overlay;
        }

        private void overlay(VirtualItemDisplay overlay) {
            this.overlay = overlay;
        }

        private int tickAndGet() {
            return ++elapsedTicks;
        }

        private float addProgress(float amount) {
            progress += amount;
            return progress;
        }
    }
}


