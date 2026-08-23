package dev.auto.blockengine.defaultadapters;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockContext;
import dev.auto.blockengine.api.blocks.BlockCreateContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import dev.auto.blockengine.chat.BlockEngineChat;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DemoRedstoneDialogBlockAdapter implements BlockAdapter {
    private static final String LOCATION_KEY = "redstone_dialog_location";
    private static final String POWER_KEY = "power";
    private static final Map<UUID, String> WATCHING = new ConcurrentHashMap<>();

    @Override
    public @NotNull String name() {
        return "demo_redstone_dialog";
    }

    @Override
    public void define(@NotNull BlockDefinition.Builder builder) {
        builder
                .setDefaultBlock(Material.REDSTONE_LAMP)
                .defaultState("default")
                .item(item -> item
                        .name("<#ff4545>Demo: Redstone Dialog")
                        .lore("<gray>Right-click to show current redstone power.")
                        .lore("<gray>The dialog refreshes when the power changes.")
                )
                .state("default", state -> state
                        .hardness(0.3f)
                        .miningSpeed(1.0f)
                        .miningProfile(Material.REDSTONE_LAMP)
                        .preferredTool(BlockDefinition.ToolType.PICKAXE)
                        .dropsItem(true)
                        .redstone(redstone -> redstone.inputAllFaces())
                        .textures(textures -> textures.all("blockengine_test:block/demo_redstone_dialog"))
                        .sounds(sounds -> sounds
                                .mining("minecraft:block.glass.hit")
                                .breakSound("minecraft:block.glass.break")
                                .place("minecraft:block.glass.place")
                        )
                );
    }

    @Override
    public @NotNull BlockData createDefaultData(@NotNull BlockCreateContext context) {
        BlockData data = context.createData();
        data.integer(POWER_KEY, 0);
        data.string(LOCATION_KEY, locationKey(context.location()));
        return data;
    }

    @Override
    public boolean onInteract(@NotNull BlockContext context, @NotNull Player player) {
        String locationKey = context.data().string(LOCATION_KEY);
        if (locationKey != null) {
            WATCHING.put(player.getUniqueId(), locationKey);
        }
        player.showDialog(powerDialog(power(context.data())));
        return true;
    }

    @Override
    public void onRedstonePowerChange(@NotNull BlockContext context, int oldPower, int newPower) {
        context.data().integer(POWER_KEY, newPower);
        String locationKey = context.data().string(LOCATION_KEY);
        if (locationKey == null) {
            return;
        }

        Dialog dialog = powerDialog(newPower);
        Iterator<Map.Entry<UUID, String>> iterator = WATCHING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, String> entry = iterator.next();
            if (!locationKey.equals(entry.getValue())) {
                continue;
            }

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }
            player.showDialog(dialog);
        }
    }

    private static int power(@NotNull BlockData data) {
        Integer power = data.integer(POWER_KEY);
        return power == null ? 0 : Math.clamp(power, 0, 15);
    }

    private static @NotNull Dialog powerDialog(int power) {
        Component title = Component.text("Redstone Power", BlockEngineChat.ORANGE).decorate(TextDecoration.BOLD);
        Component summary = Component.text()
                .append(Component.text("Current power: ", BlockEngineChat.GRAY))
                .append(Component.text(power, power > 0 ? BlockEngineChat.SUCCESS : BlockEngineChat.WHITE))
                .append(Component.text(" / 15", BlockEngineChat.GRAY))
                .build();

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .externalTitle(Component.text("BlockEngine", BlockEngineChat.ORANGE))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .body(List.of(DialogBody.plainMessage(summary, 180)))
                        .build())
                .type(DialogType.notice()));
    }

    private static @NotNull String locationKey(@NotNull Location location) {
        World world = location.getWorld();
        String worldId = world == null ? "unknown" : world.getUID().toString();
        return worldId
                + ":" + location.getBlockX()
                + ":" + location.getBlockY()
                + ":" + location.getBlockZ();
    }
}
