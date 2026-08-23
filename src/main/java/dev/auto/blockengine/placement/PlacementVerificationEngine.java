package dev.auto.blockengine.placement;

import dev.auto.blockengine.types.BlockDefinition;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlacementVerificationEngine {
    private PlacementVerificationEngine() {
    }

    public static @NotNull Result verify(@NotNull Request request) {
        State state = State.START;
        while (true) {
            switch (state) {
                case START -> state = State.TARGET_REPLACEABLE;
                case TARGET_REPLACEABLE -> {
                    if (!replaceableTarget(request.target())) {
                        return Result.denied(State.TARGET_REPLACEABLE, DenialReason.TARGET_NOT_REPLACEABLE);
                    }
                    if (waterloggedSolid(request.target())) {
                        return Result.denied(State.TARGET_REPLACEABLE, DenialReason.WATERLOGGED_TARGET);
                    }
                    state = State.VANILLA_RULES;
                }
                case VANILLA_RULES -> {
                    if (!VanillaRules.canPlace(
                            request.target(),
                            request.definition(),
                            request.stateId(),
                            request.player(),
                            request.placedAgainst()
                    )) {
                        return Result.denied(State.VANILLA_RULES, DenialReason.VANILLA_RULES_REJECTED);
                    }
                    state = State.BLOCK_CAN_BUILD_EVENT;
                }
                case BLOCK_CAN_BUILD_EVENT -> {
                    if (request.player() == null || request.hand() == null) {
                        state = State.COLLISION_CHECK;
                        continue;
                    }
                    org.bukkit.block.data.BlockData placementData = VanillaRules.placementData(
                            request.definition(),
                            request.stateId(),
                            request.player(),
                            request.placedAgainst()
                    );
                    BlockCanBuildEvent buildEvent = new BlockCanBuildEvent(
                            request.target(),
                            request.player(),
                            placementData,
                            true,
                            request.hand()
                    );
                    Bukkit.getPluginManager().callEvent(buildEvent);
                    if (!buildEvent.isBuildable()) {
                        return Result.denied(State.BLOCK_CAN_BUILD_EVENT, DenialReason.BUILD_EVENT_REJECTED);
                    }
                    state = State.COLLISION_CHECK;
                }
                case COLLISION_CHECK -> {
                    if (occupied(request.target(), request.player())) {
                        return Result.denied(State.COLLISION_CHECK, DenialReason.OCCUPIED);
                    }
                    state = State.APPROVED;
                }
                case APPROVED -> {
                    return Result.approved();
                }
                case DENIED -> throw new IllegalStateException("DENIED is terminal and cannot be executed.");
            }
        }
    }

    private static boolean occupied(@NotNull Block target, @Nullable Player player) {
        BoundingBox blockBox = new BoundingBox(
                target.getX(),
                target.getY(),
                target.getZ(),
                target.getX() + 1.0,
                target.getY() + 1.0,
                target.getZ() + 1.0
        );

        if (player != null && player.getBoundingBox().overlaps(blockBox)) {
            return true;
        }

        for (Entity entity : target.getWorld().getNearbyEntities(blockBox)) {
            if (!entity.isDead()
                    && !(entity instanceof Player targetPlayer && targetPlayer.getGameMode() == GameMode.SPECTATOR)
                    && (entity instanceof LivingEntity
                    || entity instanceof Vehicle
                    || entity.getType() == EntityType.ARMOR_STAND
                    || entity.getType() == EntityType.END_CRYSTAL)
                    && entity.getBoundingBox().overlaps(blockBox)) {
                return true;
            }
        }
        return false;
    }

    private static boolean replaceableTarget(@NotNull Block target) {
        return target.isReplaceable() || target.isLiquid();
    }

    private static boolean waterloggedSolid(@NotNull Block target) {
        return !target.isLiquid()
                && target.getBlockData() instanceof Waterlogged waterlogged
                && waterlogged.isWaterlogged();
    }

    public record Request(
            @NotNull Block target,
            @NotNull BlockDefinition definition,
            @Nullable String stateId,
            @Nullable Player player,
            @Nullable BlockFace placedAgainst,
            @Nullable EquipmentSlot hand,
            @NotNull Source source
    ) {
    }

    public enum Source {
        PLAYER,
        DISPENSER,
        GRAVITY,
        PLUGIN
    }

    public record Result(boolean allowed, @NotNull State state, @Nullable DenialReason reason) {
        private static @NotNull Result approved() {
            return new Result(true, State.APPROVED, null);
        }

        private static @NotNull Result denied(@NotNull State state, @NotNull DenialReason reason) {
            return new Result(false, state, reason);
        }
    }

    public enum State {
        START,
        TARGET_REPLACEABLE,
        VANILLA_RULES,
        BLOCK_CAN_BUILD_EVENT,
        COLLISION_CHECK,
        APPROVED,
        DENIED
    }

    public enum DenialReason {
        TARGET_NOT_REPLACEABLE,
        WATERLOGGED_TARGET,
        VANILLA_RULES_REJECTED,
        BUILD_EVENT_REJECTED,
        OCCUPIED
    }
}
