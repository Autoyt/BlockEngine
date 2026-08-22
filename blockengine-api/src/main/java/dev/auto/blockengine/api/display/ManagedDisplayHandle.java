package dev.auto.blockengine.api.display;

import dev.auto.blockengine.api.resourcepack.GeneratedItemModel;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Mutable handle to a managed client-side display entity.
 *
 * <p>Handles are lightweight references to runtime state owned by BlockEngine.
 * A handle can become invalid after the display is removed, the owning chunk is
 * unloaded, the owning block is removed, or BlockEngine shuts down. Mutating
 * methods return {@code false} when the handle is no longer valid.</p>
 *
 * <p>Implementations are expected to be used from the Bukkit server thread.
 * Runtime implementations may reject asynchronous mutation calls.</p>
 */
public interface ManagedDisplayHandle {
    /**
     * Stable display id for lookup and persistence.
     *
     * @return display UUID
     */
    @NotNull UUID id();

    /**
     * Returns this display's lifetime and storage policy.
     *
     * @return persistence mode selected at creation
     */
    @NotNull DisplayPersistence persistence();

    /**
     * Returns the current full display specification.
     *
     * <p>The returned value is immutable. Use {@link #update(DisplaySpec)} or
     * {@link #update(UnaryOperator)} to apply changes.</p>
     *
     * @return current display specification
     */
    @NotNull DisplaySpec spec();

    /**
     * Returns whether this handle still points at a live managed display.
     *
     * @return true when mutations can still be applied
     */
    boolean valid();

    /**
     * Replaces the display's full specification.
     *
     * <p>For persistent displays, this marks the owning chunk dirty. Active
     * viewers near the display are refreshed so metadata changes become visible
     * without requiring a relog.</p>
     *
     * @param spec replacement display spec
     * @return true if the update was accepted
     */
    boolean update(@NotNull DisplaySpec spec);

    /**
     * Updates the display by editing a builder seeded from the current spec.
     *
     * @param update function that mutates and returns the builder
     * @return true if the update was accepted
     */
    default boolean update(@NotNull UnaryOperator<DisplaySpec.Builder> update) {
        return update(update.apply(spec().toBuilder()).build());
    }

    /**
     * Changes which players can see this display.
     *
     * @param audience replacement audience rule
     * @return true if the update was accepted
     */
    default boolean audience(@NotNull DisplayAudience audience) {
        return update(builder -> builder.audience(audience));
    }

    /**
     * Replaces only the display's item appearance.
     *
     * @param appearance new item stack and optional item model key
     * @return true if the update was accepted
     */
    default boolean appearance(@NotNull DisplayAppearance appearance) {
        return update(builder -> builder.appearance(appearance));
    }

    /**
     * Replaces the rendered item stack while leaving the current model override.
     *
     * @param itemStack new rendered stack, or air when null
     * @return true if the update was accepted
     */
    default boolean itemStack(@Nullable ItemStack itemStack) {
        return update(builder -> builder.itemStack(itemStack));
    }

    /**
     * Changes only the item model override.
     *
     * @param itemModel item model key, or null to clear the override
     * @return true if the update was accepted
     */
    default boolean itemModel(@Nullable NamespacedKey itemModel) {
        return update(builder -> builder.itemModel(itemModel));
    }

    /**
     * Changes only the item model override to a registered generated model.
     *
     * @param itemModel generated model, or null to clear the override
     * @return true if the update was accepted
     */
    default boolean itemModel(@Nullable GeneratedItemModel itemModel) {
        return update(builder -> builder.itemModel(itemModel));
    }

    /**
     * Replaces the rendered stack with a new carrier material and model key.
     *
     * @param carrier vanilla item material used as the stack carrier
     * @param itemModel item model key to apply
     * @return true if the update was accepted
     */
    default boolean model(@NotNull Material carrier, @NotNull NamespacedKey itemModel) {
        return appearance(DisplayAppearance.model(carrier, itemModel));
    }

    /**
     * Replaces the rendered stack with a carrier material and generated model.
     *
     * @param carrier vanilla item material used as the stack carrier
     * @param itemModel generated model to apply
     * @return true if the update was accepted
     */
    default boolean generatedModel(@NotNull Material carrier, @NotNull GeneratedItemModel itemModel) {
        return appearance(DisplayAppearance.generated(carrier, itemModel));
    }

    /**
     * Clears the item model override while leaving the current item stack.
     *
     * @return true if the update was accepted
     */
    default boolean clearItemModel() {
        return itemModel((NamespacedKey) null);
    }

    /**
     * Removes the display and invalidates this handle.
     *
     * <p>Persistent displays are also removed from chunk storage. Active viewers
     * are refreshed so the client-side entity is destroyed.</p>
     *
     * @return true if the display was removed
     */
    boolean remove();
}
