package dev.auto.blockengine.api.resourcepack;

import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Convenience factories for common generated item model shapes.
 *
 * <p>These helpers produce {@link GeneratedItemModel} definitions that are
 * written into the BlockEngine-generated resource pack. They are intentionally
 * generic: callers choose the model key and texture reference, then use the
 * resulting model key on a display via
 * {@link dev.auto.blockengine.api.display.DisplayAppearance} or
 * {@link dev.auto.blockengine.api.display.DisplaySpec.Builder#itemModel(NamespacedKey)}.</p>
 */
public final class TextureModel {
    private TextureModel() {
    }

    /**
     * Creates a standard flat generated item model using {@code layer0}.
     *
     * @param modelKey item model key to generate
     * @param texture texture used as {@code layer0}
     * @return generated model definition
     */
    public static @NotNull GeneratedItemModel flatItem(@NotNull NamespacedKey modelKey, @NotNull TextureRef texture) {
        return GeneratedItemModel.builder(modelKey)
                .parent("minecraft:item/generated")
                .layer0(texture)
                .build();
    }

    /**
     * Creates a standard handheld item model using {@code layer0}.
     *
     * @param modelKey item model key to generate
     * @param texture texture used as {@code layer0}
     * @return generated model definition
     */
    public static @NotNull GeneratedItemModel handheldItem(@NotNull NamespacedKey modelKey, @NotNull TextureRef texture) {
        return GeneratedItemModel.builder(modelKey)
                .parent("minecraft:item/handheld")
                .layer0(texture)
                .build();
    }

    /**
     * Creates a cube-all model where every face uses the same texture.
     *
     * @param modelKey item model key to generate
     * @param texture texture used for the {@code all} texture slot
     * @return generated model definition
     */
    public static @NotNull GeneratedItemModel cubeAll(@NotNull NamespacedKey modelKey, @NotNull TextureRef texture) {
        return GeneratedItemModel.builder(modelKey)
                .parent("minecraft:block/cube_all")
                .texture("all", texture)
                .build();
    }
}
