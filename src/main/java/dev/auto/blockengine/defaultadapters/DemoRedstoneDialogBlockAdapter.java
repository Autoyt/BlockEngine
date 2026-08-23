package dev.auto.blockengine.defaultadapters;

import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockContext;
import dev.auto.blockengine.api.blocks.BlockCreateContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import dev.auto.blockengine.chat.BlockEngineChat;
import dev.auto.blockengine.listeners.GameListener;
import dev.auto.blockengine.runtime.BlockDataManager;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockLocationKey;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DemoRedstoneDialogBlockAdapter implements BlockAdapter {
    private static final String LOCATION_KEY = "redstone_dialog_location";
    private static final String POWER_KEY = "power";
    private static final String OUTPUT_POWER_KEY = "output_power";
    private static final String OUTPUT_FACE_KEY = "output_face";
    private static final String OUTPUT_POWER_INPUT = "output_power";
    private static final String OUTPUT_FACE_INPUT = "output_face";
    private static final List<BlockFace> OUTPUT_FACES = List.of(
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
            BlockFace.UP,
            BlockFace.DOWN
    );
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
                        .redstone(redstone -> redstone
                                .inputAllFaces()
                                .outputAllFaces()
                                .weakPower(15)
                        )
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
        data.integer(OUTPUT_POWER_KEY, 0);
        data.string(OUTPUT_FACE_KEY, BlockFace.NORTH.name());
        data.string(LOCATION_KEY, locationKey(context.location()));
        return data;
    }

    @Override
    public boolean onInteract(@NotNull BlockContext context, @NotNull Player player) {
        String locationKey = context.data().string(LOCATION_KEY);
        if (locationKey != null) {
            WATCHING.put(player.getUniqueId(), locationKey);
        }
        player.showDialog(powerDialog(context.data()));
        return true;
    }

    @Override
    public void onRedstonePowerChange(@NotNull BlockContext context, int oldPower, int newPower) {
        context.data().integer(POWER_KEY, newPower);
        String locationKey = context.data().string(LOCATION_KEY);
        if (locationKey == null) {
            return;
        }

        Dialog dialog = powerDialog(context.data());
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

    @Override
    public void onMove(
            @NotNull BlockContext context,
            @NotNull Block from,
            @NotNull Block to,
            @NotNull MoveCause cause
    ) {
        String previous = context.data().string(LOCATION_KEY);
        String moved = locationKey(to.getLocation());
        context.data().string(LOCATION_KEY, moved);
        if (previous != null && !previous.equals(moved)) {
            WATCHING.replaceAll((ignored, watched) -> previous.equals(watched) ? moved : watched);
        }
    }

    public static void cleanup(@NotNull UUID playerId) {
        WATCHING.remove(playerId);
    }

    public static void cleanup(@NotNull Block block) {
        String removed = locationKey(block.getLocation());
        WATCHING.entrySet().removeIf(entry -> removed.equals(entry.getValue()));
    }

    @Override
    public int redstoneWeakPower(
            @NotNull BlockContext context,
            @NotNull BlockFace outputFace,
            int configuredPower
    ) {
        return outputFace == outputFace(context.data())
                ? Math.min(outputPower(context.data()), configuredPower)
                : 0;
    }

    private static int power(@NotNull BlockData data) {
        Integer power = data.integer(POWER_KEY);
        return power == null ? 0 : Math.clamp(power, 0, 15);
    }

    private static int outputPower(@NotNull BlockData data) {
        Integer power = data.integer(OUTPUT_POWER_KEY);
        return power == null ? 0 : Math.clamp(power, 0, 15);
    }

    private static @NotNull BlockFace outputFace(@NotNull BlockData data) {
        String face = data.string(OUTPUT_FACE_KEY);
        if (face == null) {
            return BlockFace.NORTH;
        }
        try {
            BlockFace parsed = BlockFace.valueOf(face);
            return OUTPUT_FACES.contains(parsed) ? parsed : BlockFace.NORTH;
        } catch (IllegalArgumentException ignored) {
            return BlockFace.NORTH;
        }
    }

    private static @NotNull Dialog powerDialog(@NotNull BlockData data) {
        int power = power(data);
        int outputPower = outputPower(data);
        BlockFace outputFace = outputFace(data);
        Component title = Component.text("Redstone Power", BlockEngineChat.ORANGE).decorate(TextDecoration.BOLD);
        Component summary = Component.text()
                .append(Component.text("Current power: ", BlockEngineChat.GRAY))
                .append(Component.text(power, power > 0 ? BlockEngineChat.SUCCESS : BlockEngineChat.WHITE))
                .append(Component.text(" / 15", BlockEngineChat.GRAY))
                .append(Component.newline())
                .append(Component.text("Output: ", BlockEngineChat.GRAY))
                .append(Component.text(outputPower, outputPower > 0 ? BlockEngineChat.SUCCESS : BlockEngineChat.WHITE))
                .append(Component.text(" on " + outputFace.name().toLowerCase(), BlockEngineChat.GRAY))
                .build();

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .externalTitle(Component.text("BlockEngine", BlockEngineChat.ORANGE))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .body(List.of(DialogBody.plainMessage(summary, 180)))
                        .inputs(List.of(
                                DialogInput.numberRange(
                                        OUTPUT_POWER_INPUT,
                                        Component.text("Output power", BlockEngineChat.GRAY),
                                        0.0f,
                                        15.0f
                                )
                                        .width(260)
                                        .labelFormat("%s: %s")
                                        .initial((float) outputPower)
                                        .step(1.0f)
                                        .build(),
                                DialogInput.singleOption(
                                        OUTPUT_FACE_INPUT,
                                        Component.text("Output face", BlockEngineChat.GRAY),
                                        OUTPUT_FACES.stream()
                                                .map(face -> SingleOptionDialogInput.OptionEntry.create(
                                                        face.name(),
                                                        Component.text(face.name().toLowerCase(), BlockEngineChat.WHITE),
                                                        face == outputFace
                                                ))
                                                .toList()
                                )
                                        .width(260)
                                        .labelVisible(true)
                                        .build()
                        ))
                        .build())
                .type(DialogType.confirmation(applyButton(), closeButton())));
    }

    private static @NotNull ActionButton applyButton() {
        return ActionButton.builder(Component.text("Apply", BlockEngineChat.SUCCESS))
                .width(150)
                .action(DialogAction.customClick(
                        DemoRedstoneDialogBlockAdapter::applyDialog,
                        ClickCallback.Options.builder()
                                .uses(Integer.MAX_VALUE)
                                .lifetime(Duration.ofDays(1))
                                .build()
                ))
                .build();
    }

    private static @NotNull ActionButton closeButton() {
        return ActionButton.builder(Component.text("Close", BlockEngineChat.GRAY))
                .width(150)
                .action(DialogAction.customClick(
                        DemoRedstoneDialogBlockAdapter::closeDialog,
                        ClickCallback.Options.builder()
                                .uses(Integer.MAX_VALUE)
                                .lifetime(Duration.ofDays(1))
                                .build()
                ))
                .build();
    }

    private static void closeDialog(@NotNull DialogResponseView response, @NotNull Audience audience) {
        if (audience instanceof Player player) {
            WATCHING.remove(player.getUniqueId());
        }
    }

    private static void applyDialog(@NotNull DialogResponseView response, @NotNull Audience audience) {
        if (!(audience instanceof Player player)) {
            return;
        }

        String locationKey = WATCHING.get(player.getUniqueId());
        if (locationKey == null) {
            return;
        }

        Block block = block(locationKey);
        if (block == null) {
            return;
        }

        RuntimeBlockView view = ChunkEngine.getBlock(new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
        if (view == null || !Objects.equals(view.storedBlock().blockId(), DebugBlocks.DEMO_REDSTONE_DIALOG_ID)) {
            WATCHING.remove(player.getUniqueId(), locationKey);
            return;
        }

        dev.auto.blockengine.runtime.BlockContext context = BlockDataManager.getInstance().context(block, view, player);
        if (context == null) {
            return;
        }

        Float selectedPower = response.getFloat(OUTPUT_POWER_INPUT);
        String selectedFace = response.getText(OUTPUT_FACE_INPUT);
        context.data().integer(OUTPUT_POWER_KEY, selectedPower == null ? outputPower(context.data()) : Math.clamp(Math.round(selectedPower), 0, 15));
        context.data().string(OUTPUT_FACE_KEY, validFace(selectedFace).name());
        BlockDataManager.getInstance().save(block, context);
        GameListener.queueRedstoneUpdate(block);
        WATCHING.remove(player.getUniqueId(), locationKey);
        BlockEngineChat.send(player, BlockEngineChat.row("redstone dialog", "saved"));
    }

    private static @NotNull BlockFace validFace(String face) {
        if (face == null) {
            return BlockFace.NORTH;
        }
        return OUTPUT_FACES.stream()
                .filter(candidate -> candidate.name().equals(face))
                .findFirst()
                .orElse(BlockFace.NORTH);
    }

    private static Block block(@NotNull String locationKey) {
        String[] parts = locationKey.split(":");
        if (parts.length != 4) {
            return null;
        }
        try {
            World world = Bukkit.getWorld(UUID.fromString(parts[0]));
            if (world == null) {
                return null;
            }
            return world.getBlockAt(
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
