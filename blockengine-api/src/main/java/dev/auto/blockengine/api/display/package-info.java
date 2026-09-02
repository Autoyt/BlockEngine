/**
 * Low-level access to client-side display entities managed by BlockEngine.
 *
 * <p>This package is for plugins that need visuals beyond normal custom block
 * rendering, such as attached props, animated overlays, per-player markers, or
 * extra item-display models. Displays created through this API are
 * BlockEngine-managed packet entities rather than Bukkit entities. BlockEngine
 * owns their entity ids, visibility refreshes, persistence, cleanup, and
 * chunk/block lifecycle integration.</p>
 *
 * <p>Most custom blocks should prefer normal block definitions, states, and
 * textures. Use this package when you intentionally need a lower-level display
 * primitive controlled by your plugin.</p>
 */
package dev.auto.blockengine.api.display;
