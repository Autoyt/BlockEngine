package dev.auto.blockengine.commands;

import dev.auto.blockengine.Main;
import dev.auto.blockengine.api.event.BlockEngineBlockBreakEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockDataSaveEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockDataSavedEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockPlacedEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockPlaceEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockRemovedEvent;
import dev.auto.blockengine.api.event.BlockEngineChunkSaveEvent;
import dev.auto.blockengine.api.event.BlockEngineChunkSavedEvent;
import dev.auto.blockengine.catalog.CatalogListeners;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

public final class DebugCommands implements BasicCommand, Listener {
    private static final List<String> ROOT_SUBCOMMANDS = List.of(
            "info", "pack", "profile", "block", "plugin", "chunk", "validate", "events", "visibility", "displays"
    );
    private static final List<String> PROFILE_TARGETS = List.of(
            "placement", "validation", "chunk-save", "flush", "events", "visibility", "displays", "commands"
    );
    private static final int MAX_EVENT_TAIL = 60;

    private final Main plugin;
    private final Map<UUID, LiveProfile> liveProfiles = new HashMap<>();
    private final Map<String, Long> lastEventNanos = new HashMap<>();
    private final TimingRegistry timings = new TimingRegistry();
    private final Queue<String> eventTail = new ArrayDeque<>();
    private boolean eventTailEnabled;

    public DebugCommands(@NotNull Main plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission("blockengine.debug")) {
            sender.sendMessage("You don't have permission to use this command!");
            return;
        }
        if (args.length == 0) {
            usage(sender);
            return;
        }

