/**
 * Code-driven custom block definitions, state data, and adapter callbacks.
 *
 * <p>This package is the main API surface for plugins that want to define
 * blocks in Java. A {@link dev.auto.blockengine.api.blocks.BlockAdapter}
 * describes one custom block, fills a
 * {@link dev.auto.blockengine.api.blocks.BlockDefinition.Builder}, and receives
 * placement, break, interaction, tick, movement, save, and load callbacks.</p>
 *
 * <p>BlockEngine owns the world storage and backing vanilla block. Adapters own
 * custom behavior and any private payload they choose to serialize.</p>
 */
package dev.auto.blockengine.api.blocks;
