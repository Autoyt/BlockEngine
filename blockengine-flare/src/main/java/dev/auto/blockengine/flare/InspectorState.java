package dev.auto.blockengine.flare;

import dev.auto.blockengine.api.inspection.BlockInspection;
import dev.auto.flare.api.FlareRegistration;
import dev.auto.flare.api.schema.Field;
import dev.auto.flare.api.state.StateWriter;

import java.util.UUID;

/** The add-on owns this schema; offsets are compiled by Flare for each player's enabled add-ons. */
final class InspectorState {
    static final String SCHEMA = "target";

    static final int BLOCK_LENGTH = 32;
    static final int STATE_LENGTH = 24;
    static final int MATERIAL_LENGTH = 24;
    static final int WORLD_LENGTH = 16;

    private final FlareRegistration registration;
    private final Field<byte[]> blockId, stateId, material, world;
    private final Field<Integer> x, y, z, displays, kind;
    private final Field<Long> payload;
    private final Field<Float> hardness, miningSpeed;
    private final Field<Boolean> visible, target, exposed, unbreakable;

    InspectorState(FlareRegistration registration) {
        this.registration = registration;
        var schema = registration.schema(SCHEMA, 1);
        blockId = schema.reserveBytes("block_id", BLOCK_LENGTH);
        stateId = schema.reserveBytes("state_id", STATE_LENGTH);
        material = schema.reserveBytes("material", MATERIAL_LENGTH);
        world = schema.reserveBytes("world", WORLD_LENGTH);
        x = schema.i32("x", 0);
        y = schema.i32("y", 0);
        z = schema.i32("z", 0);
        displays = schema.u16("displays", 0);
        kind = schema.u8("kind", 0);
        payload = schema.u32("payload", 0);
        hardness = schema.f32("hardness", 0);
        miningSpeed = schema.f32("mining_speed", 0);
        visible = schema.reserveBit("visible", false);
        target = schema.reserveBit("target", false);
        exposed = schema.reserveBit("exposed", false);
        unbreakable = schema.reserveBit("unbreakable", false);

        // Keep the target identity and every diagnostic tied to that identity in the same publication unit.
        schema.atomicGroup("inspection", blockId, stateId, material, world, x, y, z, displays, kind,
                payload, hardness, miningSpeed, visible, target, exposed, unbreakable);
        schema.register();
    }

    void write(UUID playerId, boolean holdingWand, BlockInspection inspection) {
        registration.player(playerId).update(writer -> write(writer, holdingWand, inspection));
    }

    private void write(StateWriter writer, boolean holdingWand, BlockInspection inspection) {
        writer.set(visible, holdingWand);
        writer.set(target, inspection != null);

        writer.set(blockId, InspectorText.encode(inspection == null ? null : inspection.blockId(), BLOCK_LENGTH));
        writer.set(stateId, InspectorText.encode(inspection == null ? null : inspection.stateId(), STATE_LENGTH));
        writer.set(material, InspectorText.encode(inspection == null ? null : inspection.backingMaterial(), MATERIAL_LENGTH));
        writer.set(world, InspectorText.encode(inspection == null ? null : inspection.worldName(), WORLD_LENGTH));

        writer.set(x, inspection == null ? 0 : inspection.x());
        writer.set(y, inspection == null ? 0 : inspection.y());
        writer.set(z, inspection == null ? 0 : inspection.z());
        writer.set(displays, inspection == null ? 0 : Math.min(65535, inspection.displayCount()));
        writer.set(kind, inspection == null ? 0 : switch (inspection.kind()) {
            case VANILLA -> 0;
            case CUSTOM -> 1;
            case PLACEHOLDER -> 2;
        });
        writer.set(payload, inspection == null ? 0L : (long) inspection.payloadBytes());
        writer.set(hardness, inspection == null ? 0f : inspection.hardness());
        writer.set(miningSpeed, inspection == null ? 0f : inspection.miningSpeed());
        writer.set(exposed, inspection != null && inspection.exposed());
        writer.set(unbreakable, inspection != null && inspection.unbreakable());
    }
}
