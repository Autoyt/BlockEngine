package dev.auto.blockengine.api.resourcepack;

import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalized Minecraft texture reference used by generated item models.
 *
 * <p>A texture reference is the path written into model JSON, for example
 * {@code myplugin:item/portal} or {@code minecraft:block/stone}. File
 * extensions are stripped and path separators are normalized. This type does
 * not create or copy texture files; plugins still provide the actual PNG assets
 * through their resource pack asset roots.</p>
 */
public final class TextureRef {
    private final @NotNull String value;

    private TextureRef(@NotNull String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /**
     * Creates a texture reference from a raw model texture string.
     *
     * @param value texture reference, optionally ending in {@code .png}
     * @return normalized texture reference
     */
    public static @NotNull TextureRef of(@NotNull String value) {
        return new TextureRef(normalize(value));
    }

    /**
     * Creates an item texture reference from a namespaced key.
     *
     * <p>For key {@code myplugin:portal}, this returns
     * {@code myplugin:item/portal}.</p>
     *
     * @param key namespaced texture key
     * @return item texture reference
     */
    public static @NotNull TextureRef item(@NotNull NamespacedKey key) {
        return of(key.getNamespace() + ":item/" + key.getKey());
    }

    /**
     * Creates a block texture reference from a namespaced key.
     *
     * <p>For key {@code myplugin:portal}, this returns
     * {@code myplugin:block/portal}.</p>
     *
     * @param key namespaced texture key
     * @return block texture reference
     */
    public static @NotNull TextureRef block(@NotNull NamespacedKey key) {
        return of(key.getNamespace() + ":block/" + key.getKey());
    }

    /**
     * Returns the normalized value written into model JSON.
     *
     * @return texture reference string
     */
    public @NotNull String value() {
        return value;
    }

    private static @NotNull String normalize(@NotNull String value) {
        String normalized = value.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }
}
