package dev.auto.blockengine.api.blocks;

import org.bukkit.block.Block;
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
     *
     * @return local block name, such as {@code lamp} or {@code furniture/chair}
     */
    @NotNull String name();

    /**
     * Defines static server-side block details such as hardness, states,
     * textures, sounds, and placement.
     *
     * <p>BlockEngine calls this during startup while it is building its block
     * registry. Implementations should only describe the block here. Runtime
     * behavior belongs in the callback methods on this adapter.</p>
     *
     * @param builder mutable block definition builder
     */
    void define(@NotNull BlockDefinition.Builder builder);

    /**
     * Creates the first mutable data object for a newly placed block.
     *
     * <p>Use this hook to seed stateful values such as owner ids, inventory ids,
     * orientation metadata, or counters. The returned data is then persisted by
     * BlockEngine with the placed block record.</p>
     *
     * @param context placement context for the new block
     * @return initial mutable data for the placed block
     */
    @NotNull BlockData createDefaultData(@NotNull BlockCreateContext context);

    /**
     * Called after BlockEngine places this custom block.
     *
     * @param context context for the placed block
     */
    default void onPlace(@NotNull BlockContext context) {
    }

    /**
     * Called before this custom block is broken.
     *
     * @param context context for the placed block
     * @return false to cancel breaking
     */
    default boolean onBreak(@NotNull BlockContext context) {
        return true;
    }

    /**
     * Called when a player interacts with this custom block.
     *
     * @param context context for the placed block
     * @param player player who interacted with the block
     * @return true when the adapter handled the interaction
     */
    default boolean onInteract(@NotNull BlockContext context, @NotNull Player player) {
        return false;
    }

    /**
     * Returns whether BlockEngine should call {@link #onTick(BlockContext)} for
     * placed instances of this block.
     *
     * @return true to enable ticking for this adapter
     */
    default boolean ticking() {
        return false;
    }

    /**
     * Called during BlockEngine's tick pass for placed blocks owned by this
     * adapter when {@link #ticking()} returns true.
     *
     * @param context context for the placed block
     */
    default void onTick(@NotNull BlockContext context) {
    }

    /**
     * Called before BlockEngine moves this custom block between two Bukkit
     * blocks.
     *
     * @param context context for the placed block before it moves
     * @param from current Bukkit block
     * @param to target Bukkit block
     * @param cause cause of the movement
     * @return false to cancel the move
     */
    default boolean canMove(
            @NotNull BlockContext context,
            @NotNull Block from,
            @NotNull Block to,
            @NotNull MoveCause cause
    ) {
        return true;
    }

    /**
     * Called after BlockEngine moves this custom block between two Bukkit
     * blocks.
     *
     * @param context context for the placed block after it moves
     * @param from previous Bukkit block
     * @param to new Bukkit block
     * @param cause cause of the movement
     */
    default void onMove(
            @NotNull BlockContext context,
            @NotNull Block from,
            @NotNull Block to,
            @NotNull MoveCause cause
    ) {
    }

    /**
     * Serializes only adapter-private placed-block payload.
     *
     * <p>BlockEngine still serializes position, block id, state id, and public
     * {@link BlockData} maps.</p>
     *
     * @param data mutable placed-block data
     * @return serialized private payload
     */
    default byte @NotNull [] save(@NotNull BlockData data) {
        return new byte[0];
    }

    /**
     * Restores adapter-private payload into BlockEngine-owned block data.
     *
     * @param data mutable placed-block data
     * @param payload serialized private payload
     */
    default void load(@NotNull BlockData data, byte @NotNull [] payload) {
    }

    /**
     * Called if payload data cannot be read safely.
     *
     * @param data mutable placed-block data being recovered
     * @param payload serialized private payload that failed to load
     * @param error load failure, or null when no exception was captured
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

    /**
     * Describes why BlockEngine is moving a custom block.
     */
    enum MoveCause {
        /**
         * Movement caused by BlockEngine gravity behavior.
         */
        GRAVITY,

        /**
         * Movement requested directly by plugin code.
         */
        PLUGIN
    }
}
