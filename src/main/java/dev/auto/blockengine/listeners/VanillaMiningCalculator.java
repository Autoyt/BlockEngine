package dev.auto.blockengine.listeners;

import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

final class VanillaMiningCalculator {
    private VanillaMiningCalculator() {
    }

    static float progressPerTick(@NotNull Player player, @NotNull RuntimeBlockView customBlock) {
        if (customBlock.storedBlock().unbreakable()) {
            return 0.0f;
        }

        float hardness = customBlock.storedBlock().hardness();
        if (hardness == -1.0f) {
            return 0.0f;
        }

        MiningSettings settings = miningSettings(customBlock);
        BlockData profile = settings.profile().createBlockData();
        int divisor = hasCorrectToolForDrops(player, profile, settings) ? 30 : 100;
        return destroySpeed(player, profile, settings) / Math.max(0.0001f, hardness) / divisor;
    }

    static boolean canHarvest(@NotNull Player player, @NotNull RuntimeBlockView customBlock) {
        MiningSettings settings = miningSettings(customBlock);
        BlockData profile = settings.profile().createBlockData();
        return hasCorrectToolForDrops(player, profile, settings) && hasRequiredSilkTouch(player, settings);
    }

    private static float destroySpeed(
            @NotNull Player player,
            @NotNull BlockData profile,
            @NotNull MiningSettings settings
    ) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        float speed = Math.max(profile.getDestroySpeed(tool, false), preferredToolSpeed(tool, settings));

        if (speed > 1.0f) {
            speed += (float) attributeValue(player, Attribute.MINING_EFFICIENCY, 0.0);
        }

        int digSpeedAmplifier = digSpeedAmplifier(player);
        if (digSpeedAmplifier >= 0) {
            speed *= 1.0f + (digSpeedAmplifier + 1) * 0.2f;
        }

        PotionEffect fatigue = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);
        if (fatigue != null) {
            speed *= switch (fatigue.getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
        }

        speed *= (float) attributeValue(player, Attribute.BLOCK_BREAK_SPEED, 1.0);

        if (player.getEyeLocation().getBlock().getType() == Material.WATER) {
            speed *= (float) attributeValue(player, Attribute.SUBMERGED_MINING_SPEED, 0.2);
        }

        if (!player.isOnGround()) {
            speed /= 5.0f;
        }

        return speed;
    }

    private static int digSpeedAmplifier(@NotNull Player player) {
        int amplifier = -1;
        PotionEffect haste = player.getPotionEffect(PotionEffectType.HASTE);
        if (haste != null) {
            amplifier = Math.max(amplifier, haste.getAmplifier());
        }
        PotionEffect conduit = player.getPotionEffect(PotionEffectType.CONDUIT_POWER);
        if (conduit != null) {
            amplifier = Math.max(amplifier, conduit.getAmplifier());
        }
        return amplifier;
    }

    private static boolean hasCorrectToolForDrops(
            @NotNull Player player,
            @NotNull BlockData profile,
            @NotNull MiningSettings settings
    ) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (settings.requirePreferredToolForDrops()) {
            return preferredTool(tool, settings);
        }
        return !profile.requiresCorrectToolForDrops() || profile.isPreferredTool(tool);
    }

    private static boolean hasRequiredSilkTouch(@NotNull Player player, @NotNull MiningSettings settings) {
        return !settings.requireSilkTouchForDrops()
                || player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH);
    }

    private static float preferredToolSpeed(@NotNull ItemStack tool, @NotNull MiningSettings settings) {
        if (!preferredTool(tool, settings)) {
            return 1.0f;
        }
        Material material = tool.getType();
        if (material == Material.SHEARS) {
            return 5.0f;
        }
        if (type(material, "SWORD")) {
            return 1.5f;
        }
        return tierSpeed(material);
    }

    private static boolean preferredTool(@NotNull ItemStack tool, @NotNull MiningSettings settings) {
        Set<dev.auto.blockengine.api.blocks.BlockDefinition.ToolType> tools = settings.preferredTools();
        if (tools.isEmpty()) {
            return false;
        }

        Material material = tool.getType();
        return tools.stream().anyMatch(toolType -> switch (toolType) {
            case PICKAXE -> type(material, "PICKAXE");
            case AXE -> type(material, "AXE") && !type(material, "PICKAXE");
            case SHOVEL -> type(material, "SHOVEL");
            case HOE -> type(material, "HOE") && !type(material, "SHOVEL");
            case SHEARS -> material == Material.SHEARS;
            case SWORD -> type(material, "SWORD");
        });
    }

    private static boolean type(@NotNull Material material, @NotNull String suffix) {
        return material.name().endsWith("_" + suffix);
    }

    private static float tierSpeed(@NotNull Material material) {
        String name = material.name();
        if (name.startsWith("WOODEN_")) {
            return 2.0f;
        }
        if (name.startsWith("STONE_")) {
            return 4.0f;
        }
        if (name.startsWith("COPPER_")) {
            return 5.0f;
        }
        if (name.startsWith("IRON_")) {
            return 6.0f;
        }
        if (name.startsWith("DIAMOND_")) {
            return 8.0f;
        }
        if (name.startsWith("NETHERITE_")) {
            return 9.0f;
        }
        if (name.startsWith("GOLDEN_")) {
            return 12.0f;
        }
        return 1.0f;
    }

    private static double attributeValue(@NotNull Player player, @NotNull Attribute attribute, double fallback) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    private static @NotNull MiningSettings miningSettings(@NotNull RuntimeBlockView customBlock) {
        BlockDefinition definition = BlockRegistry.getBlock(customBlock.storedBlock().blockId());
        if (definition == null) {
            return MiningSettings.fallback(customBlock.storedBlock().fallbackBlock());
        }

        try {
            var state = definition.apiDefinition().state(customBlock.storedBlock().stateId());
            return new MiningSettings(
                    state.miningProfile(),
                    state.preferredTools(),
                    state.requirePreferredToolForDrops(),
                    state.requireSilkTouchForDrops()
            );
        } catch (IllegalArgumentException ignored) {
            return MiningSettings.fallback(customBlock.storedBlock().fallbackBlock());
        }
    }

    private record MiningSettings(
            @NotNull Material profile,
            @NotNull Set<dev.auto.blockengine.api.blocks.BlockDefinition.ToolType> preferredTools,
            boolean requirePreferredToolForDrops,
            boolean requireSilkTouchForDrops
    ) {
        private static @NotNull MiningSettings fallback(@NotNull Material profile) {
            return new MiningSettings(profile, Set.of(), false, false);
        }
    }
}
