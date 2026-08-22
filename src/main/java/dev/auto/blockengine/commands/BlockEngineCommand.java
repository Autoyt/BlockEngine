package dev.auto.blockengine.commands;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class BlockEngineCommand implements BasicCommand {
    private static final TextColor ORANGE = TextColor.color(0xff9f2e);
    private static final TextColor ORANGE_LIGHT = TextColor.color(0xffc46b);
    private static final TextColor GRAY = TextColor.color(0xa8a8a8);
    private static final TextColor WHITE = TextColor.color(0xf7f7f7);
    private static final TextColor SUCCESS = TextColor.color(0x72d66b);
    private static final List<String> ROOT = List.of(
            "info", "debug", "packs", "perf", "blocks", "plugins", "give", "catalog", "chunks", "validate", "events", "reload"
    );

    private final Main plugin;
    private final DebugCommands debug;

    public BlockEngineCommand(@NotNull Main plugin, @NotNull DebugCommands debug) {
        this.plugin = plugin;
        this.debug = debug;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            info(sender);
            return;
        }
        if (args[0].equalsIgnoreCase("debug")) {
            debug.execute(source, Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        debug.execute(source, args);
    }

    @Override
    public @Nullable String permission() {
        return null;
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length == 0) {
            return ROOT;
        }
        if (args.length == 1) {
            return matching(ROOT, args[0]);
        }
        if (args[0].equalsIgnoreCase("debug")) {
            return debug.suggest(source, Arrays.copyOfRange(args, 1, args.length));
        }
        return debug.suggest(source, args);
    }

    private void info(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(header("blockengine"));
            sender.sendMessage(row("version", plugin.getPluginMeta().getVersion()));
            sender.sendMessage(row("blocks", BlockRegistry.getBlocks().size()));
            sender.sendMessage(row("namespaces", NamespaceRegistry.loaded().size()));
            sender.sendMessage(row("resource packs", ResourcePackManager.getInstance().packIds().size()));
            return;
        }
        player.showDialog(infoDialog());
    }

    private @NotNull Dialog infoDialog() {
        Component title = Component.text("BlockEngine", ORANGE).decorate(TextDecoration.BOLD);
        Component summary = Component.text()
                .append(Component.text("Custom block runtime by ", GRAY))
                .append(Component.text("AutoYT", ORANGE_LIGHT).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Version: ", GRAY))
                .append(Component.text(plugin.getPluginMeta().getVersion(), WHITE))
                .append(Component.newline())
                .append(Component.text("Registered blocks: ", GRAY))
                .append(Component.text(BlockRegistry.getBlocks().size(), SUCCESS))
                .append(Component.newline())
                .append(Component.text("Namespaces: ", GRAY))
                .append(Component.text(NamespaceRegistry.loaded().size(), SUCCESS))
                .append(Component.newline())
                .append(Component.text("Resource packs: ", GRAY))
                .append(Component.text(ResourcePackManager.getInstance().packIds().size(), SUCCESS))
                .build();

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .externalTitle(Component.text("BlockEngine", ORANGE))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .body(List.of(
                                DialogBody.item(infoItem())
                                        .description(DialogBody.plainMessage(Component.text("AutoYT / BlockEngine", ORANGE_LIGHT), 180))
                                        .showDecorations(true)
                                        .showTooltip(true)
                                        .width(64)
                                        .height(64)
                                        .build(),
                                DialogBody.plainMessage(summary, 320)
                        ))
                        .build())
                .type(DialogType.notice()));
    }

    private @NotNull ItemStack infoItem() {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.itemName(Component.text("BlockEngine", ORANGE).decorate(TextDecoration.BOLD));
            meta.displayName(Component.text("AutoYT", ORANGE_LIGHT).decorate(TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Custom block runtime", GRAY),
                    Component.text("Version " + plugin.getPluginMeta().getVersion(), WHITE),
                    Component.text(BlockRegistry.getBlocks().size() + " registered blocks", SUCCESS)
            ));
            meta.setItemModel(new NamespacedKey(plugin, "info_card"));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static @NotNull Component header(@NotNull String title) {
        return Component.text("----", GRAY)
                .append(Component.text(title.toUpperCase(Locale.ROOT), ORANGE).decorate(TextDecoration.BOLD))
                .append(Component.text("----", GRAY));
    }

    private static @NotNull Component row(@NotNull String label, @NotNull Object value) {
        return Component.text("  ▟", GRAY)
                .append(Component.text("▙ ", ORANGE))
                .append(Component.text(label + ": ", GRAY))
                .append(Component.text(String.valueOf(value), WHITE));
    }

    private static @NotNull Collection<String> matching(@NotNull Collection<String> values, @NotNull String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(value);
            }
        }
        return matches;
    }
}
