package dev.auto.blockengine.api.world;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEngine-aware wrapper around a Bukkit {@link World}.
 *
 * <p>Bukkit creates the concrete {@code World} implementation, so plugins
 * should obtain this wrapper with
 * {@link dev.auto.blockengine.api.BlockEngine#world(World)} instead of casting
 * {@code player.getWorld()} directly.</p>
 *
 * <p>Setting a custom block through this wrapper uses BlockEngine's normal
 * placement/persistence path: default block data is created by the registered
 * adapter, the backing vanilla block is applied, chunk data is marked dirty,
 * visibility refreshes are scheduled, and placement callbacks run.</p>
 */
public interface BlockEngineManagedWorld {
    /**
     * Returns the wrapped Bukkit world.
     *
     * @return Bukkit world
     */
    @NotNull World bukkitWorld();

    /**
     * Sets a custom block at coordinates using the block's default state.
     *
     * @param x block x coordinate
     * @param y block y coordinate
     * @param z block z coordinate
     * @param blockId full BlockEngine id, such as {@code myplugin:portal}
     * @return true if a registered custom block was placed
     */
    default boolean setBlock(int x, int y, int z, @NotNull String blockId) {
        return setBlock(x, y, z, blockId, null, null, null);
    }

    /**
     * Sets a custom block at coordinates using a specific state id.
     *
     * @param x block x coordinate
     * @param y block y coordinate
     * @param z block z coordinate
     * @param blockId full BlockEngine id, such as {@code myplugin:portal}
     * @param stateId state id, or null/blank for the default state
     * @return true if a registered custom block was placed
     */
    default boolean setBlock(int x, int y, int z, @NotNull String blockId, @Nullable String stateId) {
        return setBlock(x, y, z, blockId, stateId, null, null);
    }

    /**
     * Sets a custom block at coordinates.
     *
     * @param x block x coordinate
     * @param y block y coordinate
     * @param z block z coordinate
     * @param blockId full BlockEngine id, such as {@code myplugin:portal}
     * @param stateId state id, or null/blank for the default state
     * @param player optional player used for adapter create/place context
     * @param placedAgainst optional face the block is considered placed against
     * @return true if a registered custom block was placed
     */
    boolean setBlock(
            int x,
            int y,
            int z,
            @NotNull String blockId,
            @Nullable String stateId,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst
    );

    /**
     * Sets a custom block at coordinates using a registered adapter and its
     * default state.
     *
     * <p>The adapter must already be registered through a
     * {@link dev.auto.blockengine.api.CustomBlockSystem}; this method does not
     * register new block definitions.</p>
     *
     * @param x block x coordinate
     * @param y block y coordinate
     * @param z block z coordinate
     * @param adapter registered block adapter
     * @return true if the adapter is registered and the custom block was placed
     */
    default boolean setBlock(int x, int y, int z, @NotNull BlockAdapter adapter) {
        return setBlock(x, y, z, adapter, null, null, null);
    }

    /**
     * Sets a custom block at coordinates using a registered adapter and state.
     *
     * @param x block x coordinate
     * @param y block y coordinate
     * @param z block z coordinate
     * @param adapter registered block adapter
     * @param stateId state id, or null/blank for the default state
     * @return true if the adapter is registered and the custom block was placed
     */
    default boolean setBlock(int x, int y, int z, @NotNull BlockAdapter adapter, @Nullable String stateId) {
        return setBlock(x, y, z, adapter, stateId, null, null);
    }

    /**
     * Sets a custom block at coordinates using a registered adapter.
     *
     * @param x block x coordinate
     * @param y block y coordinate
     * @param z block z coordinate
     * @param adapter registered block adapter
     * @param stateId state id, or null/blank for the default state
     * @param player optional player used for adapter create/place context
     * @param placedAgainst optional face the block is considered placed against
     * @return true if the adapter is registered and the custom block was placed
     */
    boolean setBlock(
            int x,
            int y,
            int z,
            @NotNull BlockAdapter adapter,
            @Nullable String stateId,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst
    );

    /**
     * Sets a custom block at a location using the location's block coordinates.
     *
     * @param location location in this world
     * @param blockId full BlockEngine id
     * @return true if a registered custom block was placed
     */
    default boolean setBlock(@NotNull Location location, @NotNull String blockId) {
        return setBlock(location, blockId, null, null, null);
    }

    /**
     * Sets a custom block at a location using a registered adapter's default
     * state.
     *
     * @param location location in this world
     * @param adapter registered block adapter
     * @return true if the adapter is registered and the custom block was placed
     */
    default boolean setBlock(@NotNull Location location, @NotNull BlockAdapter adapter) {
        return setBlock(location, adapter, null, null, null);
    }

    /**
     * Sets a custom block at a location using the location's block coordinates.
     *
     * @param location location in this world
     * @param blockId full BlockEngine id
     * @param stateId state id, or null/blank for the default state
     * @return true if a registered custom block was placed
     */
    default boolean setBlock(@NotNull Location location, @NotNull String blockId, @Nullable String stateId) {
        return setBlock(location, blockId, stateId, null, null);
    }

    /**
     * Sets a custom block at a location using a registered adapter and state.
     *
     * @param location location in this world
     * @param adapter registered block adapter
     * @param stateId state id, or null/blank for the default state
     * @return true if the adapter is registered and the custom block was placed
     */
    default boolean setBlock(@NotNull Location location, @NotNull BlockAdapter adapter, @Nullable String stateId) {
        return setBlock(location, adapter, stateId, null, null);
    }

    /**
     * Sets a custom block at a location using the location's block coordinates.
     *
     * @param location location in this world
     * @param blockId full BlockEngine id
     * @param stateId state id, or null/blank for the default state
     * @param player optional player used for adapter create/place context
     * @param placedAgainst optional face the block is considered placed against
     * @return true if a registered custom block was placed
     * @throws IllegalArgumentException if the location belongs to another world
     */
    default boolean setBlock(
            @NotNull Location location,
            @NotNull String blockId,
            @Nullable String stateId,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst
    ) {
        if (location.getWorld() == null || !location.getWorld().equals(bukkitWorld())) {
            throw new IllegalArgumentException("Location must belong to this managed world.");
        }
        return setBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ(), blockId, stateId, player, placedAgainst);
    }

    /**
     * Sets a custom block at a location using a registered adapter.
     *
     * @param location location in this world
     * @param adapter registered block adapter
     * @param stateId state id, or null/blank for the default state
     * @param player optional player used for adapter create/place context
     * @param placedAgainst optional face the block is considered placed against
     * @return true if the adapter is registered and the custom block was placed
     * @throws IllegalArgumentException if the location belongs to another world
     */
    default boolean setBlock(
            @NotNull Location location,
            @NotNull BlockAdapter adapter,
            @Nullable String stateId,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst
    ) {
        if (location.getWorld() == null || !location.getWorld().equals(bukkitWorld())) {
            throw new IllegalArgumentException("Location must belong to this managed world.");
        }
        return setBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ(), adapter, stateId, player, placedAgainst);
    }

    /**
     * Removes a BlockEngine custom block at coordinates.
     *
     * @param x block x coordinate
     * @param y block y coordinate
     * @param z block z coordinate
     * @param drop whether to drop the custom block item
     * @return true if a custom block was removed
     */
    boolean removeBlock(int x, int y, int z, boolean drop);

    /**
     * Clears a BlockEngine custom block without drops, replacing it with air.
     *
     * @param x block x coordinate
     * @param y block y coordinate
     * @param z block z coordinate
     * @return true if a custom block was cleared
     */
    default boolean clearBlock(int x, int y, int z) {
        return clearBlock(x, y, z, Material.AIR, false);
    }

    /**
     * Clears a BlockEngine custom block without drops.
     *
     * @param x block x coordinate
     * @param y block y coordinate
     * @param z block z coordinate
     * @param replacement vanilla replacement block material
     * @param applyPhysics whether to apply vanilla physics to the replacement
     * @return true if a custom block was cleared
     */
    boolean clearBlock(int x, int y, int z, @NotNull Material replacement, boolean applyPhysics);

    /**
     * Removes a BlockEngine custom block at a location.
     *
     * @param location location in this world
     * @param drop whether to drop the custom block item
     * @return true if a custom block was removed
     */
    default boolean removeBlock(@NotNull Location location, boolean drop) {
        if (location.getWorld() == null || !location.getWorld().equals(bukkitWorld())) {
            throw new IllegalArgumentException("Location must belong to this managed world.");
        }
        return removeBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ(), drop);
    }

    /**
     * Clears a BlockEngine custom block at a location without drops.
     *
     * @param location location in this world
     * @return true if a custom block was cleared
     */
    default boolean clearBlock(@NotNull Location location) {
        return clearBlock(location, Material.AIR, false);
    }

    /**
     * Clears a BlockEngine custom block at a location without drops.
     *
     * @param location location in this world
     * @param replacement vanilla replacement block material
     * @param applyPhysics whether to apply vanilla physics to the replacement
     * @return true if a custom block was cleared
     */
    default boolean clearBlock(@NotNull Location location, @NotNull Material replacement, boolean applyPhysics) {
        if (location.getWorld() == null || !location.getWorld().equals(bukkitWorld())) {
            throw new IllegalArgumentException("Location must belong to this managed world.");
        }
        return clearBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ(), replacement, applyPhysics);
    }

    /**
     * Reconciles one position with BlockEngine's persisted state.
     *
     * <p>If BlockEngine has a custom block record at this position but the real
     * Bukkit block is no longer the BlockEngine backing block, the stale custom
     * record is removed and attached displays are cleaned up.</p>
     *
     * @param x block x coordinate
     * @param y block y coordinate
     * @param z block z coordinate
     * @return true if a stale custom block record was removed
     */
    boolean reconcileBlock(int x, int y, int z);

    /**
     * Reconciles one location with BlockEngine's persisted state.
     *
     * @param location location in this world
     * @return true if a stale custom block record was removed
     */
    default boolean reconcileBlock(@NotNull Location location) {
        if (location.getWorld() == null || !location.getWorld().equals(bukkitWorld())) {
            throw new IllegalArgumentException("Location must belong to this managed world.");
        }
        return reconcileBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Returns the Bukkit block at the given coordinates.
     *
     * @param x block x coordinate
     * @param y block y coordinate
     * @param z block z coordinate
     * @return Bukkit block
     */
    default @NotNull Block blockAt(int x, int y, int z) {
        return bukkitWorld().getBlockAt(x, y, z);
    }
}
