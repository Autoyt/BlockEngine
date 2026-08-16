package dev.auto.turtle.mining;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

final class MiningSpeedResolver {
    private MiningSpeedResolver() {
    }

    static float resolve(@NotNull Player player, @NotNull Block targetBlock) {
        ItemStack item = player.getInventory().getItemInMainHand();
        float speed = baseToolSpeed(item, targetBlock);

        Enchantment efficiency = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"));
        if (efficiency != null && item.containsEnchantment(efficiency)) {
            int level = item.getEnchantmentLevel(efficiency);
            speed += level * level + 1.0f;
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
        if (player.isInWater()) {
            speed /= 5.0f;
        }

        return Math.max(0.05f, speed);
    }

    private static PotionEffect potion(@NotNull Player player, @NotNull String key) {
        PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(key));
        return type == null ? null : player.getPotionEffect(type);
    }

    private static float baseToolSpeed(@NotNull ItemStack item, @NotNull Block targetBlock) {
        String itemName = item.getType().name().toLowerCase(Locale.ROOT);
        String blockName = targetBlock.getType().name().toLowerCase(Locale.ROOT);

        if (isPickaxe(itemName) && isPickaxeBlock(blockName)) {
            return tierSpeed(itemName);
        }
        if (isAxe(itemName) && isAxeBlock(blockName)) {
            return tierSpeed(itemName);
        }
        if (isShovel(itemName) && isShovelBlock(blockName)) {
            return tierSpeed(itemName);
        }
        if (isHoe(itemName) && isHoeBlock(blockName)) {
            return tierSpeed(itemName);
        }
        if (itemName.endsWith("_sword") && blockName.contains("web")) {
            return 15.0f;
        }
        if (itemName.equals("shears") && (blockName.contains("wool") || blockName.contains("leaves") || blockName.contains("web"))) {
            return 5.0f;
        }
        return 1.0f;
    }

    private static float tierSpeed(@NotNull String itemName) {
        if (itemName.startsWith("wooden_")) {
            return 2.0f;
        }
        if (itemName.startsWith("stone_")) {
            return 4.0f;
        }
        if (itemName.startsWith("iron_")) {
            return 6.0f;
        }
        if (itemName.startsWith("diamond_")) {
            return 8.0f;
        }
        if (itemName.startsWith("netherite_")) {
            return 9.0f;
        }
        if (itemName.startsWith("golden_")) {
            return 12.0f;
        }
        return 1.0f;
    }

    private static boolean isPickaxe(@NotNull String itemName) {
        return itemName.endsWith("_pickaxe");
    }

    private static boolean isAxe(@NotNull String itemName) {
        return itemName.endsWith("_axe");
    }

    private static boolean isShovel(@NotNull String itemName) {
        return itemName.endsWith("_shovel");
    }

    private static boolean isHoe(@NotNull String itemName) {
        return itemName.endsWith("_hoe");
    }

    private static boolean isPickaxeBlock(@NotNull String blockName) {
        return blockName.contains("stone")
                || blockName.contains("ore")
                || blockName.contains("metal")
                || blockName.contains("deepslate")
                || blockName.contains("copper")
                || blockName.contains("iron")
                || blockName.contains("gold")
                || blockName.contains("diamond")
                || blockName.contains("emerald")
                || blockName.contains("lapis")
                || blockName.contains("redstone")
                || blockName.contains("coal")
                || blockName.contains("quartz")
                || blockName.contains("brick")
                || blockName.contains("concrete");
    }

    private static boolean isAxeBlock(@NotNull String blockName) {
        return blockName.contains("log")
                || blockName.contains("wood")
                || blockName.contains("planks")
                || blockName.contains("stem")
                || blockName.contains("hyphae")
                || blockName.contains("bamboo")
                || blockName.contains("chest")
                || blockName.contains("barrel");
    }

    private static boolean isShovelBlock(@NotNull String blockName) {
        return blockName.contains("dirt")
                || blockName.contains("grass_block")
                || blockName.contains("sand")
                || blockName.contains("gravel")
                || blockName.contains("clay")
                || blockName.contains("snow")
                || blockName.contains("soul_sand")
                || blockName.contains("soul_soil");
    }

    private static boolean isHoeBlock(@NotNull String blockName) {
        return blockName.contains("leaves")
                || blockName.contains("hay")
                || blockName.contains("target")
                || blockName.contains("sculk")
                || blockName.contains("moss");
    }
}
