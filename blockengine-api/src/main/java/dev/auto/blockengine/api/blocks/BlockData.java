package dev.auto.blockengine.api.blocks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Mutable placed-block state exposed to both the runtime and usage plugins.
 *
 * <p>The runtime is free to back this with its own storage implementation. The
 * API only requires a stable surface for reading and writing placed block data.</p>
 */
public interface BlockData {
    @NotNull String blockId();

    void blockId(@NotNull String blockId);

    @NotNull String stateId();

    void stateId(@NotNull String stateId);

    @NotNull Map<String, String> stringData();

    @NotNull Map<String, Integer> intData();

    @NotNull Map<String, Boolean> booleanData();

    default @Nullable String string(@NotNull String key) {
        return stringData().get(key);
    }

    default void string(@NotNull String key, @Nullable String value) {
        if (value == null) {
            stringData().remove(key);
            return;
        }
        stringData().put(key, value);
    }

    default @Nullable Integer integer(@NotNull String key) {
        return intData().get(key);
    }

    default void integer(@NotNull String key, @Nullable Integer value) {
        if (value == null) {
            intData().remove(key);
            return;
        }
        intData().put(key, value);
    }

    default @Nullable Boolean bool(@NotNull String key) {
        return booleanData().get(key);
    }

    default void bool(@NotNull String key, @Nullable Boolean value) {
        if (value == null) {
            booleanData().remove(key);
            return;
        }
        booleanData().put(key, value);
    }
}
