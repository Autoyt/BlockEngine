package dev.auto.blockengine.placement;

import dev.auto.blockengine.api.blocks.BlockDefinition.Placement;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/** The fixed item-display rotation of a placed block, independent of its adapter state. */
public enum BlockOrientation {
    IDENTITY(new Quaternionf()),
    NORTH(new Quaternionf().rotationY((float) Math.PI)),
    EAST(new Quaternionf().rotationY((float) Math.toRadians(90))),
    SOUTH(new Quaternionf()),
    WEST(new Quaternionf().rotationY((float) Math.toRadians(-90))),
    UP(new Quaternionf().rotationX((float) Math.toRadians(-90))),
    DOWN(new Quaternionf().rotationX((float) Math.toRadians(90))),
    AXIS_X(new Quaternionf().rotationZ((float) Math.toRadians(-90))),
    AXIS_Y(new Quaternionf()),
    AXIS_Z(new Quaternionf().rotationX((float) Math.toRadians(90)));

    // Minecraft's item-display renderer turns the item model 180 degrees around Y.
    // Its unrotated north/front texture therefore points south, while the top still points up.
    private final Quaternionfc rotation;

    BlockOrientation(Quaternionf rotation) {
        this.rotation = rotation;
    }

    public static @NotNull BlockOrientation forPlacement(
            @NotNull Placement placement, @Nullable BlockFace playerFacing, @Nullable BlockFace clickedFace
    ) {
        return switch (placement) {
            case NONE -> IDENTITY;
            case HORIZONTAL_FACING -> horizontal(playerFacing);
            case AXIS -> switch (clickedFace) {
                case EAST, WEST -> AXIS_X;
                case NORTH, SOUTH -> AXIS_Z;
                case null, default -> AXIS_Y;
            };
            case DIRECTIONAL -> switch (clickedFace == null ? BlockFace.UP : clickedFace) {
                case NORTH -> NORTH;
                case SOUTH -> SOUTH;
                case EAST -> EAST;
                case WEST -> WEST;
                case DOWN -> DOWN;
                case UP -> UP;
                default -> horizontal(playerFacing);
            };
        };
    }

    private static BlockOrientation horizontal(@Nullable BlockFace playerFacing) {
        return switch (playerFacing) {
            case NORTH -> SOUTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case null, default -> NORTH;
        };
    }

    public @NotNull Quaternionfc rotation() {
        return rotation;
    }
}
