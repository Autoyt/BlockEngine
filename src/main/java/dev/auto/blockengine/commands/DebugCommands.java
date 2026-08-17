package dev.auto.blockengine.commands;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.defaultadapters.DebugBlocks;
import dev.auto.blockengine.entity.BlockEngineBlockOrchestrator;
import dev.auto.blockengine.items.BlockEngineItemManager;
import dev.auto.blockengine.mining.DebugBreakAnimationService;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
            "pack",
            "breakstage"
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
            sender.sendMessage("Usage: /blockenginedebug <subcommand>");
            sender.sendMessage("Subcommands: " + String.join(", ", ROOT_SUBCOMMANDS));
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "pack" -> pack(sender, args);
            case "breakstage", "breakamount", "crack" -> breakStage(sender, args);
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
        if ((first.equals("breakstage") || first.equals("breakamount") || first.equals("crack")) && args.length == 2) {
            return List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9").stream()
                    .filter(stage -> stage.startsWith(args[1]))
                    .toList();
        }

        return List.of();
    }

    private static boolean hasAlias(String input, List<String> aliases) {
        return aliases.contains(input.toLowerCase(Locale.ROOT));
    }

    private static void pack(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
            ResourcePackManager.reload();
            sender.sendMessage("Regenerated and reloaded BlockEngine resource pack.");
            return;
        }

        if (sender instanceof Player player) {
            ResourcePackManager.send(player);
            sender.sendMessage("Sent BlockEngine resource pack.");
            return;
        }

        sender.sendMessage("Use /blockenginedebug pack reload from console, or run /blockenginedebug pack as a player.");
    }

    private static void breakStage(org.bukkit.command.CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can debug block break animation targets.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /blockenginedebug breakstage <0-9>");
            return;
        }

        int stage;
        try {
            stage = Integer.parseInt(args[1]);
        } catch (NumberFormatException ignored) {
            sender.sendMessage("Break stage must be a number from 0 to 9.");
            return;
        }
        if (stage < 0 || stage > 9) {
            sender.sendMessage("Break stage must be from 0 to 9.");
            return;
        }

        Block target = player.getTargetBlockExact(8, FluidCollisionMode.NEVER);
        if (target == null) {
            sender.sendMessage("Look at a block within 8 blocks first.");
            return;
        }

        DebugBreakAnimationService.show(player, target, (byte) stage);
        sender.sendMessage("Sending break stage " + stage + " to "
                + target.getX() + " " + target.getY() + " " + target.getZ()
                + " for 1 second.");
    }
}
