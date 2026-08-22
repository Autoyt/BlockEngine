package dev.auto.blockengine.entity;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.display.DisplayPersistence;
import dev.auto.blockengine.api.display.DisplaySpec;
import dev.auto.blockengine.api.display.ManagedDisplayHandle;
import dev.auto.blockengine.api.display.ManagedDisplayService;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ManagedDisplayManager implements ManagedDisplayService {
    private static final ManagedDisplayManager instance = new ManagedDisplayManager();
    private final @NotNull Map<UUID, ManagedDisplay> displays = new HashMap<>();

    private ManagedDisplayManager() {
    }

    public static @NotNull ManagedDisplayManager getInstance() {
        return instance;
    }

    @Override
    public @NotNull ManagedDisplayHandle create(@NotNull DisplaySpec spec, @NotNull DisplayPersistence persistence) {
        ensureServerThread();
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(persistence, "persistence");
        if (persistence == DisplayPersistence.PERSISTENT_BLOCK_ATTACHED) {
            throw new IllegalArgumentException("Use createBlockAttached for block-attached displays.");
        }

        ManagedDisplay display = new ManagedDisplay(UUID.randomUUID(), persistence, null, null, spec);
        displays.put(display.id(), display);
        if (persistence == DisplayPersistence.PERSISTENT_WORLD) {
            ChunkEngine.data(chunk(spec)).setDisplay(ChunkEngine.StoredDisplay.from(display));
            ChunkEngine.changed(chunk(spec));
        }
        refresh(display);
        return display;
    }

    @Override
    public @NotNull ManagedDisplayHandle createBlockAttached(
            @NotNull Block block,
            @NotNull String key,
            @NotNull DisplaySpec spec
    ) {
        ensureServerThread();
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(spec, "spec");

        BlockLocationKey owner = location(block);
        ManagedDisplay display = new ManagedDisplay(UUID.randomUUID(), DisplayPersistence.PERSISTENT_BLOCK_ATTACHED, owner, key, spec);
        ChunkEngine.Data data = ChunkEngine.data(block.getChunk());
        data.setBlockDisplay(owner.x() & 15, owner.y(), owner.z() & 15, ChunkEngine.StoredDisplay.from(display));
        displays.put(display.id(), display);
        ChunkEngine.changed(block);
        refresh(display);
        return display;
    }

    @Override
    public @Nullable ManagedDisplayHandle get(@NotNull UUID id) {
        ManagedDisplay display = displays.get(id);
        return display == null || !display.valid() ? null : display;
    }

    @Override
    public @NotNull Collection<ManagedDisplayHandle> displays() {
        return Collections.unmodifiableCollection(new ArrayList<>(displays.values()));
    }

    @Override
    public @NotNull Collection<ManagedDisplayHandle> displaysNear(@NotNull Location location, double radius) {
        Objects.requireNonNull(location, "location");
        if (location.getWorld() == null) {
            return List.of();
        }
        double radiusSquared = radius * radius;
        UUID worldId = location.getWorld().getUID();
        List<ManagedDisplayHandle> nearby = new ArrayList<>();
        for (ManagedDisplay display : displays.values()) {
            DisplaySpec spec = display.spec();
            if (!spec.worldId().equals(worldId)) {
                continue;
            }
            double dx = spec.x() - location.getX();
            double dy = spec.y() - location.getY();
            double dz = spec.z() - location.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                nearby.add(display);
            }
        }
        return nearby;
    }

    @Override
    public boolean remove(@NotNull UUID id) {
        ensureServerThread();
        ManagedDisplay display = displays.get(id);
        return display != null && display.remove();
    }

    public void loadChunk(@NotNull ChunkEngine.Key chunkKey, @NotNull ChunkEngine.Data data) {
        removeLoadedForChunk(chunkKey);
        for (ChunkEngine.StoredDisplay stored : data.displays()) {
            displays.put(stored.id(), ManagedDisplay.from(stored));
        }
        for (ChunkEngine.StoredBlock block : data.blocks()) {
            BlockLocationKey owner = new BlockLocationKey(
                    chunkKey.worldId(),
                    (chunkKey.x() << 4) + block.localX(),
                    block.y(),
                    (chunkKey.z() << 4) + block.localZ()
            );
            for (ChunkEngine.StoredDisplay stored : block.displays()) {
                displays.put(stored.id(), ManagedDisplay.from(stored.withOwner(owner)));
            }
        }
    }

    public void unloadChunk(@NotNull ChunkEngine.Key chunkKey) {
        Iterator<ManagedDisplay> iterator = displays.values().iterator();
        while (iterator.hasNext()) {
            ManagedDisplay display = iterator.next();
            if (!storedIn(display, chunkKey)) {
                continue;
            }
            display.invalidate();
            iterator.remove();
        }
    }

    public @NotNull Collection<ManagedDisplay> loadedDisplays() {
        return Collections.unmodifiableCollection(displays.values());
    }

    public @NotNull DesiredDisplay desired(@NotNull ManagedDisplay display) {
        return new DesiredDisplay(display.id(), display.spec());
    }

    public @NotNull DesiredDisplay defaultBlockDisplay(@NotNull BlockLocationKey location, @NotNull ItemStack itemStack) {
        World world = Bukkit.getWorld(location.worldId());
        if (world == null) {
            throw new IllegalStateException("Cannot create default display for unloaded world " + location.worldId());
        }
        UUID id = UUID.nameUUIDFromBytes(("blockengine:default:"
                + location.worldId() + ":" + location.x() + ":" + location.y() + ":" + location.z())
                .getBytes(StandardCharsets.UTF_8));
        DisplaySpec spec = DisplaySpec.builder(new Location(
                        world,
                        location.x() + 0.5,
                        location.y() + 0.5,
                        location.z() + 0.5,
                        0.0f,
                        0.0f
                ))
                .itemStack(itemStack)
                .scale(2.0f, 2.0f, 2.0f)
                .displayContext(DisplaySpec.DISPLAY_CONTEXT_FIXED)
                .build();
        return new DesiredDisplay(id, spec);
    }

    public void apply(@NotNull VirtualItemDisplay display, @NotNull DisplaySpec spec) {
        World world = Bukkit.getWorld(spec.worldId());
        if (world == null) {
            return;
        }
        ItemStack stack = spec.itemStack();
        NamespacedKey itemModel = spec.itemModel();
        if (itemModel != null) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setItemModel(itemModel);
                stack.setItemMeta(meta);
            }
        }

        display.location(new Location(world, spec.x(), spec.y(), spec.z(), spec.yaw(), spec.pitch()))
                .itemStack(stack)
                .displayContext(spec.displayContext())
                .brightness(spec.brightness())
                .scale(spec.scaleX(), spec.scaleY(), spec.scaleZ())
                .translation(spec.translationX(), spec.translationY(), spec.translationZ())
                .viewRange(spec.viewRange())
                .leftRotation(spec.leftRotationX(), spec.leftRotationY(), spec.leftRotationZ(), spec.leftRotationW())
                .rightRotation(spec.rightRotationX(), spec.rightRotationY(), spec.rightRotationZ(), spec.rightRotationW())
                .billboard(spec.billboard())
                .transformationInterpolationDelay(spec.transformationInterpolationDelay())
                .transformationInterpolationDuration(spec.transformationInterpolationDuration())
                .posRotInterpolationDuration(spec.posRotInterpolationDuration())
                .shadowRadius(spec.shadowRadius())
                .shadowStrength(spec.shadowStrength())
                .dimensions(spec.width(), spec.height())
                .glowing(spec.glowing())
                .invisible(spec.invisible())
                .glowColorOverride(spec.glowColorOverride());
    }

    public void clear() {
        for (ManagedDisplay display : displays.values()) {
            display.invalidate();
        }
        displays.clear();
    }

    public void removeBlockAttached(@NotNull BlockLocationKey owner) {
        ensureServerThread();
        List<ManagedDisplay> attached = displays.values().stream()
                .filter(display -> display.persistence() == DisplayPersistence.PERSISTENT_BLOCK_ATTACHED)
                .filter(display -> owner.equals(display.owner()))
                .toList();
        for (ManagedDisplay display : attached) {
            display.remove();
        }
    }

    private void removeLoadedForChunk(@NotNull ChunkEngine.Key chunkKey) {
        displays.values().removeIf(display ->
                display.persistence() != DisplayPersistence.TRANSIENT && storedIn(display, chunkKey));
    }

    private void refresh(@NotNull ManagedDisplay display) {
        ChunkEngine.Key key = chunkKey(display.spec());
        dev.auto.blockengine.visibility.VisibilityManager.getInstance().refreshPlayersNear(key);
    }

    private void markDirty(@NotNull ManagedDisplay display) {
        if (display.persistence() == DisplayPersistence.TRANSIENT) {
            refresh(display);
            return;
        }

        Chunk chunk = storageChunk(display);
        ChunkEngine.Data data = ChunkEngine.data(chunk);
        ChunkEngine.StoredDisplay stored = ChunkEngine.StoredDisplay.from(display);
        if (display.persistence() == DisplayPersistence.PERSISTENT_WORLD) {
            data.setDisplay(stored);
        } else if (display.owner() != null) {
            BlockLocationKey owner = display.owner();
            data.setBlockDisplay(owner.x() & 15, owner.y(), owner.z() & 15, stored);
        }
        ChunkEngine.changed(chunk);
        refresh(display);
    }

    private void removeStored(@NotNull ManagedDisplay display) {
        if (display.persistence() == DisplayPersistence.TRANSIENT) {
            return;
        }

        Chunk chunk = storageChunk(display);
        ChunkEngine.Data data = ChunkEngine.data(chunk);
        if (display.persistence() == DisplayPersistence.PERSISTENT_WORLD) {
            data.removeDisplay(display.id());
        } else if (display.owner() != null) {
            BlockLocationKey owner = display.owner();
            data.removeBlockDisplay(owner.x() & 15, owner.y(), owner.z() & 15, display.id());
        }
        ChunkEngine.changed(chunk);
    }

    private static void ensureServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Managed display API must be used on the server thread.");
        }
    }

    private static @NotNull Chunk chunk(@NotNull DisplaySpec spec) {
        World world = Bukkit.getWorld(spec.worldId());
        if (world == null) {
            throw new IllegalStateException("Display world is not loaded: " + spec.worldId());
        }
        return world.getChunkAt(floor(spec.x()) >> 4, floor(spec.z()) >> 4);
    }

    private static @NotNull Chunk storageChunk(@NotNull ManagedDisplay display) {
        if (display.persistence() != DisplayPersistence.PERSISTENT_BLOCK_ATTACHED || display.owner() == null) {
            return chunk(display.spec());
        }
        World world = Bukkit.getWorld(display.owner().worldId());
        if (world == null) {
            throw new IllegalStateException("Display owner world is not loaded: " + display.owner().worldId());
        }
        return world.getChunkAt(display.owner().x() >> 4, display.owner().z() >> 4);
    }

    private static @NotNull ChunkEngine.Key chunkKey(@NotNull DisplaySpec spec) {
        return new ChunkEngine.Key(spec.worldId(), floor(spec.x()) >> 4, floor(spec.z()) >> 4);
    }

    private static @NotNull BlockLocationKey anchor(@NotNull DisplaySpec spec) {
        return new BlockLocationKey(spec.worldId(), floor(spec.x()), floor(spec.y()), floor(spec.z()));
    }

    private static boolean storedIn(@NotNull ManagedDisplay display, @NotNull ChunkEngine.Key chunkKey) {
        if (display.persistence() == DisplayPersistence.PERSISTENT_BLOCK_ATTACHED && display.owner() != null) {
            return chunkKey.contains(display.owner());
        }
        return chunkKey.contains(anchor(display.spec()));
    }

    private static @NotNull BlockLocationKey location(@NotNull Block block) {
        return new BlockLocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    public record DesiredDisplay(@NotNull UUID id, @NotNull DisplaySpec spec) {
    }

    public final class ManagedDisplay implements ManagedDisplayHandle {
        private final @NotNull UUID id;
        private final @NotNull DisplayPersistence persistence;
        private final @Nullable BlockLocationKey owner;
        private final @Nullable String ownerKey;
        private @NotNull DisplaySpec spec;
        private boolean valid = true;

        private ManagedDisplay(
                @NotNull UUID id,
                @NotNull DisplayPersistence persistence,
                @Nullable BlockLocationKey owner,
                @Nullable String ownerKey,
                @NotNull DisplaySpec spec
        ) {
            this.id = id;
            this.persistence = persistence;
            this.owner = owner;
            this.ownerKey = ownerKey;
            this.spec = spec;
        }

        private static @NotNull ManagedDisplay from(@NotNull ChunkEngine.StoredDisplay stored) {
            return instance.new ManagedDisplay(
                    stored.id(),
                    stored.persistence(),
                    stored.owner(),
                    stored.ownerKey(),
                    stored.spec()
            );
        }

        @Override
        public @NotNull UUID id() {
            return id;
        }

        @Override
        public @NotNull DisplayPersistence persistence() {
            return persistence;
        }

        @Override
        public @NotNull DisplaySpec spec() {
            return spec;
        }

        public @Nullable BlockLocationKey owner() {
            return owner;
        }

        public @Nullable String ownerKey() {
            return ownerKey;
        }

        @Override
        public boolean valid() {
            return valid && displays.get(id) == this;
        }

        @Override
        public boolean update(@NotNull DisplaySpec spec) {
            ensureServerThread();
            if (!valid()) {
                return false;
            }
            this.spec = Objects.requireNonNull(spec, "spec");
            markDirty(this);
            return true;
        }

        @Override
        public boolean remove() {
            ensureServerThread();
            if (!valid()) {
                return false;
            }
            removeStored(this);
            invalidate();
            displays.remove(id);
            refresh(this);
            return true;
        }

        private void invalidate() {
            valid = false;
        }
    }
}
