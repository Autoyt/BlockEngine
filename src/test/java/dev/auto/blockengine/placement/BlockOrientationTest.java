package dev.auto.blockengine.placement;

import dev.auto.blockengine.api.blocks.BlockDefinition.Placement;
import org.bukkit.block.BlockFace;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockOrientationTest {
    @Test
    void directionalFrontPointsAtTheClickedFace() {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN)) {
            BlockOrientation orientation = BlockOrientation.forPlacement(Placement.DIRECTIONAL, null, face);
            assertPointsAt(orientation, new Vector3f(0, 0, 1), face);
        }
        assertEquals(BlockOrientation.UP, BlockOrientation.forPlacement(Placement.DIRECTIONAL, null, null));
    }

    @Test
    void horizontalFrontFacesThePlacingPlayer() {
        for (BlockFace facing : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            BlockOrientation orientation = BlockOrientation.forPlacement(Placement.HORIZONTAL_FACING, facing, BlockFace.UP);
            assertPointsAt(orientation, new Vector3f(0, 0, 1), facing.getOppositeFace());
        }
        assertEquals(BlockOrientation.NORTH, BlockOrientation.forPlacement(Placement.HORIZONTAL_FACING, null, null));
    }

    @Test
    void pillarTopAlignsWithTheClickedAxisFromEitherSide() {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.UP)) {
            BlockOrientation orientation = BlockOrientation.forPlacement(Placement.AXIS, null, face);
            assertEquals(orientation, BlockOrientation.forPlacement(Placement.AXIS, null, face.getOppositeFace()));
            assertPointsAt(orientation, new Vector3f(0, 1, 0), face == BlockFace.NORTH ? BlockFace.SOUTH : face);
        }
        assertEquals(BlockOrientation.AXIS_Y, BlockOrientation.forPlacement(Placement.AXIS, null, null));
        assertEquals(BlockOrientation.IDENTITY, BlockOrientation.forPlacement(Placement.NONE, BlockFace.SOUTH, BlockFace.WEST));
    }

    private static void assertPointsAt(BlockOrientation orientation, Vector3f unrotated, BlockFace expected) {
        Vector3f direction = orientation.rotation().transform(unrotated);
        assertEquals(expected.getModX(), direction.x, 0.00001f);
        assertEquals(expected.getModY(), direction.y, 0.00001f);
        assertEquals(expected.getModZ(), direction.z, 0.00001f);
    }
}
