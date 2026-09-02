/**
 * Helpers for contributing code-generated model JSON to BlockEngine resource packs.
 *
 * <p>These classes generate item model and item definition JSON during
 * BlockEngine's resource-pack build. They do not create texture images. Plugins
 * should provide PNG assets through pack details and then reference those
 * textures with {@link dev.auto.blockengine.api.resourcepack.TextureRef}.</p>
 */
package dev.auto.blockengine.api.resourcepack;
