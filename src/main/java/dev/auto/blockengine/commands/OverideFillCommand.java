package dev.auto.blockengine.commands;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.chat.BlockEngineChat;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.placement.PlacementManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.BlockRemover;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public final class OverideFillCommand implements Listener {
    private static final int MAX_BLOCKS = 32768;

    public OverideFillCommand() {
        Main.getInstance().getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(@NotNull PlayerCommandPreprocessEvent event) {
        if (handle(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(@NotNull ServerCommandEvent event) {
        if (handle(event.getSender(), event.getCommand())) {
            event.setCancelled(true);
        }
    }

    private boolean handle(@NotNull CommandSender sender, @NotNull String rawCommand) {
        ParsedCommand command = ParsedCommand.parse(rawCommand);
        if (command == null) {
            return false;
        }

        return switch (command.name()) {
            case "fill" -> {
                Source source = Source.from(sender);
                if (source == null) {
                    BlockEngineChat.send(sender, "BlockEngine full block commands need a world-bound source.");
                    yield true;
                }
                yield fill(sender, source, command.args());
            }
            case "setblock" -> {
                Source source = Source.from(sender);
                if (source == null) {
                    BlockEngineChat.send(sender, "BlockEngine full block commands need a world-bound source.");
                    yield true;
                }
                yield setBlock(sender, source, command.args());
            }
            case "give" -> give(sender, command.args());
            default -> false;
        };
    }

    private boolean give(@NotNull CommandSender sender, String @NotNull [] args) {
        if (args.length < 2) {
            return false;
        }

        Target target = Target.parse(args[1]);
        if (target == null) {
            return false;
        }

        BlockDefinition definition = BlockRegistry.getBlock(target.blockId());
        if (definition == null) {
            return false;
        }

        if (!sender.hasPermission("minecraft.command.give")) {
            BlockEngineChat.send(sender, "You do not have permission to use /give.");
            return true;
        }

        String stateId = target.stateId() == null ? definition.apiDefinition().defaultState() : target.stateId();
        try {
            definition.apiDefinition().state(stateId);
        } catch (IllegalArgumentException exception) {
            BlockEngineChat.send(sender, "Unknown BlockEngine block state: " + stateId + ".");
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.clamp(Integer.parseInt(args[2]), 1, 6400);
            } catch (NumberFormatException ignored) {
                BlockEngineChat.send(sender, "BlockEngine full block give amount must be a number.");
                return true;
            }
        }

        Collection<Player> players = players(sender, args[0]);
        if (players.isEmpty()) {
            BlockEngineChat.send(sender, "No players matched selector '" + args[0] + "'.");
            return true;
        }

        int given = 0;
        for (Player player : players) {
            give(player, definition, stateId, amount);
            given++;
        }

        BlockEngineChat.send(sender, "Gave " + amount + " " + definition.id() + " to " + given + " player(s).");
        return true;
    }

    private boolean fill(@NotNull CommandSender sender, @NotNull Source source, String @NotNull [] args) {
        if (args.length < 7) {
            return false;
        }

        Target target = Target.parse(args[6]);
        if (target == null) {
            return false;
        }

        BlockDefinition definition = BlockRegistry.getBlock(target.blockId());
        if (definition == null) {
            return false;
        }

        if (!sender.hasPermission("minecraft.command.fill")) {
            BlockEngineChat.send(sender, "You do not have permission to use /fill.");
            return true;
        }

        Position from = Position.parse(args[0], args[1], args[2], source.location());
        Position to = Position.parse(args[3], args[4], args[5], source.location());
        if (from == null || to == null) {
            BlockEngineChat.send(sender, "BlockEngine only supports absolute and ~ relative block coordinates for full block fill.");
            return true;
        }

        String mode = args.length >= 8 ? args[7].toLowerCase(Locale.ROOT) : "replace";
        if (!mode.equals("replace") && !mode.equals("destroy") && !mode.equals("keep")) {
            BlockEngineChat.send(sender, "BlockEngine full block fill currently supports replace, destroy, and keep.");
            return true;
        }

        int minX = Math.min(from.x(), to.x());
        int minY = Math.min(from.y(), to.y());
        int minZ = Math.min(from.z(), to.z());
        int maxX = Math.max(from.x(), to.x());
        int maxY = Math.max(from.y(), to.y());
        int maxZ = Math.max(from.z(), to.z());
        long count = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (count > MAX_BLOCKS) {
            BlockEngineChat.send(sender, "Too many BlockEngine blocks in fill area: " + count + " > " + MAX_BLOCKS + ".");
            return true;
        }

        int changed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = source.world().getBlockAt(x, y, z);
                    if (mode.equals("keep") && !block.getType().isAir()) {
                        continue;
                    }
                    if (!place(block, definition, source.player(), target.stateId(), mode.equals("destroy"), mode.equals("destroy"))) {
                        continue;
                    }
                    changed++;
                }
            }
        }

        BlockEngineChat.send(sender, "Filled " + changed + " BlockEngine block(s).");
        return true;
    }

    private boolean setBlock(@NotNull CommandSender sender, @NotNull Source source, String @NotNull [] args) {
        if (args.length < 4) {
            return false;
        }

        Target target = Target.parse(args[3]);
        if (target == null) {
            return false;
        }

        BlockDefinition definition = BlockRegistry.getBlock(target.blockId());
        if (definition == null) {
            return false;
        }

        if (!sender.hasPermission("minecraft.command.setblock")) {
            BlockEngineChat.send(sender, "You do not have permission to use /setblock.");
            return true;
        }

        Position position = Position.parse(args[0], args[1], args[2], source.location());
        if (position == null) {
            BlockEngineChat.send(sender, "BlockEngine only supports absolute and ~ relative block coordinates for full block setblock.");
            return true;
        }

        String mode = args.length >= 5 ? args[4].toLowerCase(Locale.ROOT) : "replace";
        if (!mode.equals("replace") && !mode.equals("destroy") && !mode.equals("keep")) {
            BlockEngineChat.send(sender, "BlockEngine full block setblock currently supports replace, destroy, and keep.");
            return true;
        }

        Block block = source.world().getBlockAt(position.x(), position.y(), position.z());
        if (mode.equals("keep") && !block.getType().isAir()) {
            BlockEngineChat.send(sender, "No block was changed.");
            return true;
        }

        boolean changed = place(block, definition, source.player(), target.stateId(), true, mode.equals("destroy"));
        BlockEngineChat.send(sender, changed ? "Set BlockEngine block." : "No block was changed.");
        return true;
    }

    private boolean place(
            @NotNull Block block,
            @NotNull BlockDefinition definition,
            @Nullable Player player,
            @Nullable String stateId,
            boolean removeExisting,
            boolean dropExisting
    ) {
        if (removeExisting && ChunkEngine.getBlock(new BlockLocationKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        )) != null && !BlockRemover.remove(block, dropExisting)) {
            return false;
        }

        return PlacementManager.getInstance().place(block, definition, player, null, stateId);
    }

    private record ParsedCommand(@NotNull String name, String @NotNull [] args) {
        private static @Nullable ParsedCommand parse(@NotNull String rawCommand) {
            String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
            String[] split = command.trim().split("\\s+");
            if (split.length == 0 || split[0].isBlank()) {
                return null;
            }

            String name = split[0].toLowerCase(Locale.ROOT);
            int namespaceSeparator = name.indexOf(':');
            if (namespaceSeparator != -1) {
                name = name.substring(namespaceSeparator + 1);
            }
            if (!name.equals("fill") && !name.equals("setblock") && !name.equals("give")) {
                return null;
            }

            String[] args = new String[Math.max(0, split.length - 1)];
            System.arraycopy(split, 1, args, 0, args.length);
            return new ParsedCommand(name, args);
        }
    }

    private static void give(
            @NotNull Player player,
            @NotNull BlockDefinition definition,
            @NotNull String stateId,
            int amount
    ) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = ItemManager.create(definition, stateId);
            stack.setAmount(Math.min(64, remaining));
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= stack.getAmount();
        }
    }

    private static @NotNull Collection<Player> players(@NotNull CommandSender sender, @NotNull String selector) {
        try {
            Collection<Player> players = new ArrayList<>();
            for (Entity entity : Bukkit.selectEntities(sender, selector)) {
                if (entity instanceof Player player) {
                    players.add(player);
                }
            }
            if (!players.isEmpty() || selector.startsWith("@")) {
                return players;
            }
        } catch (IllegalArgumentException ignored) {
            if (selector.startsWith("@")) {
                return java.util.List.of();
            }
        }

        Player exact = Bukkit.getPlayerExact(selector);
        if (exact != null) {
            return java.util.List.of(exact);
        }
        Player partial = Bukkit.getPlayer(selector);
        return partial == null ? java.util.List.of() : java.util.List.of(partial);
    }

    private record Target(@NotNull String blockId, @Nullable String stateId) {
        private static @Nullable Target parse(@NotNull String token) {
            int dataStart = firstDataIndex(token);
            String blockId = dataStart == -1 ? token : token.substring(0, dataStart);
            if (!blockId.contains(":")) {
                return null;
            }
            return new Target(blockId, state(token, dataStart));
        }

        private static int firstDataIndex(@NotNull String token) {
            int bracket = token.indexOf('[');
            int brace = token.indexOf('{');
            if (bracket == -1) {
                return brace;
            }
            if (brace == -1) {
                return bracket;
            }
            return Math.min(bracket, brace);
        }

        private static @Nullable String state(@NotNull String token, int dataStart) {
            if (dataStart == -1 || token.charAt(dataStart) != '[' || !token.endsWith("]")) {
                return null;
            }

            String data = token.substring(dataStart + 1, token.length() - 1).trim();
            if (data.isBlank()) {
                return null;
            }
            if (data.startsWith("state=")) {
                return data.substring("state=".length());
            }
            return data.contains("=") || data.contains(",") ? null : data;
        }
    }

    private record Position(int x, int y, int z) {
        private static @Nullable Position parse(
                @NotNull String x,
                @NotNull String y,
                @NotNull String z,
                @NotNull Location source
        ) {
            Integer parsedX = coordinate(x, source.getBlockX());
            Integer parsedY = coordinate(y, source.getBlockY());
            Integer parsedZ = coordinate(z, source.getBlockZ());
            if (parsedX == null || parsedY == null || parsedZ == null) {
                return null;
            }
            return new Position(parsedX, parsedY, parsedZ);
        }

        private static @Nullable Integer coordinate(@NotNull String token, int source) {
            try {
                if (token.startsWith("^")) {
                    return null;
                }
                if (token.equals("~")) {
                    return source;
                }
                if (token.startsWith("~")) {
                    return source + (int) Math.floor(Double.parseDouble(token.substring(1)));
                }
                return (int) Math.floor(Double.parseDouble(token));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private record Source(@NotNull World world, @NotNull Location location, @Nullable Player player) {
        private static @Nullable Source from(@NotNull CommandSender sender) {
            if (sender instanceof Player player) {
                return new Source(player.getWorld(), player.getLocation(), player);
            }
            if (sender instanceof Entity entity) {
                return new Source(entity.getWorld(), entity.getLocation(), null);
            }
            if (sender instanceof BlockCommandSender blockSender) {
                Block block = blockSender.getBlock();
                return new Source(block.getWorld(), block.getLocation(), null);
            }
            return null;
        }
    }
}



