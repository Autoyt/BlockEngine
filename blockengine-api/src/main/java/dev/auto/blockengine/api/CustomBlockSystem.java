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

/**
 * Plugin-provided registration unit for BlockEngine custom blocks.
 *
 * <p>Implement this interface from a plugin that wants to contribute custom
 * blocks, generated pack metadata, or generated item models. BlockEngine
 * discovers systems during startup, attaches the system namespace to returned
 * block definitions, validates dependencies, and then uses the registered
 * adapters for runtime block behavior.</p>
 *
 * <p>Registration is startup-oriented. New block and creative-inventory entries
 * discovered after startup may require the next restart before they can be
 * represented in generated data and the creative menu.</p>
 */
public interface CustomBlockSystem {
    /**
     * Returns the plugin or integration version exposed to BlockEngine.
     *
     * <p>This is informational and may be shown in diagnostics or generated
     * metadata.</p>
     *
     * @return plugin version string
     */
    String pluginVersion();

    /**
     * Returns the namespace used for every block and generated resource owned by
     * this system.
     *
     * <p>The namespace becomes the left side of custom block ids, for example
     * {@code myplugin:lamp}. Keep it stable once users have placed blocks in a
     * world.</p>
     *
     * @return lowercase namespace for this system
     */
    String getNamespace();

    /**
     * Namespaces that must be present before this system can register blocks.
     *
     * <p>BlockEngine validates these during plugin discovery. If any namespace
     * is not provided by another enabled {@link CustomBlockSystem}, this system
     * is skipped so partially registered dependencies do not fail later at
     * runtime.</p>
     */
    default @NotNull List<String> requiredNamespaces() {
        return List.of();
    }

    /**
     * Registers this system's code-driven block adapters.
     *
     * <p>BlockEngine calls this during startup. Return deterministic adapter
     * instances for all blocks that should exist for this plugin. Each adapter's
     * local {@link BlockAdapter#name()} is combined with
     * {@link #getNamespace()} to produce the full block id.</p>
     *
     * @return adapters contributed by this system
     */
    List<BlockAdapter> registerAdapters();

    /**
     * Configures metadata and static asset roots for this system's generated
     * resource pack.
     *
     * <p>The provided details object is mutable only during registration. Use it
     * to set the pack title, prompt, icon, required flag, URL ending, and asset
     * roots containing files that should be copied into the pack.</p>
     *
     * @param details mutable pack details for this system
     */
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

    /**
     * Mutable resource-pack metadata contributed by a custom block system.
     *
     * <p>BlockEngine creates one instance during startup and passes it to
     * {@link CustomBlockSystem#setPackDetails(PackDetails)}. Values are read
     * later by the pack generator.</p>
     */
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

        /**
         * Returns the pack icon path, if one was configured.
         *
         * @return path to a pack icon image, or null
         */
        public @Nullable Path icon() {
            return icon;
        }

        /**
         * Sets the pack icon path.
         *
         * @param icon path to the icon image, or null to clear it
         * @return this details object
         */
        public @NotNull PackDetails icon(@Nullable Path icon) {
            this.icon = icon;
            return this;
        }

        /**
         * Returns the display title for this system's pack contribution.
         *
         * @return pack title component
         */
        public @NotNull Component title() {
            return title;
        }

        /**
         * Sets the pack title from MiniMessage text.
         *
         * @param title MiniMessage title, or null/blank to clear it
         * @return this details object
         */
        public @NotNull PackDetails title(@Nullable String title) {
            return title(title == null || title.isBlank() ? Component.empty() : MINI.deserialize(title));
        }

        /**
         * Sets the pack title component.
         *
         * @param title title component, or null to clear it
         * @return this details object
         */
        public @NotNull PackDetails title(@Nullable Component title) {
            this.title = title == null ? Component.empty() : title;
            return this;
        }

        /**
         * Returns the descriptive text for this system's pack contribution.
         *
         * @return pack description component
         */
        public @NotNull Component description() {
            return description;
        }

