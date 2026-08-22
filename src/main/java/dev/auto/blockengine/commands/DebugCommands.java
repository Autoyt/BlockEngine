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
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
            "info", "packs", "perf", "blocks", "plugins", "give", "catalog", "chunks", "validate", "events", "reload",
            "pack", "profile", "block", "plugin", "chunk", "visibility", "displays"
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
            DebugStyle.error(sender, "You don't have permission to use this command!");
            return;
        }
        if (args.length == 0) {
            usage(sender);
            return;
        }

        long started = System.nanoTime();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> info(sender);
            case "pack", "packs" -> pack(sender, args);
            case "profile", "perf" -> profile(sender, args);
            case "block", "blocks" -> block(sender, args);
            case "plugin", "plugins" -> plugin(sender, args);
            case "give" -> blockGive(sender, prepend(args, "block"));
            case "catalog" -> catalog(sender, args);
            case "chunk", "chunks" -> chunk(sender, args);
            case "validate" -> validate(sender, args);
            case "events" -> events(sender, args);
            case "reload" -> {
                ResourcePackManager.getInstance().reload();
                DebugStyle.success(sender, "Regenerated and reloaded BlockEngine resource packs.");
            }
            case "visibility" -> DebugStyle.warn(sender, "Visibility debug commands are planned: player, chunk, refresh.");
            case "displays" -> DebugStyle.warn(sender, "Display debug commands are planned: nearby, attached, cleanup.");
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
            case "pack", "packs" -> suggestPack(args);
            case "profile", "perf" -> suggestProfile(args);
            case "block", "blocks" -> suggestBlock(args);
            case "plugin", "plugins" -> suggestPlugin(args);
            case "give" -> args.length == 2 ? matching(blockIds(), args[1]) : List.of();
            case "catalog" -> args.length == 2 ? matching(namespaces(), args[1]) : List.of();
            case "chunk", "chunks" -> suggest(args, 1, List.of("here", "flush", "pending"));
            case "validate" -> suggest(args, 1, List.of("looking", "here"));
            case "events" -> suggest(args, 1, List.of("on", "off", "tail", "clear"));
            case "visibility" -> suggest(args, 1, List.of("player", "chunk", "refresh"));
            case "displays" -> suggest(args, 1, List.of("nearby", "attached", "cleanup"));
            default -> List.of();
        };
    }

    private void usage(@NotNull CommandSender sender) {
        sender.sendMessage(DebugStyle.header("blockengine debug"));
        sender.sendMessage(DebugStyle.row("usage", "/debug <subcommand>"));
        sender.sendMessage(DebugStyle.row("main", "info, packs, perf, blocks, plugins, give, catalog, chunks, events"));
        sender.sendMessage(DebugStyle.row("legacy", "pack, profile, block, plugin, chunk"));
    }

    private void info(@NotNull CommandSender sender) {
        sender.sendMessage(DebugStyle.header("blockengine debug"));
        sender.sendMessage(DebugStyle.row("blocks", BlockRegistry.getBlocks().size()));
        sender.sendMessage(DebugStyle.row("namespaces", namespaces().size()));
        sender.sendMessage(DebugStyle.row("resource packs", ResourcePackManager.getInstance().packIds().size()));
        sender.sendMessage(DebugStyle.action("packs", "/debug packs list", "List resource packs")
                .append(Component.space())
                .append(DebugStyle.action("plugins", "/debug plugins", "List BlockEngine plugins"))
                .append(Component.space())
                .append(DebugStyle.action("blocks", "/debug blocks list", "List registered blocks")));
    }

    private void pack(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2) {
            DebugStyle.usage(sender, "/debug packs <reload|resend|list>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("reload")) {
            ResourcePackManager.getInstance().reload();
            DebugStyle.success(sender, "Regenerated and reloaded BlockEngine resource packs.");
            return;
        }
        if (action.equals("list")) {
            sender.sendMessage(DebugStyle.header("resource packs"));
            for (String packId : ResourcePackManager.getInstance().packIds()) {
                sender.sendMessage(DebugStyle.bullet(DebugStyle.pluginName(packId)
                        .append(Component.space())
                        .append(DebugStyle.action("resend", "/debug packs resend " + packId, "Resend this pack"))));
            }
            return;
        }
        if (!action.equals("resend")) {
            DebugStyle.usage(sender, "/debug packs <reload|resend|list>");
            return;
        }

        String packId = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "blockengine";
        String target = args.length >= 4 ? args[3] : sender instanceof Player player ? player.getName() : "*";
        List<Player> targets = players(target);
        if (targets.isEmpty()) {
            DebugStyle.error(sender, "No online player matched '" + target + "'.");
            return;
        }

        int packSends = 0;
        if (packId.equals("*")) {
            for (Player player : targets) {
                packSends += ResourcePackManager.getInstance().sendAll(player);
            }
        } else if (!ResourcePackManager.getInstance().packIds().contains(packId)) {
            DebugStyle.error(sender, "Unknown pack '" + packId + "'. Try: "
                    + String.join(", ", ResourcePackManager.getInstance().packIds()) + ", *");
            return;
        } else {
            for (Player player : targets) {
                if (ResourcePackManager.getInstance().send(player, packId)) {
                    packSends++;
                }
            }
        }
        DebugStyle.success(sender, "Resent " + packSends + " pack(s) to " + targets.size() + " player(s).");
    }

    private void profile(@NotNull CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
            if (sender instanceof Player player) {
                stopLive(player);
                DebugStyle.success(sender, "Stopped live BlockEngine profile view.");
                return;
            }
            DebugStyle.error(sender, "Only players can stop a bossbar profile view.");
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("live")) {
            if (!(sender instanceof Player player)) {
                DebugStyle.error(sender, "Only players can start a bossbar profile view.");
                return;
            }
            String target = args[2].toLowerCase(Locale.ROOT);
            if (!PROFILE_TARGETS.contains(target)) {
                DebugStyle.error(sender, "Unknown profile target. Try: " + String.join(", ", PROFILE_TARGETS));
                return;
            }
            startLive(player, target);
            DebugStyle.success(sender, "Started live BlockEngine profile view for " + target + ".");
            return;
        }

        String target = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "events";
        TimingSnapshot snapshot = timings.snapshot(target);
        sender.sendMessage(DebugStyle.header("profile " + target));
        sender.sendMessage(DebugStyle.row("process avg", ms(snapshot.avgNanos()) + "ms"));
        sender.sendMessage(DebugStyle.row("process p95", ms(snapshot.p95Nanos()) + "ms"));
        sender.sendMessage(DebugStyle.row("process max", ms(snapshot.maxNanos()) + "ms"));
        sender.sendMessage(DebugStyle.row("samples", snapshot.samples()));
        sender.sendMessage(DebugStyle.row("speed", ops(snapshot) + " ops/s"));
        sender.sendMessage(DebugStyle.action("bossbar", "/debug perf live " + target, "Show live bossbar profile")
                .append(Component.space())
                .append(DebugStyle.action("stop", "/debug perf stop", "Stop live bossbar profile")));
    }

    private void block(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2) {
            DebugStyle.usage(sender, "/debug blocks <looking|list|give|namespace|blockId>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (BlockRegistry.getBlock(args[1]) != null) {
            blockGive(sender, new String[]{"block", "give", args[1]});
            return;
        }
        if (namespaces().stream().anyMatch(namespace -> namespace.equalsIgnoreCase(args[1]))) {
            blockList(sender, args[1]);
            return;
        }
        switch (action) {
            case "list" -> blockList(sender, args.length >= 3 && !args[2].equals("*") ? args[2] : null);
            case "give" -> blockGive(sender, args);
            case "looking" -> blockLooking(sender);
            default -> DebugStyle.usage(sender, "/debug blocks <looking|list|give|namespace|blockId>");
        }
    }

    private void blockList(@NotNull CommandSender sender, @Nullable String namespace) {
        List<BlockDefinition> blocks = blocks(namespace);
        if (blocks.isEmpty()) {
            DebugStyle.warn(sender, namespace == null ? "No registered blocks." : "No blocks for namespace '" + namespace + "'.");
            return;
        }
        sender.sendMessage(DebugStyle.header(namespace == null ? "blocks" : namespace + " blocks"));
        for (BlockDefinition block : blocks) {
            Component line = DebugStyle.blockName(block)
                    .clickEvent(ClickEvent.runCommand("/debug block give " + block.id()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to give yourself this block.")));
            sender.sendMessage(DebugStyle.bullet(line
                    .append(Component.space())
                    .append(DebugStyle.action("give", "/debug block give " + block.id(), "Give yourself this block"))
                    .append(Component.space())
                    .append(DebugStyle.action("catalog", "/debug plugin catalog " + block.name().namespace(),
                            "Open " + block.name().namespace() + " catalog"))));
        }
    }

    private void blockGive(@NotNull CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            DebugStyle.error(sender, "Only players can receive BlockEngine block stacks.");
            return;
        }
        if (args.length < 3) {
            DebugStyle.usage(sender, "/debug give <blockId> [stateId]");
            return;
        }
        BlockDefinition block = BlockRegistry.getBlock(args[2]);
        if (block == null) {
            DebugStyle.error(sender, "Unknown block '" + args[2] + "'.");
            return;
        }
        String stateId = args.length >= 4 ? args[3] : block.apiDefinition().defaultState();
        if (!block.apiDefinition().states().containsKey(stateId)) {
            DebugStyle.error(sender, "Unknown state '" + stateId + "'.");
            return;
        }
        ItemStack stack = ItemManager.create(block, stateId);
        stack.setAmount(64);
        player.getInventory().addItem(stack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        sender.sendMessage(DebugStyle.status("gave", true)
                .append(Component.space())
                .append(DebugStyle.value("64x"))
                .append(Component.space())
                .append(DebugStyle.blockName(block))
                .append(Component.space())
                .append(DebugStyle.dim("[" + stateId + "]")));
    }

    private void blockLooking(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            DebugStyle.error(sender, "Only players can inspect a targeted block.");
            return;
        }
        org.bukkit.block.Block target = player.getTargetBlockExact(8);
        if (target == null) {
            DebugStyle.warn(sender, "No block in range.");
            return;
        }
        RuntimeBlockView view = ChunkEngine.getBlock(new BlockLocationKey(
                target.getWorld().getUID(), target.getX(), target.getY(), target.getZ()
        ));
        if (view == null) {
            DebugStyle.warn(sender, "Target is not a BlockEngine block. Type=" + target.getType());
            return;
        }
        BlockDefinition definition = BlockRegistry.getBlock(view.storedBlock().blockId());
        sender.sendMessage(DebugStyle.header("target block"));
        sender.sendMessage(DebugStyle.row("block", definition == null
                ? DebugStyle.value(view.storedBlock().blockId())
                : DebugStyle.blockName(definition)));
        sender.sendMessage(DebugStyle.row("state", view.storedBlock().stateId()));
        sender.sendMessage(DebugStyle.row("location", target.getWorld().getName() + " "
                + target.getX() + " " + target.getY() + " " + target.getZ()));
        sender.sendMessage(DebugStyle.row("backing type", target.getType()));
        sender.sendMessage(DebugStyle.row("displays", view.storedBlock().displays().size()));
    }

    private void plugin(@NotNull CommandSender sender, String[] args) {
        if (args.length >= 2 && namespaces().stream().anyMatch(namespace -> namespace.equalsIgnoreCase(args[1]))) {
            pluginInfo(sender, args[1]);
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(DebugStyle.header("plugins"));
            for (String namespace : namespaces()) {
                sender.sendMessage(DebugStyle.bullet(DebugStyle.pluginName(namespace)
                        .clickEvent(ClickEvent.runCommand("/debug plugins " + namespace))
                        .hoverEvent(HoverEvent.showText(Component.text("Inspect " + namespace)))
                        .append(Component.space())
                        .append(DebugStyle.value(blocks(namespace).size() + " blocks"))
                        .append(Component.space())
                        .append(DebugStyle.action("blocks", "/debug blocks " + namespace, "List this plugin's blocks"))
                        .append(Component.space())
                        .append(DebugStyle.action("catalog", "/debug catalog " + namespace, "Open this plugin's catalog"))));
            }
            return;
        }
        if (args[1].equalsIgnoreCase("catalog")) {
            if (!(sender instanceof Player player)) {
                DebugStyle.error(sender, "Only players can open the BlockEngine catalog.");
                return;
            }
            if (args.length < 3) {
                DebugStyle.usage(sender, "/debug catalog <namespace>");
                return;
            }
            CatalogListeners.open(player, args[2]);
            return;
        }
        if (args[1].equalsIgnoreCase("blocks")) {
            blockList(sender, args.length >= 3 ? args[2] : null);
            return;
        }
        DebugStyle.usage(sender, "/debug plugins [namespace|list|catalog|blocks]");
    }

    private void pluginInfo(@NotNull CommandSender sender, @NotNull String namespace) {
        sender.sendMessage(DebugStyle.header("plugin " + namespace));
        sender.sendMessage(DebugStyle.row("namespace", DebugStyle.pluginName(namespace)));
        sender.sendMessage(DebugStyle.row("blocks", blocks(namespace).size()));
        sender.sendMessage(DebugStyle.row("pack", ResourcePackManager.getInstance().packIds().contains(namespace)
                ? DebugStyle.status("ready", true)
                : DebugStyle.status("none", false)));
        sender.sendMessage(DebugStyle.action("blocks", "/debug blocks " + namespace, "List this plugin's blocks")
                .append(Component.space())
                .append(DebugStyle.action("catalog", "/debug catalog " + namespace, "Open this plugin's catalog"))
                .append(Component.space())
                .append(DebugStyle.action("resend", "/debug packs resend " + namespace, "Resend this plugin's pack")));
    }

    private void catalog(@NotNull CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            DebugStyle.error(sender, "Only players can open the BlockEngine catalog.");
            return;
        }
        if (args.length < 2) {
            DebugStyle.usage(sender, "/debug catalog <namespace>");
            return;
        }
        CatalogListeners.open(player, args[1]);
    }

    private void chunk(@NotNull CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            DebugStyle.error(sender, "Only players can inspect their current chunk.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("flush")) {
            ChunkEngine.flushNow();
            DebugStyle.success(sender, "Flushed pending BlockEngine chunk data.");
            return;
        }
        Chunk chunk = player.getLocation().getChunk();
        ChunkEngine.LoadedChunk loaded = ChunkEngine.get(ChunkEngine.Key.from(chunk));
        sender.sendMessage(DebugStyle.header("chunk " + chunk.getX() + "," + chunk.getZ()));
        sender.sendMessage(DebugStyle.row("loaded", DebugStyle.status(String.valueOf(loaded != null), loaded != null)));
        sender.sendMessage(DebugStyle.row("stored blocks", loaded == null ? 0 : loaded.blocks().size()));
        sender.sendMessage(DebugStyle.row("exposed blocks", loaded == null ? 0 : loaded.exposedBlocks().size()));
    }

    private void validate(@NotNull CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            DebugStyle.error(sender, "Only players can validate a block.");
            return;
        }
        org.bukkit.block.Block block = args.length >= 2 && args[1].equalsIgnoreCase("here")
                ? player.getLocation().getBlock()
                : player.getTargetBlockExact(8);
        if (block == null) {
            DebugStyle.warn(sender, "No block to validate.");
            return;
        }
        long started = System.nanoTime();
        RuntimeBlockView view = ChunkEngine.getBlock(new BlockLocationKey(
                block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()
        ));
        timings.record("validation", System.nanoTime() - started);
        sender.sendMessage(DebugStyle.header("validation"));
        sender.sendMessage(DebugStyle.row("location", block.getX() + " " + block.getY() + " " + block.getZ()));
        sender.sendMessage(DebugStyle.row("real type", block.getType()));
        if (view == null) {
            sender.sendMessage(DebugStyle.row("record", DebugStyle.status("none", false)));
            return;
        }
        BlockDefinition definition = BlockRegistry.getBlock(view.storedBlock().blockId());
        sender.sendMessage(DebugStyle.row("record", definition == null
                ? DebugStyle.value(view.storedBlock().blockId())
                : DebugStyle.blockName(definition)));
        sender.sendMessage(DebugStyle.row("state", view.storedBlock().stateId()));
        sender.sendMessage(DebugStyle.row("exposed", DebugStyle.status(String.valueOf(view.exposed()), view.exposed())));
    }

    private void events(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("tail")) {
            if (eventTail.isEmpty()) {
                DebugStyle.warn(sender, "No BlockEngine events captured.");
                return;
            }
            sender.sendMessage(DebugStyle.header("events tail"));
            for (String event : eventTail) {
                sender.sendMessage(DebugStyle.bullet(DebugStyle.value(event)));
            }
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                eventTailEnabled = true;
                DebugStyle.success(sender, "BlockEngine event tail enabled.");
            }
            case "off" -> {
                eventTailEnabled = false;
                DebugStyle.success(sender, "BlockEngine event tail disabled.");
            }
            case "clear" -> {
                eventTail.clear();
                DebugStyle.success(sender, "BlockEngine event tail cleared.");
            }
            default -> DebugStyle.usage(sender, "/debug events <on|off|tail|clear>");
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

    private static String[] prepend(@NotNull String[] args, @NotNull String value) {
        String[] result = new String[args.length + 1];
        result[0] = value;
        System.arraycopy(args, 0, result, 1, args.length);
        return result;
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

    private static final class DebugStyle {
        private static final TextColor ORANGE = TextColor.color(0xff9f2e);
        private static final TextColor ORANGE_LIGHT = TextColor.color(0xffc46b);
        private static final TextColor GRAY = TextColor.color(0xa8a8a8);
        private static final TextColor DARK_GRAY = TextColor.color(0x555555);
        private static final TextColor WHITE = TextColor.color(0xf7f7f7);
        private static final TextColor SUCCESS = TextColor.color(0x72d66b);
        private static final TextColor ERROR = TextColor.color(0xff4e4e);
        private static final TextColor WARNING = TextColor.color(0xff6a2e);

        private DebugStyle() {
        }

        private static @NotNull Component header(@NotNull String title) {
            return Component.text("----", GRAY)
                    .append(Component.text(title.toUpperCase(Locale.ROOT), ORANGE)
                            .decorate(TextDecoration.BOLD))
                    .append(Component.text("----", GRAY));
        }

        private static @NotNull Component bullet(@NotNull Component value) {
            return Component.text("➤ ", ORANGE).append(value);
        }

        private static @NotNull Component row(@NotNull String label, @NotNull Object value) {
            return row(label, value(value));
        }

        private static @NotNull Component row(@NotNull String label, @NotNull Component value) {
            return Component.text("  ", DARK_GRAY)
                    .append(Component.text("▟", GRAY))
                    .append(Component.text("▙ ", ORANGE))
                    .append(Component.text(label + ": ", GRAY))
                    .append(value);
        }

        private static @NotNull Component action(
                @NotNull String label,
                @NotNull String command,
                @NotNull String hover
        ) {
            return Component.text(label, ORANGE_LIGHT)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.runCommand(command))
                    .hoverEvent(HoverEvent.showText(Component.text(hover, ORANGE_LIGHT)));
        }

        private static @NotNull Component pluginName(@NotNull String namespace) {
            return Component.text(namespace, namespaceColor(namespace)).decorate(TextDecoration.BOLD);
        }

        private static @NotNull Component blockName(@NotNull BlockDefinition block) {
            String namespace = namespaceOf(block.id());
            String path = pathOf(block.id());
            TextColor color = namespaceColor(namespace);
            return Component.text(namespace, color).decorate(TextDecoration.BOLD)
                    .append(Component.text(":", GRAY).decoration(TextDecoration.BOLD, false))
                    .append(Component.text(path, color).decoration(TextDecoration.BOLD, false));
        }

        private static @NotNull Component status(@NotNull String value, boolean good) {
            return Component.text(value, good ? SUCCESS : WARNING);
        }

        private static @NotNull Component value(@NotNull Object value) {
            return Component.text(String.valueOf(value), WHITE);
        }

        private static @NotNull Component dim(@NotNull String value) {
            return Component.text(value, GRAY);
        }

        private static void usage(@NotNull CommandSender sender, @NotNull String usage) {
            sender.sendMessage(row("usage", usage));
        }

        private static void success(@NotNull CommandSender sender, @NotNull String message) {
            sender.sendMessage(status("success", true).append(Component.text(" " + message, WHITE)));
        }

        private static void warn(@NotNull CommandSender sender, @NotNull String message) {
            sender.sendMessage(Component.text("warn ", WARNING).append(Component.text(message, WHITE)));
        }

        private static void error(@NotNull CommandSender sender, @NotNull String message) {
            sender.sendMessage(Component.text("error ", ERROR).append(Component.text(message, WHITE)));
        }

        private static @NotNull TextColor namespaceColor(@NotNull String namespace) {
            int hash = fnv1a(namespace.toLowerCase(Locale.ROOT));
            float hue = (hash & 0xFFFF) / 65535.0f;
            float saturation = 0.58f + (((hash >>> 16) & 0xFF) / 255.0f) * 0.22f;
            float brightness = 0.72f + (((hash >>> 24) & 0xFF) / 255.0f) * 0.18f;
            return TextColor.color(hsbToRgb(hue, saturation, brightness));
        }

        private static int fnv1a(@NotNull String value) {
            int hash = 0x811C9DC5;
            for (int i = 0; i < value.length(); i++) {
                hash ^= value.charAt(i);
                hash *= 0x01000193;
            }
            return hash;
        }

        private static @NotNull String namespaceOf(@NotNull String blockId) {
            int split = blockId.indexOf(':');
            return split <= 0 ? "blockengine" : blockId.substring(0, split);
        }

        private static @NotNull String pathOf(@NotNull String blockId) {
            int split = blockId.indexOf(':');
            return split < 0 || split == blockId.length() - 1 ? blockId : blockId.substring(split + 1);
        }

        private static int hsbToRgb(float hue, float saturation, float brightness) {
            int red = 0;
            int green = 0;
            int blue = 0;
            if (saturation == 0.0f) {
                red = green = blue = Math.round(brightness * 255.0f);
            } else {
                float scaledHue = (hue - (float) Math.floor(hue)) * 6.0f;
                int sector = (int) scaledHue;
                float fraction = scaledHue - sector;
                float p = brightness * (1.0f - saturation);
                float q = brightness * (1.0f - saturation * fraction);
                float t = brightness * (1.0f - saturation * (1.0f - fraction));
                switch (sector) {
                    case 0 -> {
                        red = Math.round(brightness * 255.0f);
                        green = Math.round(t * 255.0f);
                        blue = Math.round(p * 255.0f);
                    }
                    case 1 -> {
                        red = Math.round(q * 255.0f);
                        green = Math.round(brightness * 255.0f);
                        blue = Math.round(p * 255.0f);
                    }
                    case 2 -> {
                        red = Math.round(p * 255.0f);
                        green = Math.round(brightness * 255.0f);
                        blue = Math.round(t * 255.0f);
                    }
                    case 3 -> {
                        red = Math.round(p * 255.0f);
                        green = Math.round(q * 255.0f);
                        blue = Math.round(brightness * 255.0f);
                    }
                    case 4 -> {
                        red = Math.round(t * 255.0f);
                        green = Math.round(p * 255.0f);
                        blue = Math.round(brightness * 255.0f);
                    }
                    default -> {
                        red = Math.round(brightness * 255.0f);
                        green = Math.round(p * 255.0f);
                        blue = Math.round(q * 255.0f);
                    }
                }
            }
            return (red << 16) | (green << 8) | blue;
        }
    }
}
