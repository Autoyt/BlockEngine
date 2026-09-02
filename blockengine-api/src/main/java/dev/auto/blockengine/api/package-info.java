/**
 * Public entry points for integrating with BlockEngine.
 *
 * <p>Most plugins start with {@link dev.auto.blockengine.api.CustomBlockSystem}
 * to register their custom blocks and resource-pack details, then use
 * {@link dev.auto.blockengine.api.BlockEngine} at runtime to access managed
 * worlds and lower-level services.</p>
 *
 * <p>BlockEngine keeps its API split by responsibility: block definitions and
 * adapter callbacks live in {@link dev.auto.blockengine.api.blocks}, managed
 * client-side display entities live in {@link dev.auto.blockengine.api.display},
 * generated pack helpers live in {@link dev.auto.blockengine.api.resourcepack},
 * world mutation helpers live in {@link dev.auto.blockengine.api.world}, and
 * Bukkit events live in {@link dev.auto.blockengine.api.event}.</p>
 */
package dev.auto.blockengine.api;
