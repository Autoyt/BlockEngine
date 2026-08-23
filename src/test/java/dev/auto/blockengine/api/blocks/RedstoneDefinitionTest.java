package dev.auto.blockengine.api.blocks;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedstoneDefinitionTest {
    @Test
    void clampsConfiguredPower() {
        BlockDefinition.Redstone redstone = new BlockDefinition.Redstone(
                Set.of(BlockFace.NORTH), Set.of(BlockFace.SOUTH), -4, 20);

        assertEquals(0, redstone.weakPower());
        assertEquals(15, redstone.strongPower());
    }

    @Test
    void dynamicOutputFacesPropagateEvenWithZeroConfiguredPower() {
        BlockDefinition.Redstone redstone = new BlockDefinition.Redstone(
                Set.of(), Set.of(BlockFace.UP), 0, 0);

        assertTrue(redstone.hasOutputs());
        assertFalse(redstone.hasInputs());
    }

    @Test
    void rejectsNonCartesianFaces() {
        assertThrows(IllegalArgumentException.class, () -> new BlockDefinition.Redstone(
                Set.of(BlockFace.NORTH_EAST), Set.of(), 0, 0));
    }
}
