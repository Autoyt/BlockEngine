package dev.auto.blockengine.visibility;

import dev.auto.blockengine.entity.VirtualItemDisplay;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.types.BlockLocationKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

final class PlayerVisibility {
    private final @NotNull Map<BlockLocationKey, VirtualItemDisplay> active = new HashMap<>();
    private final @NotNull ArrayDeque<VirtualItemDisplay> pool = new ArrayDeque<>();

    private int lastRecalcTick = -1;
    private @Nullable ChunkEngine.Key centerChunk;
    private int radius = -1;

    @NotNull Map<BlockLocationKey, VirtualItemDisplay> active() {
        return active;
    }

    @NotNull ArrayDeque<VirtualItemDisplay> pool() {
        return pool;
    }

    int lastRecalcTick() {
        return lastRecalcTick;
    }

    void lastRecalcTick(int lastRecalcTick) {
        this.lastRecalcTick = lastRecalcTick;
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
