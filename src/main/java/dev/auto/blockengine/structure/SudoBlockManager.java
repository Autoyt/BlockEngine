package dev.auto.blockengine.structure;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.display.DisplayPersistence;
import dev.auto.blockengine.api.display.DisplaySpec;
import dev.auto.blockengine.entity.ManagedDisplayManager;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.placement.PlacementManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class SudoBlockManager {
    public static final String PERMISSION = "blockengine.structure";
    public static final String PREVIEW_DISPLAY_KEY = "sudo-preview";

    private static final NamespacedKey SUDO_MARKER_KEY = new NamespacedKey(Main.getInstance(), "sudo_marker");
    private static final NamespacedKey MARKER_BLOCK_ID_KEY = new NamespacedKey(Main.getInstance(), "sudo_marker_block_id");
    private static final NamespacedKey MARKER_STATE_ID_KEY = new NamespacedKey(Main.getInstance(), "sudo_marker_state_id");
    private static final NamespacedKey MARKER_PREVIEW_ID_KEY = new NamespacedKey(Main.getInstance(), "sudo_marker_preview_id");
    private static final NamespacedKey TRANSFORMER_KEY = new NamespacedKey(Main.getInstance(), "sudo_structure_transformer");
    private static final int MAX_APPLY_ATTEMPTS = 20;

    private static final SudoBlockManager instance = new SudoBlockManager();
    private final @NotNull Queue<PendingConversion> pendingConversions = new ConcurrentLinkedQueue<>();
    private final @NotNull AtomicBoolean applyScheduled = new AtomicBoolean();

    private SudoBlockManager() {
    }

    public static @NotNull SudoBlockManager getInstance() {
        return instance;
    }

    public static @NotNull NamespacedKey transformerKey() {
        return TRANSFORMER_KEY;
    }

    public boolean isSudoMarker(@NotNull Block block) {
        return block.getState(false) instanceof TileState state && markerBlockId(state) != null;
    }

    public @Nullable String markerBlockId(@NotNull Block block) {
        return block.getState(false) instanceof TileState state ? markerBlockId(state) : null;
    }

    public @Nullable String markerStateId(@NotNull Block block) {
        return block.getState(false) instanceof TileState state ? markerStateId(state) : null;
    }

    public @Nullable String markerBlockId(@NotNull BlockState state) {
        if (!(state instanceof TileState tileState)) {
            return null;
        }
        return markerBlockId(tileState);
    }

    public @Nullable String markerStateId(@NotNull BlockState state) {
        if (!(state instanceof TileState tileState)) {
            return null;
        }
        return markerStateId(tileState);
    }

    public void placeMarker(@NotNull Block block, @NotNull String blockId, @Nullable String stateId) {
        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (definition == null) {
            return;
        }
        String resolvedState = resolveState(definition, stateId);
        block.setType(Material.CHEST, false);
        writeMarker(block, definition.id(), resolvedState, null);
        spawnPreview(block, definition, resolvedState);
    }

    public boolean hidePreview(@NotNull Block block) {
        TileState state = tileState(block);
        if (state == null || markerBlockId(state) == null) {
            return false;
        }

        UUID previewId = previewId(state);
        boolean removed = previewId != null && ManagedDisplayManager.getInstance().remove(previewId);
        state.getPersistentDataContainer().remove(MARKER_PREVIEW_ID_KEY);
        state.update(true, false);
        return removed;
    }

    public void removePreviewIfMarker(@NotNull Block block) {
        TileState state = tileState(block);
        if (state == null || markerBlockId(state) == null) {
            return;
        }
        UUID previewId = previewId(state);
        if (previewId != null) {
            ManagedDisplayManager.getInstance().remove(previewId);
        }
    }

    public boolean convertCustomBlockToMarker(@NotNull Block block, @NotNull RuntimeBlockView customBlock) {
        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return false;
        }

        BlockIntegrityManager.getInstance().clearRecord(
                block,
                customBlock,
                dev.auto.blockengine.api.event.BlockEngineModificationEvent.Action.CLEAR_CUSTOM_BLOCK
        );
        placeMarker(block, definition.id(), customBlock.storedBlock().stateId());
        return true;
    }

    public void recordStructureMarker(@NotNull World world, int x, int y, int z, @NotNull String blockId, @Nullable String stateId) {
        if (BlockRegistry.getBlock(blockId) == null) {
            return;
        }
        pendingConversions.add(new PendingConversion(world.getUID(), x, y, z, blockId, stateId, 0));
        scheduleApply();
    }

    public void flushPendingNow() {
        applyPending(true);
        ChunkEngine.flushNow();
    }

    private void spawnPreview(@NotNull Block block, @NotNull BlockDefinition definition, @NotNull String stateId) {
        removePreviewIfMarker(block);
        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        DisplaySpec spec = DisplaySpec.builder(location)
                .itemStack(ItemManager.display(definition, stateId))
                .scale(2.0f, 2.0f, 2.0f)
                .displayContext(DisplaySpec.DISPLAY_CONTEXT_FIXED)
                .brightness(15)
                .viewRange(1.25f)
                .shadowRadius(0.0f)
                .shadowStrength(0.0f)
                .build();
        var handle = ManagedDisplayManager.getInstance().create(spec, DisplayPersistence.PERSISTENT_WORLD);
        writeMarker(block, definition.id(), stateId, handle.id());
    }

    private void writeMarker(
            @NotNull Block block,
            @NotNull String blockId,
            @NotNull String stateId,
            @Nullable UUID previewId
    ) {
        TileState state = tileState(block);
        if (state == null) {
            return;
        }
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        pdc.set(SUDO_MARKER_KEY, PersistentDataType.BOOLEAN, true);
        pdc.set(MARKER_BLOCK_ID_KEY, PersistentDataType.STRING, blockId);
        pdc.set(MARKER_STATE_ID_KEY, PersistentDataType.STRING, stateId);
        if (previewId == null) {
            pdc.remove(MARKER_PREVIEW_ID_KEY);
        } else {
            pdc.set(MARKER_PREVIEW_ID_KEY, PersistentDataType.STRING, previewId.toString());
        }
        state.update(true, false);
    }

    private void scheduleApply() {
        if (!applyScheduled.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> applyPending(false), 1L);
    }

    private void applyPending(boolean shutdown) {
        applyScheduled.set(false);
        PendingConversion conversion;
        while ((conversion = pendingConversions.poll()) != null) {
            if (!apply(conversion, shutdown) && !shutdown && conversion.attempts() < MAX_APPLY_ATTEMPTS) {
                pendingConversions.add(conversion.nextAttempt());
            }
        }
        if (!pendingConversions.isEmpty() && !shutdown) {
            scheduleApply();
        }
    }

    private boolean apply(@NotNull PendingConversion conversion, boolean shutdown) {
        World world = Bukkit.getWorld(conversion.worldId());
        BlockDefinition definition = BlockRegistry.getBlock(conversion.blockId());
        if (world == null || definition == null) {
            return true;
        }
        if (!world.isChunkLoaded(conversion.x() >> 4, conversion.z() >> 4)) {
            if (shutdown) {
                return true;
            }
            world.getChunkAt(conversion.x() >> 4, conversion.z() >> 4);
        }

        Block block = world.getBlockAt(conversion.x(), conversion.y(), conversion.z());
        if (block.getType() != Material.BARRIER && !isSudoMarker(block)) {
            return shutdown;
        }

        if (isSudoMarker(block)) {
            removePreviewIfMarker(block);
            block.setType(Material.BARRIER, false);
        }
        return PlacementManager.getInstance().place(block, definition, null, null, conversion.stateId());
    }

    private @Nullable TileState tileState(@NotNull Block block) {
        return block.getState(false) instanceof TileState state ? state : null;
    }

    private @Nullable String markerBlockId(@NotNull TileState state) {
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        Boolean marker = pdc.get(SUDO_MARKER_KEY, PersistentDataType.BOOLEAN);
        if (!Boolean.TRUE.equals(marker)) {
            return null;
        }
        return pdc.get(MARKER_BLOCK_ID_KEY, PersistentDataType.STRING);
    }

    private @Nullable String markerStateId(@NotNull TileState state) {
        return state.getPersistentDataContainer().get(MARKER_STATE_ID_KEY, PersistentDataType.STRING);
    }

    private @Nullable UUID previewId(@NotNull TileState state) {
        String id = state.getPersistentDataContainer().get(MARKER_PREVIEW_ID_KEY, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private @NotNull String resolveState(@NotNull BlockDefinition definition, @Nullable String stateId) {
        if (stateId != null && definition.apiDefinition().states().containsKey(stateId)) {
            return stateId;
        }
        return definition.apiDefinition().defaultState();
    }

    private record PendingConversion(
            @NotNull UUID worldId,
            int x,
            int y,
            int z,
            @NotNull String blockId,
            @Nullable String stateId,
            int attempts
    ) {
        private @NotNull PendingConversion nextAttempt() {
            return new PendingConversion(worldId, x, y, z, blockId, stateId, attempts + 1);
        }
    }
}
