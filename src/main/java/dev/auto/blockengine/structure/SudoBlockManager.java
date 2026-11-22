package dev.auto.blockengine.structure;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.display.DisplayPersistence;
import dev.auto.blockengine.api.display.DisplaySpec;
import dev.auto.blockengine.entity.ManagedDisplayManager;
import dev.auto.blockengine.integrity.BlockIntegrityManager;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.placement.PlacementManager;
import dev.auto.blockengine.placement.BlockOrientation;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.BlockState;
import org.bukkit.block.Structure;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.block.structure.UsageMode;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BlockTransformer;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.BlockVector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SudoBlockManager {
    public static final String PERMISSION = "blockengine.structure";
    public static final String PREVIEW_DISPLAY_KEY = "sudo-preview";

    private static final NamespacedKey SUDO_MARKER_KEY = new NamespacedKey("blockengine", "sudo_marker");
    private static final NamespacedKey MARKER_BLOCK_ID_KEY = new NamespacedKey("blockengine", "sudo_marker_block_id");
    private static final NamespacedKey MARKER_STATE_ID_KEY = new NamespacedKey("blockengine", "sudo_marker_state_id");
    private static final NamespacedKey MARKER_PREVIEW_ID_KEY = new NamespacedKey("blockengine", "sudo_marker_preview_id");
    private static final NamespacedKey GENERATED_MARKER_KEY = new NamespacedKey("blockengine", "generated_marker");
    private static final NamespacedKey MARKER_ORIENTATION_KEY = new NamespacedKey("blockengine", "sudo_marker_orientation");
    private static final NamespacedKey TRANSFORMER_KEY = new NamespacedKey("blockengine", "sudo_structure_transformer");
    private static final int MAX_APPLY_ATTEMPTS = 20;

    private static final SudoBlockManager instance = new SudoBlockManager();
    private final @NotNull Queue<PendingConversion> pendingConversions = new ConcurrentLinkedQueue<>();
    private final @NotNull ConcurrentMap<BlockLocationKey, UUID> feedbackIds = new ConcurrentHashMap<>();
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
        placeMarker(block, blockId, stateId, BlockOrientation.IDENTITY);
    }

    public void placeMarker(Block block, String blockId, @Nullable String stateId, BlockOrientation orientation) {
        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (definition == null) {
            return;
        }
        String resolvedState = resolveState(definition, stateId);
        removePreviewIfMarker(block);
        block.setType(Material.CHEST, false);
        writeMarker(block, definition.id(), resolvedState, orientation);
        spawnPreview(block, definition, resolvedState, orientation);
    }

    public boolean hidePreview(@NotNull Block block) {
        TileState state = tileState(block);
        if (state == null || markerBlockId(state) == null) {
            return false;
        }

        UUID previewId = previewId(block, state);
        boolean removed = previewId != null && ManagedDisplayManager.getInstance().remove(previewId);
        state.getPersistentDataContainer().remove(MARKER_PREVIEW_ID_KEY);
        state.update(true, false);
        return removed;
    }

    public @NotNull PreviewToggle togglePreview(@NotNull Block block) {
        TileState state = tileState(block);
        if (state == null) {
            return PreviewToggle.NOT_A_MARKER;
        }

        String blockId = markerBlockId(state);
        if (blockId == null) {
            return PreviewToggle.NOT_A_MARKER;
        }
        UUID previewId = previewId(block, state);
        if (previewId != null && ManagedDisplayManager.getInstance().get(previewId) != null) {
            ManagedDisplayManager.getInstance().remove(previewId);
            state.getPersistentDataContainer().remove(MARKER_PREVIEW_ID_KEY);
            state.update(true, false);
            return PreviewToggle.HIDDEN;
        }

        BlockDefinition definition = BlockRegistry.getBlock(blockId);
        if (definition == null) {
            return PreviewToggle.NOT_A_MARKER;
        }
        spawnPreview(block, definition, resolveState(definition, markerStateId(state)), markerOrientation(state));
        return PreviewToggle.VISIBLE;
    }

    public void removePreviewIfMarker(@NotNull Block block) {
        TileState state = tileState(block);
        if (state == null || markerBlockId(state) == null) {
            return;
        }
        UUID previewId = previewId(block, state);
        if (previewId != null) {
            ManagedDisplayManager.getInstance().remove(previewId);
        }
        state.getPersistentDataContainer().remove(MARKER_PREVIEW_ID_KEY);
        state.update(true, false);
    }

    public boolean convertCustomBlockToMarker(@NotNull Block block, @NotNull RuntimeBlockView customBlock) {
        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return false;
        }

        BlockIntegrityManager.getInstance().clearRecord(
                block,
                customBlock,
                dev.auto.blockengine.api.event.BlockEngineModificationEvent.Action.CLEAR_CUSTOM_BLOCK,
                () -> placeMarker(block, definition.id(), customBlock.storedBlock().stateId(), customBlock.storedBlock().orientation())
        );
        return true;
    }

    public boolean convertMarkerToCustomBlock(@NotNull Block block) {
        return convertMarkerNow(block);
    }

    public void playWandFeedback(@NotNull Player player, @NotNull Block block, boolean sudoConversion) {
        BlockLocationKey blockKey = location(block);
        UUID previousId = feedbackIds.remove(blockKey);
        if (previousId != null) {
            ManagedDisplayManager.getInstance().remove(previousId);
        }

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        DisplaySpec spec = DisplaySpec.builder(center)
                .itemStack(ItemManager.wandFeedback(sudoConversion))
                .scale(2.03f, 2.03f, 2.03f)
                .displayContext(DisplaySpec.DISPLAY_CONTEXT_FIXED)
                .brightness(15)
                .viewRange(12.0f)
                .shadowRadius(0.0f)
                .shadowStrength(0.0f)
                .build();
        var handle = ManagedDisplayManager.getInstance().create(spec, DisplayPersistence.TRANSIENT);
        UUID feedbackId = handle.id();
        feedbackIds.put(blockKey, feedbackId);
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (feedbackIds.remove(blockKey, feedbackId)) {
                handle.remove();
            }
        }, 20L);

        World world = block.getWorld();
        world.spawnParticle(
                Particle.DUST,
                center,
                24,
                0.35,
                0.22,
                0.35,
                0.0,
                new Particle.DustOptions(Color.fromRGB(176, 72, 255), 1.25f)
        );
        if (sudoConversion) {
            player.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.7f);
            player.playSound(center, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.BLOCKS, 0.75f, 1.45f);
            player.playSound(center, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 0.45f, 1.25f);
        } else {
            player.playSound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.BLOCKS, 0.8f, 0.65f);
        }
    }

    public void recordStructureMarker(@NotNull World world, int x, int y, int z, @NotNull String blockId, @Nullable String stateId) {
        if (!Main.getInstance().isEnabled()) {
            return;
        }
        pendingConversions.add(new PendingConversion(world.getUID(), x, y, z, blockId, stateId, 0));
        scheduleApply();
    }

    public @NotNull BlockTransformer structureBlockTransformer() {
        return (region, x, y, z, blockState, transformationState) ->
                transformGeneratedMarker(region.getWorld(), x, y, z, blockState);
    }

    public @NotNull BlockState transformGeneratedMarker(
            @NotNull World world,
            int x,
            int y,
            int z,
            @NotNull BlockState blockState
    ) {
        String blockId = markerBlockId(blockState);
        if (blockId == null) {
            return blockState;
        }

        // Keep the id and state in the generated chunk until main-thread placement succeeds.
        TileState marker = (TileState) blockState.copy();
        marker.getPersistentDataContainer().set(GENERATED_MARKER_KEY, PersistentDataType.BOOLEAN, true);
        marker.getPersistentDataContainer().remove(MARKER_PREVIEW_ID_KEY);
        recordStructureMarker(world, x, y, z, blockId, markerStateId(blockState));
        return marker;
    }

    public void convertGeneratedMarkers(@NotNull Chunk chunk) {
        for (BlockState state : chunk.getTileEntities(block -> block.getType() == Material.CHEST, false)) {
            if (state instanceof TileState tile && generated(tile)) {
                convertMarkerNow(state.getBlock());
            }
        }
    }

    private boolean generated(@NotNull TileState state) {
        return Boolean.TRUE.equals(state.getPersistentDataContainer().get(GENERATED_MARKER_KEY, PersistentDataType.BOOLEAN));
    }

    public int convertLoadedMarkers(@NotNull World world, @NotNull BoundingBox area) {
        Main.serverThread();
        int minX = (int) Math.floor(area.getMinX());
        int minY = Math.max(world.getMinHeight(), (int) Math.floor(area.getMinY()));
        int minZ = (int) Math.floor(area.getMinZ());
        int maxX = (int) Math.floor(area.getMaxX());
        int maxY = Math.min(world.getMaxHeight() - 1, (int) Math.floor(area.getMaxY()));
        int maxZ = (int) Math.floor(area.getMaxZ());
        int converted = 0;
        for (int chunkX = minX >> 4; chunkX <= (maxX >> 4); chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= (maxZ >> 4); chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                for (BlockState state : chunk.getTileEntities(block -> block.getType() == Material.CHEST, false)) {
                    if (state.getX() >= minX && state.getX() <= maxX
                            && state.getY() >= minY && state.getY() <= maxY
                            && state.getZ() >= minZ && state.getZ() <= maxZ
                            && convertMarkerNow(state.getBlock())) {
                        converted++;
                    }
                }
            }
        }
        return converted;
    }

    public boolean convertMarkerNow(@NotNull Block block) {
        Main.serverThread();
        TileState state = tileState(block);
        if (state == null) {
            return false;
        }
        String blockId = markerBlockId(state);
        BlockDefinition definition = blockId == null ? null : BlockRegistry.getBlock(blockId);
        if (definition == null) {
            return false;
        }

        String stateId = resolveState(definition, markerStateId(state));
        UUID preview = previewId(block, state);
        if (!PlacementManager.getInstance().place(block, definition, null, null, stateId, markerOrientation(state))) {
            return false;
        }
        if (preview != null) {
            ManagedDisplayManager.getInstance().remove(preview);
        }
        return true;
    }

    public @Nullable BoundingBox structureLoadArea(@NotNull Block structureBlock) {
        BlockState state = structureBlock.getState(false);
        if (!(state instanceof Structure structure) || structure.getUsageMode() != UsageMode.LOAD) {
            return null;
        }

        BlockVector offset = structure.getRelativePosition();
        BlockVector size = structure.getStructureSize();
        if (size.getBlockX() <= 0 || size.getBlockY() <= 0 || size.getBlockZ() <= 0) {
            return null;
        }

        int startX = structureBlock.getX() + offset.getBlockX();
        int startY = structureBlock.getY() + offset.getBlockY();
        int startZ = structureBlock.getZ() + offset.getBlockZ();
        BlockVector farCorner = structureExtent(size, structure.getMirror(), structure.getRotation());
        int endX = startX + farCorner.getBlockX();
        int endY = startY + size.getBlockY() - 1;
        int endZ = startZ + farCorner.getBlockZ();
        return new BoundingBox(
                Math.min(startX, endX),
                Math.min(startY, endY),
                Math.min(startZ, endZ),
                Math.max(startX, endX),
                Math.max(startY, endY),
                Math.max(startZ, endZ)
        );
    }

    static BlockVector structureExtent(BlockVector size, Mirror mirror, StructureRotation rotation) {
        int x = (size.getBlockX() - 1) * (mirror == Mirror.FRONT_BACK ? -1 : 1);
        int z = (size.getBlockZ() - 1) * (mirror == Mirror.LEFT_RIGHT ? -1 : 1);
        int y = size.getBlockY() - 1;
        return switch (rotation) {
            case NONE -> new BlockVector(x, y, z);
            case CLOCKWISE_90 -> new BlockVector(-z, y, x);
            case CLOCKWISE_180 -> new BlockVector(-x, y, -z);
            case COUNTERCLOCKWISE_90 -> new BlockVector(z, y, -x);
        };
    }

    public void flushPendingNow() {
        applyPending(true);
        ChunkEngine.flushNow();
    }

    private void spawnPreview(Block block, BlockDefinition definition, String stateId, BlockOrientation orientation) {
        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        var rotation = orientation.rotation();
        DisplaySpec spec = DisplaySpec.builder(location)
                .itemStack(ItemManager.display(definition, stateId))
                .leftRotation(rotation.x(), rotation.y(), rotation.z(), rotation.w())
                .scale(2.0f, 2.0f, 2.0f)
                .displayContext(DisplaySpec.DISPLAY_CONTEXT_FIXED)
                .brightness(15)
                .viewRange(1.25f)
                .shadowRadius(0.0f)
                .shadowStrength(0.0f)
                .build();
        var handle = ManagedDisplayManager.getInstance().create(spec, DisplayPersistence.PERSISTENT_WORLD);
        TileState state = tileState(block);
        state.getPersistentDataContainer().set(MARKER_PREVIEW_ID_KEY, PersistentDataType.STRING, handle.id().toString());
        state.update(true, false);
    }

    private void writeMarker(
            @NotNull Block block,
            @NotNull String blockId,
            @NotNull String stateId,
            @NotNull BlockOrientation orientation
    ) {
        TileState state = tileState(block);
        if (state == null) {
            return;
        }
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        pdc.set(SUDO_MARKER_KEY, PersistentDataType.BOOLEAN, true);
        pdc.set(MARKER_BLOCK_ID_KEY, PersistentDataType.STRING, blockId);
        pdc.set(MARKER_STATE_ID_KEY, PersistentDataType.STRING, stateId);
        pdc.set(MARKER_ORIENTATION_KEY, PersistentDataType.STRING, orientation.name());
        pdc.remove(MARKER_PREVIEW_ID_KEY);
        // Minecraft rotates and mirrors chest facing when a structure is transformed.
        BlockFace facing = switch (orientation) {
            case NORTH -> BlockFace.NORTH;
            case EAST, AXIS_X -> BlockFace.EAST;
            case SOUTH, AXIS_Z -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
            default -> null;
        };
        if (facing != null && state.getBlockData() instanceof Directional chest) {
            chest.setFacing(facing);
            state.setBlockData(chest);
        }
        state.update(true, false);
    }

    private BlockOrientation markerOrientation(TileState marker) {
        String stored = marker.getPersistentDataContainer().get(MARKER_ORIENTATION_KEY, PersistentDataType.STRING);
        if (stored == null) {
            return BlockOrientation.IDENTITY;
        }
        try {
            BlockOrientation orientation = BlockOrientation.valueOf(stored);
            if (marker.getBlockData() instanceof Directional chest) {
                BlockFace facing = chest.getFacing();
                return switch (orientation) {
                    case NORTH, EAST, SOUTH, WEST -> BlockOrientation.valueOf(facing.name());
                    case AXIS_X, AXIS_Z -> (facing == BlockFace.EAST || facing == BlockFace.WEST)
                            ? BlockOrientation.AXIS_X : BlockOrientation.AXIS_Z;
                    default -> orientation;
                };
            }
            return orientation;
        } catch (IllegalArgumentException ignored) {
            return BlockOrientation.IDENTITY;
        }
    }

    private void scheduleApply() {
        if (!applyScheduled.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> applyPending(false), 1L);
    }

    private void applyPending(boolean shutdown) {
        applyScheduled.set(false);
        List<PendingConversion> retry = new ArrayList<>();
        PendingConversion conversion;
        while ((conversion = pendingConversions.poll()) != null) {
            if (!apply(conversion, shutdown) && !shutdown && conversion.attempts() < MAX_APPLY_ATTEMPTS) {
                retry.add(conversion.nextAttempt());
            }
        }
        pendingConversions.addAll(retry);
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
            return true; // Chunk load will pick up the persisted marker.
        }

        Block block = world.getBlockAt(conversion.x(), conversion.y(), conversion.z());
        TileState marker = tileState(block);
        if (marker != null && generated(marker) && conversion.blockId().equals(markerBlockId(marker))) {
            convertMarkerNow(block);
            return true;
        }
        return shutdown || block.getType() == Main.getBackingBlock();
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

    private @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private @Nullable UUID previewId(@NotNull Block block, @NotNull TileState state) {
        String id = state.getPersistentDataContainer().get(MARKER_PREVIEW_ID_KEY, PersistentDataType.STRING);
        if (id != null) {
            try {
                UUID stored = UUID.fromString(id);
                if (previewBelongsToBlock(block, stored)) {
                    return stored;
                }
            } catch (IllegalArgumentException ignored) {
                // Old markers may have a stale preview id.
            }
        }
        return nearbyPreviewId(block, state);
    }

    private @Nullable UUID nearbyPreviewId(@NotNull Block block, @NotNull TileState state) {
        String blockId = markerBlockId(state);
        BlockDefinition definition = blockId == null ? null : BlockRegistry.getBlock(blockId);
        if (definition == null) {
            return null;
        }
        ItemStack expected = ItemManager.display(definition, resolveState(definition, markerStateId(state)));
        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        return ManagedDisplayManager.getInstance().displaysNear(location, 0.001).stream()
                .filter(handle -> handle.persistence() == DisplayPersistence.PERSISTENT_WORLD)
                .filter(handle -> handle.spec().itemStack().isSimilar(expected))
                .map(handle -> handle.id())
                .findFirst()
                .orElse(null);
    }

    private boolean previewBelongsToBlock(@NotNull Block block, @NotNull UUID previewId) {
        var handle = ManagedDisplayManager.getInstance().get(previewId);
        if (handle == null) {
            return false;
        }
        DisplaySpec spec = handle.spec();
        return spec.worldId().equals(block.getWorld().getUID())
                && Math.abs(spec.x() - (block.getX() + 0.5)) < 0.001
                && Math.abs(spec.y() - (block.getY() + 0.5)) < 0.001
                && Math.abs(spec.z() - (block.getZ() + 0.5)) < 0.001;
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

    public enum PreviewToggle {
        VISIBLE,
        HIDDEN,
        NOT_A_MARKER
    }
}
