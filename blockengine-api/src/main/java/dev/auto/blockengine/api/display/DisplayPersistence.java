package dev.auto.blockengine.api.display;

/**
 * Controls how long a managed display exists and where it is stored.
 */
public enum DisplayPersistence {
    /**
     * Exists only in memory until removed, invalidated, chunk unload, plugin
     * disable, or server stop.
     */
    TRANSIENT,

    /**
     * Stored in the chunk containing the display's anchor location and restored
     * when that chunk loads again.
     */
    PERSISTENT_WORLD,

    /**
     * Stored on a custom block record and removed when the owning custom block
     * is removed.
     */
    PERSISTENT_BLOCK_ATTACHED
}