        /**
         * Sets the pack description from MiniMessage text.
         *
         * @param description MiniMessage description, or null/blank to clear it
         * @return this details object
         */
        public @NotNull PackDetails description(@Nullable String description) {
            return description(description == null || description.isBlank()
                    ? Component.empty()
                    : MINI.deserialize(description));
        }

        /**
         * Sets the pack description component.
         *
         * @param description description component, or null to clear it
         * @return this details object
         */
        public @NotNull PackDetails description(@Nullable Component description) {
            this.description = description == null ? Component.empty() : description;
            return this;
        }

        /**
         * Returns the prompt shown to players when the pack is offered.
         *
         * @return resource-pack prompt component
         */
        public @NotNull Component prompt() {
            return prompt;
        }

        /**
         * Sets the resource-pack prompt from MiniMessage text.
         *
         * @param prompt MiniMessage prompt, or null/blank to clear it
         * @return this details object
         */
        public @NotNull PackDetails prompt(@Nullable String prompt) {
            return prompt(prompt == null || prompt.isBlank() ? Component.empty() : MINI.deserialize(prompt));
        }

        /**
         * Sets the resource-pack prompt component.
         *
         * @param prompt prompt component, or null to clear it
         * @return this details object
         */
        public @NotNull PackDetails prompt(@Nullable Component prompt) {
            this.prompt = prompt == null ? Component.empty() : prompt;
            return this;
        }

        /**
         * Returns the URL suffix used when serving this pack contribution.
         *
         * @return configured URL ending
         */
        public @NotNull String urlEnding() {
            return urlEnding;
        }

        /**
         * Sets the URL suffix used when serving this pack contribution.
         *
         * @param urlEnding stable URL ending
         * @return this details object
         */
        public @NotNull PackDetails urlEnding(@NotNull String urlEnding) {
            this.urlEnding = Objects.requireNonNull(urlEnding, "urlEnding");
            return this;
        }

        /**
         * Returns whether players are required to accept the generated pack.
         *
         * @return true when the pack should be required
         */
        public boolean required() {
            return required;
        }

        /**
         * Sets whether players are required to accept the generated pack.
         *
         * @param required true to require the pack
         * @return this details object
         */
        public @NotNull PackDetails required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Returns namespaces whose static assets are contributed by this system.
         *
         * @return immutable namespace list
         */
        public @NotNull List<String> assetNamespaces() {
            return Collections.unmodifiableList(assetNamespaces);
        }

        /**
         * Adds one asset namespace.
         *
         * @param namespace namespace containing pack assets
         * @return this details object
         */
        public @NotNull PackDetails assetNamespace(@NotNull String namespace) {
            assetNamespaces.add(Objects.requireNonNull(namespace, "namespace"));
            return this;
        }

        /**
         * Adds multiple asset namespaces.
         *
         * @param namespaces namespaces containing pack assets
         * @return this details object
         */
        public @NotNull PackDetails assetNamespaces(@NotNull String... namespaces) {
            for (String namespace : namespaces) {
                assetNamespace(namespace);
            }
            return this;
        }

        /**
         * Returns asset roots copied into the generated pack.
         *
         * @return immutable asset root list
         */
        public @NotNull List<Path> assets() {
            return Collections.unmodifiableList(assets);
        }

        /**
         * Adds an asset root directory to copy into the generated pack.
         *
         * <p>The root should contain normal resource-pack folders such as
         * {@code assets/<namespace>/textures/...}.</p>
         *
         * @param root asset root path
         * @return this details object
         */
        public @NotNull PackDetails addAssets(@NotNull Path root) {
            assets.add(Objects.requireNonNull(root, "root"));
            return this;
        }

        /**
         * Adds multiple asset root directories.
         *
         * @param roots asset root paths
         * @return this details object
         */
        public @NotNull PackDetails addAssets(@NotNull Path... roots) {
            for (Path root : roots) {
                addAssets(root);
            }
            return this;
        }
    }
}
