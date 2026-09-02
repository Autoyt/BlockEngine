/**
 * Bukkit events fired by BlockEngine while custom blocks and chunk data change.
 *
 * <p>Events in this package are intended for plugins that want to observe,
 * cancel, or react to BlockEngine activity without owning the block adapter that
 * caused it. The pre-events expose cancellation where BlockEngine can still
 * stop the operation. The post-events describe work that has already been
 * applied to BlockEngine state.</p>
 */
package dev.auto.blockengine.api.event;
