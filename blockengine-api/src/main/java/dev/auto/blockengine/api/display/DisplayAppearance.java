package dev.auto.blockengine.api.display;

import dev.auto.blockengine.api.resourcepack.GeneratedItemModel;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Describes the item rendered by a managed item-display entity.
 *
 * <p>Minecraft item displays render an {@link ItemStack}. Modern resource packs
 * can override that stack's visual model with an item model key. This type
 * keeps those two pieces together so callers can swap a display's appearance
 * without rebuilding its full {@link DisplaySpec} transform, audience, and
 * metadata.</p>
 *
 * <p>The {@code ItemStack} is cloned on input and output. Mutating the original
 * stack after creating a {@code DisplayAppearance} will not change the display.</p>
 */
public final class DisplayAppearance {
    private final @NotNull ItemStack itemStack;
    private final @Nullable NamespacedKey itemModel;

    private DisplayAppearance(@NotNull ItemStack itemStack, @Nullable NamespacedKey itemModel) {
        this.itemStack = Objects.requireNonNull(itemStack, "itemStack").clone();
        this.itemModel = itemModel;
    }

    /**
     * Uses the given item stack exactly as the rendered display item.
     *
     * @param itemStack stack to render, or air when null
     * @return an appearance with no explicit item model override
     */
    public static @NotNull DisplayAppearance item(@Nullable ItemStack itemStack) {
        return new DisplayAppearance(itemStack == null ? new ItemStack(Material.AIR) : itemStack, null);
    }

    /**
     * Renders a vanilla carrier material with the supplied item model key.
     *
     * <p>The model key must exist in the player's resource pack, either from a
     * {@link dev.auto.blockengine.api.resourcepack.GeneratedItemModel} registered
     * with BlockEngine or from assets supplied by a plugin.</p>
     *
     * @param carrier vanilla item material used as the stack carrier
     * @param model item model key to apply
     * @return an appearance using the carrier and model override
     */
    public static @NotNull DisplayAppearance model(@NotNull Material carrier, @NotNull NamespacedKey model) {
        return model(new ItemStack(carrier), model);
    }

    /**
     * Renders an item stack with the supplied item model key.
     *
     * @param carrier item stack to carry the model, or air when null
     * @param model item model key to apply
     * @return an appearance using the carrier and model override
     */
    public static @NotNull DisplayAppearance model(@Nullable ItemStack carrier, @NotNull NamespacedKey model) {
        return new DisplayAppearance(carrier == null ? new ItemStack(Material.AIR) : carrier, model);
    }

    /**
     * Renders a vanilla carrier material with a generated item model.
     *
     * @param carrier vanilla item material used as the stack carrier
     * @param model generated model registered for the resource pack
     * @return an appearance using {@code model.key()} as the item model override
     */
    public static @NotNull DisplayAppearance generated(@NotNull Material carrier, @NotNull GeneratedItemModel model) {
        return model(carrier, model.key());
    }

    /**
     * Renders an item stack with a generated item model.
     *
     * @param carrier item stack to carry the model, or air when null
     * @param model generated model registered for the resource pack
     * @return an appearance using {@code model.key()} as the item model override
     */
    public static @NotNull DisplayAppearance generated(@Nullable ItemStack carrier, @NotNull GeneratedItemModel model) {
        return model(carrier, model.key());
    }

    /**
     * Returns a clone of the rendered item stack.
     *
     * @return cloned display item stack
     */
    public @NotNull ItemStack itemStack() {
        return itemStack.clone();
    }

    /**
     * Returns the item model override key, if one should be applied.
     *
     * @return item model key, or null to use the stack's normal model
     */
    public @Nullable NamespacedKey itemModel() {
        return itemModel;
    }
}
