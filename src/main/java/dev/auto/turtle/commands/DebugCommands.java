package dev.auto.turtle.commands;

import dev.auto.turtle.Main;
import dev.auto.turtle.entity.TurtleBlockOrchestrator;
import dev.auto.turtle.types.BlockLocationKey;
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
            "spawn"
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

        if (!sender.hasPermission("turtle.debug")) {
            sender.sendMessage("You don't have permission to use this command!");
            return;
        }

        if (args.length == 0) {
            sender.sendMessage("Usage: /turtledebug <subcommand>");
            sender.sendMessage("Subcommands: " + String.join(", ", ROOT_SUBCOMMANDS));
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "spawn" -> spawnDebugEntity(sender, args);
            default -> sender.sendMessage("Unknown subcommand. Try: " + String.join(", ", ROOT_SUBCOMMANDS));
        }
    }

    @Override
    public @Nullable String permission() {
        return "turtle.debug";
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        var sender = source.getSender();
        if (!sender.hasPermission("turtle.debug")) {
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

        return List.of();
    }

    private static boolean hasAlias(String input, List<String> aliases) {
        return aliases.contains(input.toLowerCase(Locale.ROOT));
    }

    private static void spawnDebugEntity(org.bukkit.command.CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this subcommand.");
            return;
        }

        Material material = Material.STONE;
        if (args.length >= 2) {
            material = Material.matchMaterial(args[1], true);
            if (material == null || !material.isItem()) {
                sender.sendMessage("Unknown item material: " + args[1]);
                return;
            }
        }

        Block targetBlock = player.getTargetBlockExact(6, FluidCollisionMode.NEVER);
        if (targetBlock == null) {
            sender.sendMessage("Look at a block within 6 blocks first.");
            return;
        }

        BlockLocationKey key = new BlockLocationKey(
                player.getWorld().getUID(),
                targetBlock.getX(),
                targetBlock.getY(),
                targetBlock.getZ()
        );

        TurtleBlockOrchestrator.addEntity(key, material);
        sender.sendMessage("Spawned a debug item display at " +
                targetBlock.getX() + ", " +
                targetBlock.getY() + ", " +
                targetBlock.getZ() + " using " + material.name().toLowerCase(Locale.ROOT) + ".");
    }
}
