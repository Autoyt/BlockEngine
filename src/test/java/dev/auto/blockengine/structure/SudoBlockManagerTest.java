package dev.auto.blockengine.structure;

import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SudoBlockManagerTest {
    @Test
    void transformsTheInclusiveFarCornerAroundTheStructureOrigin() {
        BlockVector size = new BlockVector(5, 3, 2);

        assertEquals(new BlockVector(-1, 2, 4),
                SudoBlockManager.structureExtent(size, Mirror.NONE, StructureRotation.CLOCKWISE_90));
        assertEquals(new BlockVector(1, 2, 4),
                SudoBlockManager.structureExtent(size, Mirror.LEFT_RIGHT, StructureRotation.CLOCKWISE_90));
        assertEquals(new BlockVector(1, 2, 4),
                SudoBlockManager.structureExtent(size, Mirror.FRONT_BACK, StructureRotation.COUNTERCLOCKWISE_90));
    }

    @Test
    void aSingleBlockStaysAtTheOriginUnderEveryTransformation() {
        BlockVector size = new BlockVector(1, 1, 1);
        for (Mirror mirror : Mirror.values()) {
            for (StructureRotation rotation : StructureRotation.values()) {
                assertEquals(new BlockVector(), SudoBlockManager.structureExtent(size, mirror, rotation));
            }
        }
    }
}
