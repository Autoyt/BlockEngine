package dev.auto.blockengine.api;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.resourcepack.GeneratedItemModel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public interface CustomBlockSystem {
    String pluginVersion();

    String getNamespace();

    List<BlockAdapter> registerAdapters();

    default void setPackDetails(@NotNull PackDetails details) {
    }

    /**
     * Registers code-generated item models for this plugin's resource pack.
     *
     * <p>BlockEngine calls this during resource-pack generation. Implementations
     * should register deterministic model definitions only; avoid depending on
     * player state or other runtime state that can change between generation and
     * use. The generated model keys can then be used by managed displays through
     * {@link dev.auto.blockengine.api.display.DisplayAppearance} or
     * {@link dev.auto.blockengine.api.display.DisplaySpec.Builder#itemModel(org.bukkit.NamespacedKey)}.</p>
     *
     * <p>This hook creates model JSON and item definition JSON. It does not
     * create PNG texture files. Provide texture assets with
     * {@link PackDetails#addAssets(Path)}.</p>
     *
     * @param registry registry for generated item model definitions
     */
    default void onItemModelGeneration(@NotNull GeneratedItemModel.Registry registry) {
    }

    final class PackDetails {
        private static final @NotNull MiniMessage MINI = MiniMessage.miniMessage();
        private @Nullable Path icon;
        private @NotNull Component title = Component.empty();
        private @NotNull Component description = Component.empty();
        private @NotNull Component prompt = Component.empty();
        private @NotNull String urlEnding = "";
        private boolean required = true;
        private final @NotNull List<String> assetNamespaces = new ArrayList<>();
        private final @NotNull List<Path> assets = new ArrayList<>();

        public @Nullable Path icon() {
            return icon;
        }

        public @NotNull PackDetails icon(@Nullable Path icon) {
            this.icon = icon;
            return this;
        }

        public @NotNull Component title() {
            return title;
        }

        public @NotNull PackDetails title(@Nullable String title) {
            return title(title == null || title.isBlank() ? Component.empty() : MINI.deserialize(title));
        }

        public @NotNull PackDetails title(@Nullable Component title) {
            this.title = title == null ? Component.empty() : title;
            return this;
        }

        public @NotNull Component description() {
            return description;
        }

        public @NotNull PackDetails description(@Nullable String description) {
            return description(description == null || description.isBlank()
                    ? Component.empty()
                    : MINI.deserialize(description));
        }

        public @NotNull PackDetails description(@Nullable Component description) {
            this.description = description == null ? Component.empty() : description;
            return this;
        }

        public @NotNull Component prompt() {
            return prompt;
        }

        public @NotNull PackDetails prompt(@Nullable String prompt) {
            return prompt(prompt == null || prompt.isBlank() ? Component.empty() : MINI.deserialize(prompt));
        }

        public @NotNull PackDetails prompt(@Nullable Component prompt) {
            this.prompt = prompt == null ? Component.empty() : prompt;
            return this;
        }

        public @NotNull String urlEnding() {
            return urlEnding;
        }

        public @NotNull PackDetails urlEnding(@NotNull String urlEnding) {
            this.urlEnding = Objects.requireNonNull(urlEnding, "urlEnding");
            return this;
        }

        public boolean required() {
            return required;
        }

        public @NotNull PackDetails required(boolean required) {
            this.required = required;
            return this;
        }

        public @NotNull List<String> assetNamespaces() {
            return Collections.unmodifiableList(assetNamespaces);
        }

        public @NotNull PackDetails assetNamespace(@NotNull String namespace) {
            assetNamespaces.add(Objects.requireNonNull(namespace, "namespace"));
            return this;
        }

        public @NotNull PackDetails assetNamespaces(@NotNull String... namespaces) {
            for (String namespace : namespaces) {
                assetNamespace(namespace);
            }
            return this;
        }

        public @NotNull List<Path> assets() {
            return Collections.unmodifiableList(assets);
        }

        public @NotNull PackDetails addAssets(@NotNull Path root) {
            assets.add(Objects.requireNonNull(root, "root"));
            return this;
        }

        public @NotNull PackDetails addAssets(@NotNull Path... roots) {
            for (Path root : roots) {
                addAssets(root);
            }
            return this;
        }
    }
}
