package dev.auto.blockengine.commands;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class DebugCommands implements BasicCommand {
    private static final List<String> ROOT_SUBCOMMANDS = List.of(
            "spawn",
            "redblock",
            "pack"
    );
    private static final List<String> EXAMPLE_ALIASES = List.of(
            "spawn"
    );

    private final Main plugin;

    public DebugCommands(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        var sender = source.getSender();

        if (!sender.hasPermission("blockengine.debug")) {
            sender.sendMessage("You don't have permission to use this command!");
            return;
        }

        if (args.length == 0) {
            sender.sendMessage("Usage: /debug <subcommand>");
            sender.sendMessage("Subcommands: " + String.join(", ", ROOT_SUBCOMMANDS));
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "pack" -> pack(sender, args);
            default -> sender.sendMessage("Unknown subcommand. Try: " + String.join(", ", ROOT_SUBCOMMANDS));
        }
    }

    @Override
    public @Nullable String permission() {
        return "blockengine.debug";
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        var sender = source.getSender();
        if (!sender.hasPermission("blockengine.debug")) {
            return List.of();
        }

        if (args.length == 0) {
            return ROOT_SUBCOMMANDS;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return ROOT_SUBCOMMANDS.stream()
                    .filter(subcommand -> subcommand.startsWith(first))
                    .toList();
        }

        if (hasAlias(first, EXAMPLE_ALIASES) && args.length == 2) {
            String materialPrefix = args[1].toLowerCase(Locale.ROOT);
            return Arrays.stream(Material.values())
                    .filter(Material::isItem)
                    .map(material -> material.name().toLowerCase(Locale.ROOT))
                    .filter(name -> name.startsWith(materialPrefix))
                    .toList();
        }
        if (first.equals("pack") && args.length == 2) {
            return List.of("reload").stream()
                    .filter(option -> option.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    private static boolean hasAlias(String input, List<String> aliases) {
        return aliases.contains(input.toLowerCase(Locale.ROOT));
    }

    private static void pack(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
            ResourcePackManager.getInstance().reload();
            sender.sendMessage("Regenerated and reloaded BlockEngine resource pack.");
            return;
        }

        if (sender instanceof Player player) {
            ResourcePackManager.getInstance().send(player);
            sender.sendMessage("Sent BlockEngine resource pack.");
            return;
        }

        sender.sendMessage("Use /debug pack reload from console, or run /debug pack as a player.");
    }

}



