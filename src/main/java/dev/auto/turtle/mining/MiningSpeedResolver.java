package dev.auto.turtle.mining;

import dev.auto.turtle.placement.TurtleVanillaRules;
import dev.auto.turtle.runtime.RuntimeBlockView;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

final class MiningSpeedResolver {
    private MiningSpeedResolver() {
    }

    static float progressPerTick(@NotNull Player player, @NotNull RuntimeBlockView customBlock) {
        BlockData blockData = TurtleVanillaRules.blockData(customBlock);
        float hardness = Math.max(0.05f, customBlock.storedBlock().hardness());
        float speed = destroySpeed(player, blockData);
        float divisor = correctForDrops(player, blockData) ? 30.0f : 100.0f;
        float customSpeed = Math.max(0.05f, customBlock.storedBlock().miningSpeed());
        return Math.max(0.0f, (speed / hardness / divisor) * customSpeed);
    }

    static boolean shouldDrop(@NotNull Player player, @NotNull RuntimeBlockView customBlock) {
        BlockData blockData = TurtleVanillaRules.blockData(customBlock);
        return customBlock.storedBlock().dropsItem() && correctForDrops(player, blockData);
    }

    static float resolve(@NotNull Player player, @NotNull Block targetBlock) {
        ItemStack item = player.getInventory().getItemInMainHand();
        float speed = destroySpeed(item, targetBlock.getBlockData());
        return applyPlayerModifiers(player, item, speed);
    }

    private static float destroySpeed(@NotNull Player player, @NotNull BlockData blockData) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return applyPlayerModifiers(player, item, destroySpeed(item, blockData));
    }

    private static float destroySpeed(@NotNull ItemStack item, @NotNull BlockData blockData) {
        Tool tool = item.getData(DataComponentTypes.TOOL);
        if (tool == null) {
            return 1.0f;
        }

        float speed = tool.defaultMiningSpeed();
        BlockType blockType = blockData.getMaterial().asBlockType();
        if (blockType == null) {
            return speed;
        }

        TypedKey<BlockType> key = TypedKey.create(RegistryKey.BLOCK, blockType.getKey());
        for (Tool.Rule rule : tool.rules()) {
            if (!rule.blocks().contains(key) || rule.speed() == null) {
                continue;
            }
            speed = rule.speed();
            break;
        }
        return speed;
    }

    private static float applyPlayerModifiers(
            @NotNull Player player,
            @NotNull ItemStack item,
            float speed
    ) {
        if (speed > 1.0f) {
            Enchantment efficiency = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"));
            if (efficiency != null && item.containsEnchantment(efficiency)) {
                int level = item.getEnchantmentLevel(efficiency);
                speed += level * level + 1.0f;
            }
        }


        PotionEffect haste = potion(player, "haste");
        if (haste != null) {
            speed *= 1.0f + (haste.getAmplifier() + 1) * 0.2f;
        }

        PotionEffect fatigue = potion(player, "mining_fatigue");
        if (fatigue != null) {
            speed *= switch (fatigue.getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
        }

        if (!player.isOnGround()) {
            speed /= 5.0f;
        }
        if (player.isInWater() && !hasAquaAffinity(player)) {
            speed /= 5.0f;
        }

        return Math.max(0.05f, speed);
    }

    private static PotionEffect potion(@NotNull Player player, @NotNull String key) {
        PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(key));
        return type == null ? null : player.getPotionEffect(type);
    }

    private static boolean correctForDrops(@NotNull Player player, @NotNull BlockData blockData) {
        if (!blockData.requiresCorrectToolForDrops()) {
            return true;
        }
        return blockData.isPreferredTool(player.getInventory().getItemInMainHand());
    }

    private static boolean hasAquaAffinity(@NotNull Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null) {
            return false;
        }
        Enchantment aquaAffinity = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("aqua_affinity"));
        return aquaAffinity != null && helmet.containsEnchantment(aquaAffinity);
    }
}
