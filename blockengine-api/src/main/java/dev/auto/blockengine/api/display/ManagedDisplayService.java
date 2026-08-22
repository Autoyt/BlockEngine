package dev.auto.blockengine.api.display;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Service for creating and managing BlockEngine-owned client-side item displays.
 *
 * <p>Managed displays are packet entities: they are visible to clients, but do
 * not exist as Bukkit entities in the world. BlockEngine owns their packet ids,
 * per-player visibility, persistence, and cleanup. Plugins mutate displays
 * through {@link ManagedDisplayHandle} rather than spawning packets directly.</p>
 *
 * <p>All mutating calls should be made on the Bukkit server thread.</p>
 */
public interface ManagedDisplayService {
    /**
     * Creates a managed display from a full spec.
     *
     * <p>{@link DisplayPersistence#TRANSIENT} displays are memory-only.
     * {@link DisplayPersistence#PERSISTENT_WORLD} displays are stored in the
     * chunk containing their anchor location. Use
     * {@link #createBlockAttached(Block, String, DisplaySpec)} for
     * {@link DisplayPersistence#PERSISTENT_BLOCK_ATTACHED} displays.</p>
     *
     * @param spec initial display state
     * @param persistence lifetime and storage policy
     * @return mutable handle to the created display
     * @throws IllegalArgumentException if block-attached persistence is used
     *                                  with this method
     */
    @NotNull ManagedDisplayHandle create(@NotNull DisplaySpec spec, @NotNull DisplayPersistence persistence);

    /**
     * Creates a managed display visible only to one player.
     *
     * <p>This is a convenience for setting
     * {@link DisplaySpec.Builder#audience(DisplayAudience)} to
     * {@link DisplayAudience#only(Player)} before creation.</p>
     *
     * @param player only player that can see the display
     * @param spec initial display state
     * @param persistence lifetime and storage policy
     * @return mutable handle to the created display
     */
    default @NotNull ManagedDisplayHandle createFor(
            @NotNull Player player,
            @NotNull DisplaySpec spec,
            @NotNull DisplayPersistence persistence
    ) {
        return create(spec.toBuilder().audience(DisplayAudience.only(player)).build(), persistence);
    }

    /**
     * Creates or replaces a display attached to a custom block.
     *
     * <p>The display is stored on the custom block's persisted record, not in the
     * chunk containing the display's visual location. This means a display can be
     * offset from the block while still following the block's lifecycle. When the
     * owning custom block is removed, the display record is removed too.</p>
     *
     * <p>The {@code key} identifies the display slot on that block. Creating
     * another block-attached display with the same key should replace the old
     * slot in storage.</p>
     *
     * @param block owning custom block
     * @param key stable plugin-defined display key for that block
     * @param spec initial display state
     * @return mutable handle to the created display
     */
    @NotNull ManagedDisplayHandle createBlockAttached(@NotNull Block block, @NotNull String key, @NotNull DisplaySpec spec);

    /**
     * Looks up a currently loaded managed display.
     *
     * @param id display id returned by {@link ManagedDisplayHandle#id()}
     * @return display handle, or null if not loaded or no longer valid
     */
    @Nullable ManagedDisplayHandle get(@NotNull UUID id);

    /**
     * Returns handles for currently loaded managed displays.
     *
     * @return immutable snapshot collection of loaded handles
     */
    @NotNull Collection<ManagedDisplayHandle> displays();

    /**
     * Returns loaded managed displays near a location.
     *
     * <p>This method is a query helper and does not force chunks to load.</p>
     *
     * @param location center of the search
     * @param radius radius in blocks
     * @return immutable snapshot collection of nearby loaded handles
     */
    @NotNull Collection<ManagedDisplayHandle> displaysNear(@NotNull Location location, double radius);

    /**
     * Removes a loaded managed display by id.
     *
     * <p>Persistent displays are removed from their storage location as part of
     * removal.</p>
     *
     * @param id display id
     * @return true if a display was found and removed
     */
    boolean remove(@NotNull UUID id);
}
