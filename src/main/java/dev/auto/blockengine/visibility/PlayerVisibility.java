package dev.auto.blockengine.visibility;

import dev.auto.blockengine.entity.VirtualItemDisplay;
import dev.auto.blockengine.runtime.ChunkEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class PlayerVisibility {
    private final @NotNull Map<UUID, VirtualItemDisplay> active = new HashMap<>();
    private final @NotNull ArrayDeque<VirtualItemDisplay> pool = new ArrayDeque<>();

    private boolean recalculationQueued;
    private @Nullable ChunkEngine.Key centerChunk;
    private int radius = -1;

    @NotNull Map<UUID, VirtualItemDisplay> active() {
        return active;
    }

    @NotNull ArrayDeque<VirtualItemDisplay> pool() {
        return pool;
    }

    boolean queueRecalculation() {
        if (recalculationQueued) {
            return false;
        }
        recalculationQueued = true;
        return true;
    }

    void finishRecalculation() {
        recalculationQueued = false;
    }

    @Nullable ChunkEngine.Key centerChunk() {
        return centerChunk;
    }

    void centerChunk(@Nullable ChunkEngine.Key centerChunk) {
        this.centerChunk = centerChunk;
    }

    int radius() {
        return radius;
    }

    void radius(int radius) {
        this.radius = radius;
    }
}