        long started = System.nanoTime();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> info(sender);
            case "pack" -> pack(sender, args);
            case "profile" -> profile(sender, args);
            case "block" -> block(sender, args);
            case "plugin" -> plugin(sender, args);
            case "chunk" -> chunk(sender, args);
            case "validate" -> validate(sender, args);
            case "events" -> events(sender, args);
            case "visibility" -> sender.sendMessage("Visibility debug commands are planned: player, chunk, refresh.");
            case "displays" -> sender.sendMessage("Display debug commands are planned: nearby, attached, cleanup.");
            default -> usage(sender);
        }
        timings.record("commands", System.nanoTime() - started);
    }

    @Override
    public @Nullable String permission() {
        return "blockengine.debug";
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission("blockengine.debug")) {
            return List.of();
        }
        if (args.length == 0) {
            return ROOT_SUBCOMMANDS;
        }
        if (args.length == 1) {
            return matching(ROOT_SUBCOMMANDS, args[0]);
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pack" -> suggestPack(args);
            case "profile" -> suggestProfile(args);
            case "block" -> suggestBlock(args);
            case "plugin" -> suggestPlugin(args);
            case "chunk" -> suggest(args, 1, List.of("here", "flush", "pending"));
            case "validate" -> suggest(args, 1, List.of("looking", "here"));
            case "events" -> suggest(args, 1, List.of("on", "off", "tail", "clear"));
            case "visibility" -> suggest(args, 1, List.of("player", "chunk", "refresh"));
            case "displays" -> suggest(args, 1, List.of("nearby", "attached", "cleanup"));
            default -> List.of();
        };
    }

    private void usage(@NotNull CommandSender sender) {
        sender.sendMessage("Usage: /debug <subcommand>");
        sender.sendMessage("Subcommands: " + String.join(", ", ROOT_SUBCOMMANDS));
    }

    private void info(@NotNull CommandSender sender) {
        sender.sendMessage("BlockEngine debug");
        sender.sendMessage("Blocks: " + BlockRegistry.getBlocks().size());
        sender.sendMessage("Namespaces: " + String.join(", ", namespaces()));
        sender.sendMessage("Resource packs: " + String.join(", ", ResourcePackManager.getInstance().packIds()));
    }

    private void pack(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /debug pack <reload|resend|list>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("reload")) {
            ResourcePackManager.getInstance().reload();
            sender.sendMessage("Regenerated and reloaded BlockEngine resource packs.");
            return;
        }
        if (action.equals("list")) {
            sender.sendMessage("Resource packs: " + String.join(", ", ResourcePackManager.getInstance().packIds()));
            return;
        }
        if (!action.equals("resend")) {
            sender.sendMessage("Usage: /debug pack <reload|resend|list>");
            return;
        }

        String packId = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "blockengine";
        String target = args.length >= 4 ? args[3] : sender instanceof Player player ? player.getName() : "*";
        List<Player> targets = players(target);
        if (targets.isEmpty()) {
            sender.sendMessage("No online player matched '" + target + "'.");
            return;
        }

        int packSends = 0;
        if (packId.equals("*")) {
            for (Player player : targets) {
                packSends += ResourcePackManager.getInstance().sendAll(player);
            }
        } else if (!ResourcePackManager.getInstance().packIds().contains(packId)) {
            sender.sendMessage("Unknown pack '" + packId + "'. Try: "
                    + String.join(", ", ResourcePackManager.getInstance().packIds()) + ", *");
            return;
        } else {
            for (Player player : targets) {
                if (ResourcePackManager.getInstance().send(player, packId)) {
                    packSends++;
                }
            }
        }
        sender.sendMessage("Resent " + packSends + " pack(s) to " + targets.size() + " player(s).");
    }

    private void profile(@NotNull CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
            if (sender instanceof Player player) {
                stopLive(player);
                sender.sendMessage("Stopped live BlockEngine profile view.");
                return;
            }
            sender.sendMessage("Only players can stop a bossbar profile view.");
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("live")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can start a bossbar profile view.");
                return;
            }
            String target = args[2].toLowerCase(Locale.ROOT);
            if (!PROFILE_TARGETS.contains(target)) {
                sender.sendMessage("Unknown profile target. Try: " + String.join(", ", PROFILE_TARGETS));
                return;
            }
            startLive(player, target);
            sender.sendMessage("Started live BlockEngine profile view for " + target + ".");
            return;
        }

        String target = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "events";
        TimingSnapshot snapshot = timings.snapshot(target);
        sender.sendMessage("BlockEngine profile: " + target);
        sender.sendMessage("PROCESS avg=" + ms(snapshot.avgNanos()) + "ms p95=" + ms(snapshot.p95Nanos())
                + "ms max=" + ms(snapshot.maxNanos()) + "ms samples=" + snapshot.samples());
        sender.sendMessage("Speed: " + ops(snapshot) + " ops/s");
        sender.sendMessage("VALIDATION is sampled by /debug validate and live validation hooks.");
    }

    private void block(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /debug block <looking|list|give>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> blockList(sender, args.length >= 3 && !args[2].equals("*") ? args[2] : null);
            case "give" -> blockGive(sender, args);
            case "looking" -> blockLooking(sender);
            default -> sender.sendMessage("Usage: /debug block <looking|list|give>");
        }
    }

    private void blockList(@NotNull CommandSender sender, @Nullable String namespace) {
        List<BlockDefinition> blocks = blocks(namespace);
        if (blocks.isEmpty()) {
            sender.sendMessage(namespace == null ? "No registered blocks." : "No blocks for namespace '" + namespace + "'.");
            return;
        }
        sender.sendMessage("BlockEngine blocks:");
        for (BlockDefinition block : blocks) {
            Component line = Component.text(block.id(), NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/debug block give " + block.id()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to give yourself this block.")))
                    .append(Component.text(" [give]", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/debug block give " + block.id())))
                    .append(Component.text(" [catalog]", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/debug plugin catalog " + block.name().namespace())));
            sender.sendMessage(line);
        }
    }

    private void blockGive(@NotNull CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can receive BlockEngine block stacks.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /debug block give <blockId> [stateId]");
            return;
        }
        BlockDefinition block = BlockRegistry.getBlock(args[2]);
        if (block == null) {
            sender.sendMessage("Unknown block '" + args[2] + "'.");
            return;
        }
        String stateId = args.length >= 4 ? args[3] : block.apiDefinition().defaultState();
        if (!block.apiDefinition().states().containsKey(stateId)) {
            sender.sendMessage("Unknown state '" + stateId + "'.");
            return;
        }
        ItemStack stack = ItemManager.create(block, stateId);
        stack.setAmount(64);
        player.getInventory().addItem(stack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        sender.sendMessage("Gave 64x " + block.id() + " [" + stateId + "].");
    }

    private void blockLooking(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can inspect a targeted block.");
            return;
        }
        org.bukkit.block.Block target = player.getTargetBlockExact(8);
        if (target == null) {
            sender.sendMessage("No block in range.");
            return;
        }
        RuntimeBlockView view = ChunkEngine.getBlock(new BlockLocationKey(
                target.getWorld().getUID(), target.getX(), target.getY(), target.getZ()
        ));
        if (view == null) {
            sender.sendMessage("Target is not a BlockEngine block. Type=" + target.getType());
            return;
        }
        sender.sendMessage("BlockEngine block: " + view.storedBlock().blockId());
        sender.sendMessage("State: " + view.storedBlock().stateId());
        sender.sendMessage("Location: " + target.getWorld().getName() + " "
                + target.getX() + " " + target.getY() + " " + target.getZ());
        sender.sendMessage("Backing type: " + target.getType());
        sender.sendMessage("Displays: " + view.storedBlock().displays().size());
    }

    private void plugin(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            sender.sendMessage("BlockEngine namespaces:");
            for (String namespace : namespaces()) {
                Component line = Component.text(namespace, NamedTextColor.AQUA)
                        .append(Component.text(" [catalog]", NamedTextColor.YELLOW)
                                .clickEvent(ClickEvent.runCommand("/debug plugin catalog " + namespace))
                                .hoverEvent(HoverEvent.showText(Component.text("Open only " + namespace + " blocks."))))
                        .append(Component.text(" [blocks]", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/debug block list " + namespace)));
                sender.sendMessage(line);
            }
            return;
        }
        if (args[1].equalsIgnoreCase("catalog")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can open the BlockEngine catalog.");
                return;
            }
            if (args.length < 3) {
                sender.sendMessage("Usage: /debug plugin catalog <namespace>");
                return;
            }
            CatalogListeners.open(player, args[2]);
            return;
        }
        if (args[1].equalsIgnoreCase("blocks")) {
            blockList(sender, args.length >= 3 ? args[2] : null);
            return;
        }
        sender.sendMessage("Usage: /debug plugin <list|catalog|blocks>");
    }

    private void chunk(@NotNull CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can inspect their current chunk.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("flush")) {
            ChunkEngine.flushNow();
            sender.sendMessage("Flushed pending BlockEngine chunk data.");
            return;
        }
        Chunk chunk = player.getLocation().getChunk();
        ChunkEngine.LoadedChunk loaded = ChunkEngine.get(ChunkEngine.Key.from(chunk));
        sender.sendMessage("Chunk " + chunk.getX() + "," + chunk.getZ());
        sender.sendMessage("Loaded: " + (loaded != null));
        sender.sendMessage("Stored blocks: " + (loaded == null ? 0 : loaded.blocks().size()));
        sender.sendMessage("Exposed blocks: " + (loaded == null ? 0 : loaded.exposedBlocks().size()));
    }

    private void validate(@NotNull CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can validate a block.");
            return;
        }
        org.bukkit.block.Block block = args.length >= 2 && args[1].equalsIgnoreCase("here")
                ? player.getLocation().getBlock()
                : player.getTargetBlockExact(8);
        if (block == null) {
            sender.sendMessage("No block to validate.");
            return;
        }
        long started = System.nanoTime();
        RuntimeBlockView view = ChunkEngine.getBlock(new BlockLocationKey(
                block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()
        ));
        timings.record("validation", System.nanoTime() - started);
        sender.sendMessage("Validation at " + block.getX() + " " + block.getY() + " " + block.getZ());
        sender.sendMessage("Real type: " + block.getType());
        sender.sendMessage("BlockEngine record: " + (view == null ? "none" : view.storedBlock().blockId()));
        sender.sendMessage("State: " + (view == null ? "none" : view.storedBlock().stateId()));
        sender.sendMessage("Exposed: " + (view != null && view.exposed()));
    }

    private void events(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("tail")) {
            if (eventTail.isEmpty()) {
                sender.sendMessage("No BlockEngine events captured.");
                return;
            }
            for (String event : eventTail) {
                sender.sendMessage(event);
            }
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                eventTailEnabled = true;
                sender.sendMessage("BlockEngine event tail enabled.");
            }
            case "off" -> {
                eventTailEnabled = false;
                sender.sendMessage("BlockEngine event tail disabled.");
            }
            case "clear" -> {
                eventTail.clear();
                sender.sendMessage("BlockEngine event tail cleared.");
            }
            default -> sender.sendMessage("Usage: /debug events <on|off|tail|clear>");
        }
    }

    private void startLive(@NotNull Player player, @NotNull String target) {
        stopLive(player);
        BossBar bar = Bukkit.createBossBar("BlockEngine " + target, BarColor.GREEN, BarStyle.SOLID);
        bar.addPlayer(player);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            TimingSnapshot snapshot = timings.snapshot(target);
            double avgMs = snapshot.avgNanos() / 1_000_000.0;
            bar.setColor(avgMs > 5.0 ? BarColor.RED : avgMs > 1.0 ? BarColor.YELLOW : BarColor.GREEN);
            bar.setProgress(Math.max(0.05, Math.min(1.0, avgMs / 10.0)));
            bar.setTitle("BlockEngine " + target
                    + " | avg " + ms(snapshot.avgNanos())
                    + "ms | p95 " + ms(snapshot.p95Nanos())
                    + "ms | max " + ms(snapshot.maxNanos())
                    + "ms | " + ops(snapshot) + "/s"
                    + " | n " + snapshot.samples());
        }, 1L, 20L);
        liveProfiles.put(player.getUniqueId(), new LiveProfile(bar, task));
    }

    private void stopLive(@NotNull Player player) {
        LiveProfile live = liveProfiles.remove(player.getUniqueId());
        if (live == null) {
            return;
        }
        live.task().cancel();
        live.bar().removeAll();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockPlace(BlockEngineBlockPlaceEvent event) {
        recordEvent("placement", "BlockEngineBlockPlaceEvent " + event.blockId() + " cancelled=" + event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlaced(BlockEngineBlockPlacedEvent event) {
        recordEvent("placement", "BlockEngineBlockPlacedEvent " + event.blockId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockBreak(BlockEngineBlockBreakEvent event) {
        recordEvent("placement", "BlockEngineBlockBreakEvent " + event.blockId() + " cancelled=" + event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemoved(BlockEngineBlockRemovedEvent event) {
        recordEvent("events", "BlockEngineBlockRemovedEvent " + event.blockId() + " reason=" + event.reason());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDataSave(BlockEngineBlockDataSaveEvent event) {
        recordEvent("events", "BlockEngineBlockDataSaveEvent " + event.blockId() + " cancelled=" + event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDataSaved(BlockEngineBlockDataSavedEvent event) {
        recordEvent("events", "BlockEngineBlockDataSavedEvent " + event.blockId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSave(BlockEngineChunkSaveEvent event) {
        recordEvent("chunk-save", "BlockEngineChunkSaveEvent chunk=" + event.chunk().getX() + "," + event.chunk().getZ()
                + " blocks=" + event.blockCount() + " displays=" + event.displayCount());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSaved(BlockEngineChunkSavedEvent event) {
        recordEvent("chunk-save", "BlockEngineChunkSavedEvent chunk=" + event.chunk().getX() + "," + event.chunk().getZ());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopLive(event.getPlayer());
    }

    private void recordEvent(@NotNull String target, @NotNull String text) {
        long now = System.nanoTime();
        Long previous = lastEventNanos.put(target, now);
        timings.record(target, previous == null ? 0L : now - previous);
        if (!eventTailEnabled) {
            return;
        }
        eventTail.add(text);
        while (eventTail.size() > MAX_EVENT_TAIL) {
            eventTail.poll();
        }
    }

    private @NotNull Collection<String> suggestPack(String[] args) {
        if (args.length == 2) {
            return matching(List.of("reload", "resend", "list"), args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("resend")) {
            List<String> packs = new ArrayList<>();
            packs.add("*");
            packs.addAll(ResourcePackManager.getInstance().packIds());
            return matching(packs, args[2]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("resend")) {
            return matching(playerTargets(), args[3]);
        }
        return List.of();
    }

    private @NotNull Collection<String> suggestProfile(String[] args) {
        if (args.length == 2) {
            List<String> options = new ArrayList<>(PROFILE_TARGETS);
            options.add("live");
            options.add("stop");
            return matching(options, args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("live")) {
            return matching(PROFILE_TARGETS, args[2]);
        }
        return List.of();
    }

    private @NotNull Collection<String> suggestBlock(String[] args) {
        if (args.length == 2) {
            return matching(List.of("looking", "list", "give"), args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("list")) {
            List<String> options = new ArrayList<>();
            options.add("*");
            options.addAll(namespaces());
            return matching(options, args[2]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("give")) {
            return matching(blockIds(), args[2]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("give")) {
            BlockDefinition block = BlockRegistry.getBlock(args[2]);
            return block == null ? List.of() : matching(block.apiDefinition().states().keySet(), args[3]);
        }
        return List.of();
    }

    private @NotNull Collection<String> suggestPlugin(String[] args) {
        if (args.length == 2) {
            return matching(List.of("list", "catalog", "blocks"), args[1]);
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("catalog") || args[1].equalsIgnoreCase("blocks"))) {
            return matching(namespaces(), args[2]);
        }
        return List.of();
    }

    private @NotNull Collection<String> suggest(String[] args, int index, Collection<String> values) {
        return args.length == index + 1 ? matching(values, args[index]) : List.of();
    }

    private @NotNull List<BlockDefinition> blocks(@Nullable String namespace) {
        return BlockRegistry.getBlocks().stream()
                .filter(block -> namespace == null || block.name().namespace().equalsIgnoreCase(namespace))
                .sorted(Comparator.comparing(BlockDefinition::id))
                .toList();
    }

    private @NotNull List<String> blockIds() {
        return BlockRegistry.getBlocks().stream().map(BlockDefinition::id).sorted().toList();
    }

    private @NotNull List<String> namespaces() {
        return NamespaceRegistry.loaded().stream().sorted().toList();
    }

    private @NotNull List<String> playerTargets() {
        List<String> targets = new ArrayList<>();
        targets.add("*");
        targets.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList());
        return targets;
    }

    private @NotNull List<Player> players(@NotNull String target) {
        if (target.equals("*")) {
            return List.copyOf(Bukkit.getOnlinePlayers());
        }
        Player player = Bukkit.getPlayerExact(target);
        return player == null ? List.of() : List.of(player);
    }

    private static @NotNull Collection<String> matching(@NotNull Collection<String> values, @NotNull String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    private static @NotNull String ms(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0);
    }

    private static @NotNull String ops(@NotNull TimingSnapshot snapshot) {
        if (snapshot.avgNanos() <= 0L) {
            return "0.00";
        }
        return String.format(Locale.ROOT, "%.2f", 1_000_000_000.0 / snapshot.avgNanos());
    }

    private record LiveProfile(@NotNull BossBar bar, @NotNull BukkitTask task) {
    }

    private static final class TimingRegistry {
        private static final int WINDOW = 120;
        private final Map<String, ArrayDeque<Long>> samples = new HashMap<>();

        private void record(@NotNull String target, long nanos) {
            ArrayDeque<Long> queue = samples.computeIfAbsent(target, ignored -> new ArrayDeque<>());
            queue.add(nanos);
            while (queue.size() > WINDOW) {
                queue.poll();
            }
        }

        private @NotNull TimingSnapshot snapshot(@NotNull String target) {
            List<Long> values = new ArrayList<>(samples.getOrDefault(target, new ArrayDeque<>()));
            if (values.isEmpty()) {
                return new TimingSnapshot(0, 0, 0, 0);
            }
            values.sort(Long::compareTo);
            long total = 0;
            long max = 0;
            for (long value : values) {
                total += value;
                max = Math.max(max, value);
            }
            long p95 = values.get(Math.min(values.size() - 1, (int) Math.floor(values.size() * 0.95)));
            return new TimingSnapshot(values.size(), total / values.size(), p95, max);
        }
    }

    private record TimingSnapshot(int samples, long avgNanos, long p95Nanos, long maxNanos) {
    }
}
