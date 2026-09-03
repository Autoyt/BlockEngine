package dev.auto.blockengine.commands;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.chat.BlockEngineChat;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import dev.auto.blockengine.structure.SudoBlockManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class BlockEngineCommand implements BasicCommand {
    private static final List<String> ROOT = List.of(
            "info", "catalog", "packs", "debug", "wand"
    );
    private static final String DOCS_URL = "https://autoyt.github.io/BlockEngine/";
    private static final String HOW_IT_WORKS_URL = DOCS_URL + "concepts/how-it-works/";
    private static final String DATA_PACK_GUIDE_URL = DOCS_URL + "guides/create-a-data-pack/";
    private static final String JAVA_API_GUIDE_URL = DOCS_URL + "guides/register-blocks-from-java/";
    private static final String JAVADOC_URL = DOCS_URL + "api/";
    private static final String GITHUB_URL = "https://github.com/Autoyt/BlockEngine";
    private static final String RELEASES_URL = GITHUB_URL + "/releases";

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
        if (args[0].equalsIgnoreCase("catalog") || args[0].equalsIgnoreCase("packs")) {
            debug.execute(source, args);
            return;
        }
        if (args[0].equalsIgnoreCase("debug")) {
            debug.execute(source, Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args[0].equalsIgnoreCase("wand")) {
            giveWand(sender);
            return;
        }
        usage(sender);
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
        if (args[0].equalsIgnoreCase("catalog") || args[0].equalsIgnoreCase("packs")) {
            return debug.suggest(source, args);
        }
        return List.of();
    }

    private void usage(@NotNull CommandSender sender) {
        BlockEngineChat.send(sender, BlockEngineChat.header("blockengine"));
        BlockEngineChat.send(sender, BlockEngineChat.row("usage", "/blockengine <info|catalog|packs|debug|wand>"));
        BlockEngineChat.send(sender, BlockEngineChat.row("short", "/be <info|catalog|packs|debug|wand>"));
    }

    private void giveWand(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            BlockEngineChat.error(sender, "Only players can receive the Block Engine Wand.");
            return;
        }
        if (!sender.hasPermission(SudoBlockManager.PERMISSION)) {
            BlockEngineChat.error(sender, "You don't have permission to use BlockEngine structure tools.");
            return;
        }

        ItemStack stack = ItemManager.createWand();
        player.getInventory().addItem(stack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        BlockEngineChat.success(sender, "Gave Block Engine Wand.");
    }

    private void info(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            BlockEngineChat.send(sender, BlockEngineChat.header("blockengine"));
            BlockEngineChat.send(sender, BlockEngineChat.row("version", plugin.getPluginMeta().getVersion()));
            BlockEngineChat.send(sender, BlockEngineChat.row("blocks", BlockRegistry.getBlocks().size()));
            BlockEngineChat.send(sender, BlockEngineChat.row("namespaces", NamespaceRegistry.loaded().size()));
            BlockEngineChat.send(sender, BlockEngineChat.row("resource packs", ResourcePackManager.getInstance().packIds().size()));
            BlockEngineChat.send(sender, BlockEngineChat.row("docs", link("open documentation", DOCS_URL)));
            BlockEngineChat.send(sender, BlockEngineChat.row("github", link("source and issues", GITHUB_URL)));
            BlockEngineChat.send(sender, BlockEngineChat.row("releases", link("download builds", RELEASES_URL)));
            return;
        }
        player.showDialog(infoDialog());
    }

    private @NotNull Dialog infoDialog() {
        Component title = Component.text("BlockEngine", BlockEngineChat.ORANGE).decorate(TextDecoration.BOLD);
        Component summary = Component.text()
                .append(Component.text("✦ ", BlockEngineChat.ORANGE_LIGHT))
                .append(Component.text("Custom blocks that behave on the server and render through generated resource packs.", BlockEngineChat.WHITE))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Created by ", BlockEngineChat.GRAY))
                .append(Component.text("AutoYT", BlockEngineChat.ORANGE_LIGHT).decorate(TextDecoration.BOLD))
                .append(Component.text(" with API, data-pack, creative inventory, display, persistence, and resource-pack systems in one runtime.", BlockEngineChat.GRAY))
                .append(Component.newline())
                .append(Component.newline())
                .append(section("Runtime"))
                .append(stat("Version", plugin.getPluginMeta().getVersion()))
                .append(stat("Registered blocks", BlockRegistry.getBlocks().size()))
                .append(stat("Loaded namespaces", NamespaceRegistry.loaded().size()))
                .append(stat("Generated resource packs", ResourcePackManager.getInstance().packIds().size()))
                .append(Component.newline())
                .append(section("Start Here"))
                .append(linkLine("Documentation", DOCS_URL, "Browse the BlockEngine docs"))
                .append(linkLine("How it works", HOW_IT_WORKS_URL, "Understand the custom block runtime"))
                .append(linkLine("Data-driven packs", DATA_PACK_GUIDE_URL, "Create blocks with JSON and assets"))
                .append(linkLine("Java integrations", JAVA_API_GUIDE_URL, "Register blocks from another plugin"))
                .append(linkLine("Java API docs", JAVADOC_URL, "Open generated Javadocs"))
                .append(Component.newline())
                .append(section("Project"))
                .append(linkLine("GitHub repository", GITHUB_URL, "Open source, issues, and workflows"))
                .append(linkLine("Releases", RELEASES_URL, "Download published plugin builds"))
                .append(Component.newline())
                .append(section("Commands"))
                .append(command("/be catalog", "Browse registered custom blocks"))
                .append(command("/be packs", "Inspect loaded data packs"))
                .append(command("/be wand", "Receive the structure-building wand"))
                .append(command("/be debug", "Open diagnostics and maintenance tools"))
                .build();

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .externalTitle(Component.text("BlockEngine", BlockEngineChat.ORANGE))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .body(List.of(
                                DialogBody.plainMessage(summary, 360)
                        ))
                        .build())
                .type(DialogType.notice()));
    }

    private static @NotNull Component section(@NotNull String label) {
        return Component.text("▟▙ ", BlockEngineChat.ORANGE)
                .append(Component.text(label, BlockEngineChat.ORANGE_LIGHT).decorate(TextDecoration.BOLD))
                .append(Component.newline());
    }

    private static @NotNull Component stat(@NotNull String label, @NotNull Object value) {
        return Component.text("  • ", BlockEngineChat.DARK_GRAY)
                .append(Component.text(label + ": ", BlockEngineChat.GRAY))
                .append(Component.text(String.valueOf(value), BlockEngineChat.SUCCESS))
                .append(Component.newline());
    }

    private static @NotNull Component command(@NotNull String command, @NotNull String description) {
        return Component.text("  • ", BlockEngineChat.DARK_GRAY)
                .append(Component.text(command, BlockEngineChat.ORANGE_LIGHT)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand(command))
                        .hoverEvent(HoverEvent.showText(Component.text(description, BlockEngineChat.ORANGE_LIGHT))))
                .append(Component.text(" — " + description, BlockEngineChat.GRAY))
                .append(Component.newline());
    }

    private static @NotNull Component linkLine(
            @NotNull String label,
            @NotNull String url,
            @NotNull String hover
    ) {
        return Component.text("  • ", BlockEngineChat.DARK_GRAY)
                .append(link(label, url).hoverEvent(HoverEvent.showText(Component.text(hover, BlockEngineChat.ORANGE_LIGHT))))
                .append(Component.newline());
    }

    private static @NotNull Component link(@NotNull String label, @NotNull String url) {
        return Component.text(label, BlockEngineChat.ORANGE_LIGHT)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text("Open " + url, BlockEngineChat.ORANGE_LIGHT)));
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
