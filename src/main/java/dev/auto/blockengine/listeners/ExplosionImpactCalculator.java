package dev.auto.blockengine.listeners;

import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ExplosionImpactCalculator {
    private ExplosionImpactCalculator() {
    }

    static @NotNull Set<BlockLocationKey> affectedByExplosion(@NotNull Location origin, @NotNull List<Block> blocks) {
        return affectedByExplosion(origin, radius(origin, blocks));
    }

    private static double radius(@NotNull Location origin, @NotNull List<Block> blocks) {
        double radius = 4.0;
        for (Block block : blocks) {
            radius = Math.max(radius, block.getLocation().add(0.5, 0.5, 0.5).distance(origin));
        }
        return radius + 1.0;
    }

    private static @NotNull Set<BlockLocationKey> affectedByExplosion(@NotNull Location origin, double radius) {
        Set<BlockLocationKey> affected = new HashSet<>();
        World world = origin.getWorld();
        if (world == null) {
            return affected;
        }

        double power = Math.max(0.5, radius / 2.0);
        int samples = 16;
        for (int x = 0; x < samples; x++) {
            for (int y = 0; y < samples; y++) {
                for (int z = 0; z < samples; z++) {
                    if (x != 0 && x != samples - 1
                            && y != 0 && y != samples - 1
                            && z != 0 && z != samples - 1) {
                        continue;
                    }
                    ray(origin, world, affected, power, dir(x, samples), dir(y, samples), dir(z, samples));
                }
            }
        }
        return affected;
    }

    private static void ray(
            @NotNull Location origin,
            @NotNull World world,
            @NotNull Set<BlockLocationKey> affected,
            double explosionPower,
            double dx,
            double dy,
            double dz
    ) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= length;
        dy /= length;
        dz /= length;

        double power = explosionPower * (0.7 + Math.random() * 0.6);
        double x = origin.getX();
        double y = origin.getY();
        double z = origin.getZ();

        while (power > 0.0) {
            int blockX = floor(x);
            int blockY = floor(y);
            int blockZ = floor(z);
            Block block = world.getBlockAt(blockX, blockY, blockZ);
            if (protectedByWater(block)) {
                return;
            }

            BlockLocationKey location = new BlockLocationKey(world.getUID(), blockX, blockY, blockZ);
            RuntimeBlockView customBlock = ChunkEngine.getBlock(location);
            float resistance = customBlock == null
                    ? block.getType().getBlastResistance()
                    : Math.max(0.05f, customBlock.storedBlock().hardness());
            if (!block.getType().isAir() || customBlock != null) {
                power -= (resistance + 0.3f) * 0.3f;
            }

            if (power > 0.0) {
                affected.add(location);
            }

            x += dx * 0.3;
            y += dy * 0.3;
            z += dz * 0.3;
            power -= 0.225;
        }
    }

    private static boolean protectedByWater(@NotNull Block block) {
        return block.getType() == Material.WATER
                || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private static double dir(int value, int samples) {
        return (value / (double) (samples - 1)) * 2.0 - 1.0;
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }
}
