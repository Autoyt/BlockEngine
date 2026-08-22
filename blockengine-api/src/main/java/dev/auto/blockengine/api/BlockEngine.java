package dev.auto.blockengine.api;

import dev.auto.blockengine.api.display.ManagedDisplayService;
import dev.auto.blockengine.api.world.BlockEngineManagedWorld;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * Entry point for BlockEngine services exposed to other plugins.
 *
 * <p>Plugins normally call this from event handlers, scheduled tasks, or block
 * adapter callbacks after BlockEngine has enabled. Service accessors throw if
 * BlockEngine is not ready yet, which usually means the call happened during
 * plugin construction or before the Bukkit enable phase.</p>
 */
public final class BlockEngine {
    private static @Nullable ManagedDisplayService managedDisplayService;
    private static @Nullable Function<World, BlockEngineManagedWorld> managedWorldFactory;

    private BlockEngine() {
    }

    /**
     * Returns the managed client-side display entity service.
     *
     * <p>The returned service owns packet entity ids, per-player visibility,
     * chunk persistence for persistent displays, and viewer refreshes. Public
     * callers interact only with BlockEngine API value objects and handles.</p>
     *
     * @return the active managed display service
     * @throws IllegalStateException if BlockEngine has not enabled the service
     */
    public static @NotNull ManagedDisplayService managedDisplays() {
        if (managedDisplayService == null) {
            throw new IllegalStateException("BlockEngine managed display service is not available yet.");
        }
        return managedDisplayService;
    }

    /**
     * Wraps a Bukkit world with BlockEngine-aware world operations.
     *
     * <p>Use this instead of casting {@link World}; Bukkit owns the runtime
     * world implementation, so {@code player.getWorld()} will not implement
     * BlockEngine interfaces directly.</p>
     *
     * @param world Bukkit world to wrap
     * @return managed world wrapper
     * @throws IllegalStateException if BlockEngine has not enabled the service
     */
    public static @NotNull BlockEngineManagedWorld world(@NotNull World world) {
        if (managedWorldFactory == null) {
            throw new IllegalStateException("BlockEngine managed world service is not available yet.");
        }
        return managedWorldFactory.apply(Objects.requireNonNull(world, "world"));
    }

    /**
     * Installs the managed display service implementation.
     *
     * <p>This is part of BlockEngine's internal boot process and is not intended
     * for third-party plugin use.</p>
     *
     * @param service runtime implementation to expose
     */
    public static void setManagedDisplayService(@NotNull ManagedDisplayService service) {
        managedDisplayService = Objects.requireNonNull(service, "service");
    }

    /**
     * Installs the managed world wrapper factory.
     *
     * <p>This is part of BlockEngine's internal boot process and is not intended
     * for third-party plugin use.</p>
     *
     * @param factory wrapper factory
     */
    public static void setManagedWorldFactory(@NotNull Function<World, BlockEngineManagedWorld> factory) {
        managedWorldFactory = Objects.requireNonNull(factory, "factory");
    }

    /**
     * Clears the managed display service during shutdown.
     *
     * <p>This is part of BlockEngine's internal shutdown process.</p>
     */
    public static void clearManagedDisplayService() {
        managedDisplayService = null;
    }

    /**
     * Clears the managed world wrapper factory during shutdown.
     *
     * <p>This is part of BlockEngine's internal shutdown process.</p>
     */
    public static void clearManagedWorldFactory() {
        managedWorldFactory = null;
    }
}
