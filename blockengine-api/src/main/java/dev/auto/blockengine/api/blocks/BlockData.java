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
    /**
     * Returns the full custom block id stored for this placed block.
     *
     * @return full block id
     */
    @NotNull String blockId();

    /**
     * Sets the full custom block id stored for this placed block.
     *
     * <p>Most adapters should not need to change this directly. Prefer changing
     * the state id for normal variant changes.</p>
     *
     * @param blockId full block id
     */
    void blockId(@NotNull String blockId);

    /**
     * Returns the active state id for this placed block.
     *
     * @return state id from the block definition
     */
    @NotNull String stateId();

    /**
     * Sets the active state id for this placed block.
     *
     * @param stateId state id from the block definition
     */
    void stateId(@NotNull String stateId);

    /**
     * Returns the mutable string data map persisted with this block.
     *
     * @return mutable string data map
     */
    @NotNull Map<String, String> stringData();

    /**
     * Returns the mutable integer data map persisted with this block.
     *
     * @return mutable integer data map
     */
    @NotNull Map<String, Integer> intData();

    /**
     * Returns the mutable boolean data map persisted with this block.
     *
     * @return mutable boolean data map
     */
    @NotNull Map<String, Boolean> booleanData();

    /**
     * Reads one string value from {@link #stringData()}.
     *
     * @param key data key
     * @return stored value, or null
     */
    default @Nullable String string(@NotNull String key) {
        return stringData().get(key);
    }

    /**
     * Writes or removes one string value in {@link #stringData()}.
     *
     * @param key data key
     * @param value value to store, or null to remove
     */
    default void string(@NotNull String key, @Nullable String value) {
        if (value == null) {
            stringData().remove(key);
            return;
        }
        stringData().put(key, value);
    }

    /**
     * Reads one integer value from {@link #intData()}.
     *
     * @param key data key
     * @return stored value, or null
     */
    default @Nullable Integer integer(@NotNull String key) {
        return intData().get(key);
    }

    /**
     * Writes or removes one integer value in {@link #intData()}.
     *
     * @param key data key
     * @param value value to store, or null to remove
     */
    default void integer(@NotNull String key, @Nullable Integer value) {
        if (value == null) {
            intData().remove(key);
            return;
        }
        intData().put(key, value);
    }

    /**
     * Reads one boolean value from {@link #booleanData()}.
     *
     * @param key data key
     * @return stored value, or null
     */
    default @Nullable Boolean bool(@NotNull String key) {
        return booleanData().get(key);
    }

    /**
     * Writes or removes one boolean value in {@link #booleanData()}.
     *
     * @param key data key
     * @param value value to store, or null to remove
     */
    default void bool(@NotNull String key, @Nullable Boolean value) {
        if (value == null) {
            booleanData().remove(key);
            return;
        }
        booleanData().put(key, value);
    }
}
