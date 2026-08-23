package dev.auto.blockengine.api.blocks;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Code-first definition and behavior surface for a BlockEngine custom block.
 *
 * <p>BlockEngine owns the outer world/chunk format. Adapters own their static block
 * definition, runtime behavior, and optional private payload.</p>
 */
public interface BlockAdapter {
    /**
     * Local block name inside the owning plugin namespace.
     *
     * <p>The full id is built by BlockEngine as {@code namespace:name()}.</p>
     */
    @NotNull String name();

    /**
     * Defines static server-side block details such as hardness, states,
     * textures, sounds, and placement.
     */
    void define(@NotNull BlockDefinition.Builder builder);

    /**
     * Creates the first mutable data object for a newly placed block.
     */
    @NotNull BlockData createDefaultData(@NotNull BlockCreateContext context);

    default void onPlace(@NotNull BlockContext context) {
    }

    /**
     * Return false to cancel breaking.
     */
    default boolean onBreak(@NotNull BlockContext context) {
        return true;
    }

    /**
     * Return true when the adapter handled the interaction.
     */
    default boolean onInteract(@NotNull BlockContext context, @NotNull Player player) {
        return false;
    }

    default boolean ticking() {
        return false;
    }

    default void onTick(@NotNull BlockContext context) {
    }

    default void onRedstonePowerChange(@NotNull BlockContext context, int oldPower, int newPower) {
    }

    default int redstoneWeakPower(
            @NotNull BlockContext context,
            @NotNull BlockFace outputFace,
            int configuredPower
    ) {
        return configuredPower;
    }

    default int redstoneStrongPower(
            @NotNull BlockContext context,
            @NotNull BlockFace outputFace,
            int configuredPower
    ) {
        return configuredPower;
    }

    /**
     * Serializes only adapter-private placed-block payload.
     *
     * <p>BlockEngine still serializes position, block id, state id, and public
     * {@link BlockData} maps.</p>
     */
    default byte @NotNull [] save(@NotNull BlockData data) {
        return new byte[0];
    }

    /**
     * Restores adapter-private payload into BlockEngine-owned block data.
     */
    default void load(@NotNull BlockData data, byte @NotNull [] payload) {
    }

    /**
     * Called if payload data cannot be read safely.
     *
     * @return true to keep the block with fallback/default data, false to mark
     * the placed block unloadable.
     */
    default boolean recoverPayload(
            @NotNull BlockData data,
            byte @NotNull [] payload,
            @Nullable Throwable error
    ) {
        return true;
    }
}
