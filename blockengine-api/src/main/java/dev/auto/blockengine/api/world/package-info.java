/**
 * BlockEngine-aware world operations.
 *
 * <p>Use {@link dev.auto.blockengine.api.BlockEngine#world(org.bukkit.World)}
 * to obtain a managed world wrapper before placing, clearing, removing, or
 * reconciling custom blocks. These methods route mutations through BlockEngine's
 * normal persistence, display, and event pipelines.</p>
 */
package dev.auto.blockengine.api.world;
