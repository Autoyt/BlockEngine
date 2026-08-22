package dev.auto.blockengine.placement;

import dev.auto.blockengine.api.blocks.BlockDefinition.Placement;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import org.bukkit.Axis;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VanillaRules {
    private VanillaRules() {
    }

    public static @NotNull BlockData blockData(@NotNull RuntimeBlockView block) {
        return block.storedBlock().fallbackBlock().createBlockData();
    }

    public static @NotNull BlockData placementData(
            @NotNull BlockDefinition definition,
            @Nullable String stateId,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst
    ) {
        BlockData data = definition.apiDefinition().vanillaBlock().createBlockData();
        applyPlacement(definition.apiDefinition().placement(), data, player, placedAgainst);
        return data;
    }

    public static boolean canPlace(
            @NotNull Block target,
            @NotNull BlockDefinition definition,
            @Nullable String stateId,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst
    ) {
        BlockData data = placementData(definition, stateId, player, placedAgainst);
        return target.canPlace(data) && data.isSupported(target);
    }

    private static void applyPlacement(
            @NotNull Placement placement,
            @NotNull BlockData data,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst
    ) {
        switch (placement) {
            case NONE -> {
            }
            case HORIZONTAL_FACING -> applyHorizontalFacing(data, player);
            case AXIS -> applyAxis(data, placedAgainst);
            case DIRECTIONAL -> applyDirectional(data, player, placedAgainst);
        }
    }

    private static void applyHorizontalFacing(@NotNull BlockData data, @Nullable Player player) {
        BlockFace facing = player == null ? BlockFace.NORTH : player.getFacing().getOppositeFace();
        if (data instanceof Directional directional && directional.getFaces().contains(facing)) {
            directional.setFacing(facing);
        } else if (data instanceof Rotatable rotatable) {
            rotatable.setRotation(facing);
        }
    }

    private static void applyAxis(@NotNull BlockData data, @Nullable BlockFace placedAgainst) {
        if (!(data instanceof Orientable orientable)) {
            return;
        }
        Axis axis = axis(placedAgainst);
        if (orientable.getAxes().contains(axis)) {
            orientable.setAxis(axis);
        }
    }

    private static void applyDirectional(
            @NotNull BlockData data,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst
    ) {
        BlockFace facing = placedAgainst == null ? BlockFace.UP : placedAgainst;
        if (data instanceof Directional directional) {
            if (!directional.getFaces().contains(facing) && player != null) {
                facing = player.getFacing().getOppositeFace();
            }
            if (directional.getFaces().contains(facing)) {
                directional.setFacing(facing);
            }
        } else if (data instanceof Rotatable rotatable && player != null) {
            rotatable.setRotation(player.getFacing().getOppositeFace());
        }
    }

    private static @NotNull Axis axis(@Nullable BlockFace face) {
        if (face == null) {
            return Axis.Y;
        }
        return switch (face) {
            case EAST, WEST -> Axis.X;
            case NORTH, SOUTH -> Axis.Z;
            default -> Axis.Y;
        };
    }
}
