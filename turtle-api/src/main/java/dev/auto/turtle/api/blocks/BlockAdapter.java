package dev.auto.turtle.api.blocks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Shared block type contract used by both the Turtle runtime and usage plugins.
 *
 * <p>The adapter owns the block definition and behavior hooks. By default, the
 * accessors on this class read from the attached {@link BlockDefinition}. Usage
 * plugins can override any accessor or hook and code will always win over the
 * definition-backed defaults.</p>
 */
public abstract class BlockAdapter {
    private @Nullable BlockDefinition definition;

    /**
     * Allows an adapter to advertise an optional JSON definition path.
     *
     * <p>The runtime may load this file and hydrate {@link #definition()} before
     * registration. Adapters are still free to override values in code after the
     * definition has been attached.</p>
     *
     * @return the optional path to a block definition JSON file
     */
    public @Nullable Path jsonDefinitionPath() {
        return null;
    }

    /**
     * Called after the runtime attaches a resolved block definition.
     *
     * <p>Adapters may use this hook to apply final code-side overrides or to
     * validate that required sections exist.</p>
     */
    public void onDefinitionAttached(@NotNull BlockDefinition definition) {
    }

    /**
     * Attaches the resolved block definition to this adapter using the owning
     * plugin namespace supplied by the runtime.
     */
    public final void attachDefinition(@NotNull String namespace, @NotNull BlockDefinition definition) {
        BlockDefinition resolvedDefinition = Objects.requireNonNull(definition, "definition");
        resolvedDefinition.namespace(Objects.requireNonNull(namespace, "namespace"));
        this.definition = resolvedDefinition;
        onDefinitionAttached(resolvedDefinition);
    }

    /**
     * Attaches the resolved block definition to this adapter.
     */
    public final void attachDefinition(@NotNull BlockDefinition definition) {
        BlockDefinition resolvedDefinition = Objects.requireNonNull(definition, "definition");
        this.definition = resolvedDefinition;
        onDefinitionAttached(resolvedDefinition);
    }

    /**
     * Returns the resolved definition used by this adapter.
     */
    public final @NotNull BlockDefinition definition() {
        if (definition == null) {
            throw new IllegalStateException("BlockDefinition has not been attached yet.");
        }
        return definition;
    }

    /**
     * @return the unique block id
     */
    public @NotNull String id() {
        return definition().id();
    }

    /**
     * @return the owning plugin namespace for this block
     */
    public @NotNull String namespace() {
        String namespace = definition().namespace();
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalStateException("BlockDefinition namespace has not been attached yet.");
        }
        return namespace;
    }

    /**
     * @return the local block name within the owning plugin namespace
     */
    public @NotNull String name() {
        return definition().name();
    }

    /**
     * @return the default blockstate id
     */
    public @NotNull String defaultStateId() {
        return definition().defaultStateId();
    }

    /**
     * @return the item definition exposed for the block type
     */
    public @NotNull BlockDefinition.ItemDefinition item() {
        return definition().item();
    }

    /**
     * @return the placement behavior for the block type
     */
    public @NotNull BlockDefinition.PlacementDefinition placement() {
        return definition().placement();
    }

    /**
     * Resolves the requested state, falling back to the adapter's default state.
     */
    public @NotNull BlockDefinition.StateDefinition state(@Nullable String stateId) {
        String resolved = stateId == null || stateId.isBlank() ? defaultStateId() : stateId;
        return definition().state(resolved);
    }

    /**
     * @return the resolved hardness for the given state
     */
    public float getBlockHardness(@Nullable String stateId) {
        return state(stateId).properties().hardness();
    }

    /**
     * @return the light level emitted by the given state
     */
    public int getBlockLightLevel(@Nullable String stateId) {
        return state(stateId).properties().lightLevel();
    }

    /**
     * @return true if the block can be wiped away by a fluid
     */
    public boolean washable(@Nullable String stateId) {
        return state(stateId).properties().washable();
    }

    /**
     * @return true if the block can be broken
     */
    public boolean breakable(@Nullable String stateId) {
        return state(stateId).properties().breakable();
    }

    /**
     * @return the texture state reference for the given blockstate
     */
    public @Nullable String textureState(@Nullable String stateId) {
        return state(stateId).textureState();
    }

    /**
     * @return the sound state reference for the given blockstate
     */
    public @Nullable String soundState(@Nullable String stateId) {
        return state(stateId).soundState();
    }

    /**
     * @return the animation state reference for the given blockstate
     */
    public @Nullable String animationState(@Nullable String stateId) {
        return state(stateId).animationState();
    }

    public void onPlace(@NotNull BlockContext context) {
    }

    public void onBreak(@NotNull BlockContext context) {
    }

    public void onInteract(@NotNull BlockContext context) {
    }

    public void onNeighborChanged(@NotNull BlockContext context) {
    }

    public void onRedstoneChanged(@NotNull BlockContext context, int oldPower, int newPower) {
    }

    public void onLightChanged(@NotNull BlockContext context, int oldLightLevel, int newLightLevel) {
    }

    public void onStateEnter(@NotNull BlockContext context, @NotNull String previousStateId, @NotNull String newStateId) {
    }

    public void onStateExit(@NotNull BlockContext context, @NotNull String previousStateId, @NotNull String newStateId) {
    }
}
