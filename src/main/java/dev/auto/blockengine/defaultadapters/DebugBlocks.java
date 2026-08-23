package dev.auto.blockengine.defaultadapters;

import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;

public final class DebugBlocks {
    public static final String NAMESPACE = "blockengine_test";
    public static final String RED_BLOCK_ID = NAMESPACE + ":red_block";
    public static final String INVISIBLE_BLOCK_ID = NAMESPACE + ":invisible_block";
    public static final String DEMO_INVENTORY_ID = NAMESPACE + ":demo_inventory";
    public static final String DEMO_BREAK_ID = NAMESPACE + ":demo_break";
    public static final String DEMO_STATE_ID = NAMESPACE + ":demo_state";
    public static final String DEMO_REDSTONE_DIALOG_ID = NAMESPACE + ":demo_redstone_dialog";
    public static final String DEMO_MOVEMENT_ID = NAMESPACE + ":demo_movement";

    private DebugBlocks() {
    }

    public static void register() {
        NamespaceRegistry.load(NAMESPACE);
        BlockRegistry.registerBlock(new RedBlockAdapter(), NAMESPACE);
        BlockRegistry.registerBlock(new InvisibleBlockAdapter(), NAMESPACE);
        BlockRegistry.registerBlock(new DemoInventoryBlockAdapter(), NAMESPACE);
        BlockRegistry.registerBlock(new DemoBreakBlockAdapter(), NAMESPACE);
        BlockRegistry.registerBlock(new DemoStateBlockAdapter(), NAMESPACE);
        BlockRegistry.registerBlock(new DemoRedstoneDialogBlockAdapter(), NAMESPACE);
        BlockRegistry.registerBlock(new DemoMovementBlockAdapter(), NAMESPACE);
    }
}
