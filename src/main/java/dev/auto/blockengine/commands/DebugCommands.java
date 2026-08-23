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
import dev.auto.blockengine.chat.BlockEngineChat;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.registry.NamespaceRegistry;
import dev.auto.blockengine.resourcepack.ResourcePackManager;
import dev.auto.blockengine.resourcepack.ResourcePackDownload;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.PerformanceMetrics;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.types.BlockDefinition;
import dev.auto.blockengine.types.BlockLocationKey;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
            "perf", "blocks", "plugins", "give", "chunks", "validate", "events", "reload", "profile", "block", "plugin",
            "chunk", "visibility", "displays", "sample-pack"
    );
    private static final List<String> PROFILE_TARGETS = List.of(
            "overall", "placement", "validation", "chunk-save", "flush", "events", "visibility", "displays", "commands"
    );
    private static final int MAX_EVENT_TAIL = 60;

    private final Main plugin;
    private final Map<UUID, LiveProfile> liveProfiles = new HashMap<>();
    private final TimingRegistry timings = new TimingRegistry();
    private final ActivityRegistry activity = new ActivityRegistry();
    private final Queue<String> eventTail = new ArrayDeque<>();
    private boolean eventTailEnabled;
    private boolean performanceTrackingEnabled = true;

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
            case "sample-pack", "samplepack" -> samplePack(sender);
            case "reload" -> {
                ResourcePackManager.getInstance().reload();
                DebugStyle.success(sender, "Regenerated and reloaded BlockEngine resource packs.");
            }
            case "visibility" -> DebugStyle.warn(sender, "Visibility debug commands are planned: player, chunk, refresh.");
            case "displays" -> DebugStyle.warn(sender, "Display debug commands are planned: nearby, attached, cleanup.");
            default -> usage(sender);
        }
        recordTiming("commands", System.nanoTime() - started);
    }

    @Override
    public @Nullable String permission() {
        return "blockengine.debug";
    }

    public void perfShortcut(@NotNull CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission("blockengine.debug")) {
            DebugStyle.error(sender, "You don't have permission to use this command!");
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("stop")) {
            if (sender instanceof Player player) {
                stopLive(player);
                DebugStyle.success(sender, "Stopped live BlockEngine profile view.");
                return;
            }
            DebugStyle.error(sender, "Only players can stop a bossbar profile view.");
            return;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
            performanceTrackingEnabled = args[0].equalsIgnoreCase("on");
            PerformanceMetrics.enabled(performanceTrackingEnabled);
            DebugStyle.success(sender, "BlockEngine performance tracking is now "
                    + (performanceTrackingEnabled ? "enabled." : "disabled."));
            return;
        }

        String target = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "overall";
        if (!PROFILE_TARGETS.contains(target)) {
            DebugStyle.error(sender, "Unknown profile target. Try: " + String.join(", ", PROFILE_TARGETS));
            return;
        }
        if (sender instanceof Player player) {
            startLive(player, target);
            DebugStyle.success(sender, "Started live BlockEngine profile view for " + target + ".");
        }
        perfOverview(sender, target);
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
        BlockEngineChat.send(sender, DebugStyle.header("blockengine debug"));
        BlockEngineChat.send(sender, DebugStyle.row("usage", "/blockengine debug <subcommand>"));
        BlockEngineChat.send(sender, DebugStyle.row("short", "/be debug <subcommand>"));
        BlockEngineChat.send(sender, DebugStyle.row("main", "perf, blocks, plugins, give, chunks, validate, events, reload"));
        BlockEngineChat.send(sender, DebugStyle.row("packs", "sample-pack"));
        BlockEngineChat.send(sender, DebugStyle.row("legacy", "profile, block, plugin, chunk"));
    }

    private void info(@NotNull CommandSender sender) {
        BlockEngineChat.send(sender, DebugStyle.header("blockengine debug"));
        BlockEngineChat.send(sender, DebugStyle.row("blocks", BlockRegistry.getBlocks().size()));
        BlockEngineChat.send(sender, DebugStyle.row("namespaces", namespaces().size()));
        BlockEngineChat.send(sender, DebugStyle.row("resource packs", ResourcePackManager.getInstance().packIds().size()));
        BlockEngineChat.send(sender, DebugStyle.action("packs", "/blockengine packs list", "List resource packs")
                .append(Component.space())
                .append(DebugStyle.action("plugins", "/blockengine debug plugins", "List BlockEngine plugins"))
                .append(Component.space())
                .append(DebugStyle.action("blocks", "/blockengine debug blocks list", "List registered blocks")));
    }

    private void pack(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2) {
            DebugStyle.usage(sender, "/blockengine packs <reload|resend|download|list>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("reload")) {
            ResourcePackManager.getInstance().reload();
            DebugStyle.success(sender, "Regenerated and reloaded BlockEngine resource packs.");
            return;
        }
        if (action.equals("list")) {
            BlockEngineChat.send(sender, DebugStyle.header("resource packs"));
            for (String packId : ResourcePackManager.getInstance().packIds()) {
                BlockEngineChat.send(sender, DebugStyle.bullet(DebugStyle.pluginName(packId)
                        .append(Component.space())
                        .append(DebugStyle.action("resend", "/blockengine packs resend " + packId, "Resend this pack"))
                        .append(Component.space())
                        .append(DebugStyle.action("download", "/blockengine packs download " + packId, "Create a download link"))));
            }
            BlockEngineChat.send(sender, DebugStyle.bullet(DebugStyle.action("download all", "/blockengine packs download *",
                    "Create one combined download link")));
            return;
        }
        if (action.equals("download")) {
            packDownload(sender, args);
            return;
        }
        if (!action.equals("resend")) {
            DebugStyle.usage(sender, "/blockengine packs <reload|resend|download|list>");
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

    private void packDownload(@NotNull CommandSender sender, String[] args) {
        String packId = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "blockengine";
        ResourcePackDownload link = ResourcePackManager.getInstance().download(packId);
        if (link == null) {
            DebugStyle.error(sender, "Unknown or unavailable pack '" + packId + "'. Try: "
                    + String.join(", ", downloadPackIds()));
            return;
        }

        Component download = Component.text(link.url(), BlockEngineChat.ORANGE_LIGHT)
                .clickEvent(ClickEvent.openUrl(link.url()))
                .hoverEvent(HoverEvent.showText(Component.text("Open pack download", BlockEngineChat.ORANGE_LIGHT)));
        BlockEngineChat.send(sender, DebugStyle.header(packId.equals("*") ? "combined pack download" : "pack download"));
        BlockEngineChat.send(sender, DebugStyle.row("pack", packId.equals("*") ? "all packs" : link.packId()));
        BlockEngineChat.send(sender, DebugStyle.row("size", bytes(link.bytes())));
        BlockEngineChat.send(sender, DebugStyle.row("file", link.zip().getFileName()));
        BlockEngineChat.send(sender, DebugStyle.row("download", download));
    }

    private void samplePack(@NotNull CommandSender sender) {
        ResourcePackDownload link = ResourcePackManager.getInstance().sampleExpansionPackDownload();
        if (link == null) {
            DebugStyle.error(sender, "The bundled sample expansion pack is unavailable.");
            return;
        }

        Component download = Component.text(link.url(), BlockEngineChat.ORANGE_LIGHT)
                .clickEvent(ClickEvent.openUrl(link.url()))
                .hoverEvent(HoverEvent.showText(Component.text("Open sample expansion pack download", BlockEngineChat.ORANGE_LIGHT)));
        BlockEngineChat.send(sender, DebugStyle.header("sample expansion pack"));
        BlockEngineChat.send(sender, DebugStyle.row("size", bytes(link.bytes())));
        BlockEngineChat.send(sender, DebugStyle.row("file", link.zip().getFileName()));
        BlockEngineChat.send(sender, DebugStyle.row("download", download));
        BlockEngineChat.send(sender, DebugStyle.row("install", "plugins/blockengine/expansion/packs/sample-expansion-pack.zip"));
        BlockEngineChat.send(sender, DebugStyle.row("restart", "restart the server to discover newly installed blocks"));
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
        if (args.length >= 2 && (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            performanceTrackingEnabled = args[1].equalsIgnoreCase("on");
            PerformanceMetrics.enabled(performanceTrackingEnabled);
            DebugStyle.success(sender, "BlockEngine performance tracking is now "
                    + (performanceTrackingEnabled ? "enabled." : "disabled."));
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

        String target = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "overall";
        if (target.equals("overall")) {
            perfOverview(sender, target);
            return;
        }
        TimingSnapshot snapshot = timings.snapshot(target);
        ActivitySnapshot activitySnapshot = activity.snapshot(target);
        BlockEngineChat.send(sender, DebugStyle.header("profile " + target));
        if (snapshot.samples() > 0) {
            BlockEngineChat.send(sender, DebugStyle.row("process avg", ms(snapshot.avgNanos()) + "ms"));
            BlockEngineChat.send(sender, DebugStyle.row("process p95", ms(snapshot.p95Nanos()) + "ms"));
            BlockEngineChat.send(sender, DebugStyle.row("process max", ms(snapshot.maxNanos()) + "ms"));
            BlockEngineChat.send(sender, DebugStyle.row("samples", snapshot.samples()));
            BlockEngineChat.send(sender, DebugStyle.row("last sample", age(snapshot.lastSampleAgeNanos())));
            BlockEngineChat.send(sender, DebugStyle.row("speed", ops(snapshot) + " ops/s"));
        } else {
            BlockEngineChat.send(sender, DebugStyle.row("process", DebugStyle.status("idle", false)));
        }
        BlockEngineChat.send(sender, DebugStyle.row("event rate", rate(activitySnapshot) + " events/s"));
        BlockEngineChat.send(sender, DebugStyle.row("events", activitySnapshot.samples()));
        BlockEngineChat.send(sender, DebugStyle.row("last event", age(activitySnapshot.lastEventAgeNanos())));
        BlockEngineChat.send(sender, DebugStyle.action("bossbar", "/blockengine debug perf live " + target, "Show live bossbar profile")
                .append(Component.space())
                .append(DebugStyle.action("stop", "/blockengine debug perf stop", "Stop live bossbar profile")));
    }

    private void perfOverview(@NotNull CommandSender sender, @NotNull String target) {
        PerformanceOverview overview = overview();
        TimingSnapshot commands = timings.snapshot("commands");
        TimingSnapshot validation = timings.snapshot("validation");
        ActivitySnapshot placement = activity.snapshot("placement");
        ActivitySnapshot events = activity.snapshot("events");
        ActivitySnapshot chunkSave = activity.snapshot("chunk-save");

        BlockEngineChat.send(sender, DebugStyle.header("performance overview"));
        BlockEngineChat.send(sender, DebugStyle.row("health", perfHealth(commands, validation, events)));
        BlockEngineChat.send(sender, DebugStyle.row("tracking", DebugStyle.status(
                performanceTrackingEnabled ? "enabled" : "disabled",
                performanceTrackingEnabled
        )));
        BlockEngineChat.send(sender, DebugStyle.row("bossbar", sender instanceof Player player
                ? DebugStyle.status(liveProfiles.containsKey(player.getUniqueId()) ? "running" : "ready", true)
                : DebugStyle.status("player only", false)));

        BlockEngineChat.send(sender, DebugStyle.header("runtime load"));
        BlockEngineChat.send(sender, DebugStyle.row("loaded chunks", overview.loadedChunks()));
        BlockEngineChat.send(sender, DebugStyle.row("stored blocks", overview.storedBlocks()));
        BlockEngineChat.send(sender, DebugStyle.row("exposed blocks", overview.exposedBlocks()));
        BlockEngineChat.send(sender, DebugStyle.row("attached displays", overview.displays()));
        BlockEngineChat.send(sender, DebugStyle.row("registered blocks", BlockRegistry.getBlocks().size()));
        BlockEngineChat.send(sender, DebugStyle.row("namespaces", namespaces().size()));
        BlockEngineChat.send(sender, DebugStyle.row("resource packs", ResourcePackManager.getInstance().packIds().size()));

        BlockEngineChat.send(sender, DebugStyle.header("process phases"));
        perfTiming(sender, "commands", commands);
        perfTiming(sender, "validation", validation);
        perfTiming(sender, "chunk flush", timings.snapshot("flush"));
        perfTiming(sender, "visibility", timings.snapshot("visibility"));
        perfTiming(sender, "displays", timings.snapshot("displays"));

        BlockEngineChat.send(sender, DebugStyle.header("chunk storage"));
        runtimeTiming(sender, "chunk load", PerformanceMetrics.snapshot(PerformanceMetrics.CHUNK_LOAD), "chunks", "blocks");
        runtimeTiming(sender, "chunk save", PerformanceMetrics.snapshot(PerformanceMetrics.CHUNK_SAVE), "chunks", "blocks");
        runtimeTiming(sender, "chunk encode", PerformanceMetrics.snapshot(PerformanceMetrics.CHUNK_ENCODE), "chunks", "blocks");
        runtimeTiming(sender, "chunk decode", PerformanceMetrics.snapshot(PerformanceMetrics.CHUNK_DECODE), "chunks", "blocks");

        BlockEngineChat.send(sender, DebugStyle.header("block storage"));
        runtimeTiming(sender, "block read", PerformanceMetrics.snapshot(PerformanceMetrics.BLOCK_READ), "blocks", "blocks");
        runtimeTiming(sender, "block write", PerformanceMetrics.snapshot(PerformanceMetrics.BLOCK_WRITE), "blocks", "blocks");

        BlockEngineChat.send(sender, DebugStyle.header("adapter payload"));
        runtimeTiming(sender, "adapter load", PerformanceMetrics.snapshot(PerformanceMetrics.ADAPTER_LOAD), "calls", "blocks");
        runtimeTiming(sender, "adapter save", PerformanceMetrics.snapshot(PerformanceMetrics.ADAPTER_SAVE), "calls", "blocks");

        BlockEngineChat.send(sender, DebugStyle.header("event phases"));
        perfActivity(sender, "placement", placement);
        perfActivity(sender, "events", events);
        perfActivity(sender, "chunk-save", chunkSave);

        BlockEngineChat.send(sender, DebugStyle.header("controls"));
        BlockEngineChat.send(sender, DebugStyle.action("start bossbar", "/be debug perf live " + target, "Start the live performance bossbar")
                .append(Component.space())
                .append(DebugStyle.action("stop bossbar", "/be debug perf stop", "Stop the live performance bossbar"))
                .append(Component.space())
                .append(DebugStyle.action(performanceTrackingEnabled ? "tracking off" : "tracking on",
                        "/be debug perf " + (performanceTrackingEnabled ? "off" : "on"),
                        "Toggle BlockEngine performance recording")));
        BlockEngineChat.send(sender, DebugStyle.action("live overall", "/be debug perf live overall", "Show overall live performance")
                .append(Component.space())
                .append(DebugStyle.action("live validation", "/be debug perf live validation", "Show validation live performance")
                )
                .append(Component.space())
                .append(DebugStyle.action("live events", "/be debug perf live events", "Show event live performance")));
        BlockEngineChat.send(sender,
                DebugStyle.action("events tail", "/be debug events tail", "Show recent event tail")
                .append(Component.space())
                .append(DebugStyle.action("debug info", "/be debug info", "Open BlockEngine debug info")));
    }

    private @NotNull Component perfHealth(
            @NotNull TimingSnapshot commands,
            @NotNull TimingSnapshot validation,
            @NotNull ActivitySnapshot events
    ) {
        long worst = Math.max(commands.avgNanos(), validation.avgNanos());
        boolean good = worst <= 1_000_000L;
        boolean active = events.active() || commands.samples() > 0 || validation.samples() > 0;
        String label = !performanceTrackingEnabled ? "tracking disabled"
                : !active ? "idle"
                : good ? "healthy"
                : worst <= 5_000_000L ? "warm" : "slow";
        return DebugStyle.status(label, performanceTrackingEnabled && (good || !active));
    }

    private void perfTiming(@NotNull CommandSender sender, @NotNull String label, @NotNull TimingSnapshot snapshot) {
        if (snapshot.samples() <= 0) {
            BlockEngineChat.send(sender, DebugStyle.row(label, DebugStyle.status("idle", false)
                    .append(Component.space())
                    .append(DebugStyle.dim("no samples"))));
            return;
        }
        BlockEngineChat.send(sender, DebugStyle.row(label,
                Component.text("avg ", BlockEngineChat.GRAY)
                        .append(Component.text(ms(snapshot.avgNanos()) + "ms", speedTextColor(snapshot.avgNanos())))
                        .append(Component.text(" | p95 ", BlockEngineChat.GRAY))
                        .append(Component.text(ms(snapshot.p95Nanos()) + "ms", speedTextColor(snapshot.p95Nanos())))
                        .append(Component.text(" | max ", BlockEngineChat.GRAY))
                        .append(Component.text(ms(snapshot.maxNanos()) + "ms", speedTextColor(snapshot.maxNanos())))
                        .append(Component.text(" | speed ", BlockEngineChat.GRAY))
                        .append(DebugStyle.value(ops(snapshot) + "/s"))
                        .append(Component.text(" | samples ", BlockEngineChat.GRAY))
                        .append(DebugStyle.value(snapshot.samples()))
                        .append(Component.text(" | last ", BlockEngineChat.GRAY))
                        .append(DebugStyle.value(age(snapshot.lastSampleAgeNanos())))));
    }

    private void runtimeTiming(
            @NotNull CommandSender sender,
            @NotNull String label,
            @NotNull PerformanceMetrics.Snapshot snapshot,
            @NotNull String sampleUnit,
            @NotNull String unitLabel
    ) {
        if (snapshot.empty()) {
            BlockEngineChat.send(sender, DebugStyle.row(label, DebugStyle.status("idle", false)
                    .append(Component.space())
                    .append(DebugStyle.dim("no samples"))));
            return;
        }

        Component detail = Component.text("avg ", BlockEngineChat.GRAY)
                .append(Component.text(PerformanceMetrics.ms(snapshot.avgNanos()) + "ms", speedTextColor(snapshot.avgNanos())))
                .append(Component.text(" | p95 ", BlockEngineChat.GRAY))
                .append(Component.text(PerformanceMetrics.ms(snapshot.p95Nanos()) + "ms", speedTextColor(snapshot.p95Nanos())))
                .append(Component.text(" | max ", BlockEngineChat.GRAY))
                .append(Component.text(PerformanceMetrics.ms(snapshot.maxNanos()) + "ms", speedTextColor(snapshot.maxNanos())))
                .append(Component.text(" | per " + unitLabel + " ", BlockEngineChat.GRAY))
                .append(Component.text(snapshot.nanosPerUnit() <= 0L
                        ? "n/a"
                        : PerformanceMetrics.ms(snapshot.nanosPerUnit()) + "ms", speedTextColor(snapshot.nanosPerUnit())))
                .append(Component.text(" | avg " + unitLabel + " ", BlockEngineChat.GRAY))
                .append(DebugStyle.value(snapshot.avgUnits()))
                .append(Component.text(" | avg bytes ", BlockEngineChat.GRAY))
                .append(DebugStyle.value(bytes(snapshot.avgBytes())))
                .append(Component.text(" | window ", BlockEngineChat.GRAY))
                .append(DebugStyle.value(snapshot.windowSamples() + " " + sampleUnit))
                .append(Component.text(" | total ", BlockEngineChat.GRAY))
                .append(DebugStyle.value(snapshot.totalSamples() + " " + sampleUnit + ", " + snapshot.totalUnits() + " " + unitLabel))
                .append(Component.text(" | last ", BlockEngineChat.GRAY))
                .append(DebugStyle.value(age(snapshot.lastSampleAgeNanos())));
        BlockEngineChat.send(sender, DebugStyle.row(label, detail));
    }

    private void perfActivity(@NotNull CommandSender sender, @NotNull String label, @NotNull ActivitySnapshot snapshot) {
        BlockEngineChat.send(sender, DebugStyle.row(label,
                rate(snapshot) + "/s"
                        + " | events " + snapshot.samples()
                        + " | last " + age(snapshot.lastEventAgeNanos())));
    }

    private void block(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2) {
            DebugStyle.usage(sender, "/blockengine debug blocks <looking|list|give|namespace|blockId>");
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
            default -> DebugStyle.usage(sender, "/blockengine debug blocks <looking|list|give|namespace|blockId>");
        }
    }

    private void blockList(@NotNull CommandSender sender, @Nullable String namespace) {
        List<BlockDefinition> blocks = blocks(namespace);
        if (blocks.isEmpty()) {
            DebugStyle.warn(sender, namespace == null ? "No registered blocks." : "No blocks for namespace '" + namespace + "'.");
            return;
        }
        BlockEngineChat.send(sender, DebugStyle.header(namespace == null ? "blocks" : namespace + " blocks"));
        for (BlockDefinition block : blocks) {
            Component line = DebugStyle.blockName(block)
                    .clickEvent(ClickEvent.runCommand("/blockengine debug give " + block.id()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to give yourself this block.")));
            BlockEngineChat.send(sender, DebugStyle.bullet(line
                    .append(Component.space())
                    .append(DebugStyle.action("give", "/blockengine debug give " + block.id(), "Give yourself this block"))
                    .append(Component.space())
                    .append(DebugStyle.action("catalog", "/blockengine catalog " + block.name().namespace(),
                            "Open " + block.name().namespace() + " catalog"))));
        }
    }

    private void blockGive(@NotNull CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            DebugStyle.error(sender, "Only players can receive BlockEngine block stacks.");
            return;
        }
        if (args.length < 3) {
            DebugStyle.usage(sender, "/blockengine debug give <blockId> [stateId]");
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
        BlockEngineChat.send(sender, DebugStyle.status("gave", true)
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
        BlockEngineChat.send(sender, DebugStyle.header("target block"));
        BlockEngineChat.send(sender, DebugStyle.row("block", definition == null
                ? DebugStyle.value(view.storedBlock().blockId())
                : DebugStyle.blockName(definition)));
        BlockEngineChat.send(sender, DebugStyle.row("state", view.storedBlock().stateId()));
        BlockEngineChat.send(sender, DebugStyle.row("location", target.getWorld().getName() + " "
                + target.getX() + " " + target.getY() + " " + target.getZ()));
        BlockEngineChat.send(sender, DebugStyle.row("backing type", target.getType()));
        BlockEngineChat.send(sender, DebugStyle.row("displays", view.storedBlock().displays().size()));
    }

    private void plugin(@NotNull CommandSender sender, String[] args) {
        if (args.length >= 2 && namespaces().stream().anyMatch(namespace -> namespace.equalsIgnoreCase(args[1]))) {
            pluginInfo(sender, args[1]);
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            BlockEngineChat.send(sender, DebugStyle.header("plugins"));
            for (String namespace : namespaces()) {
                BlockEngineChat.send(sender, DebugStyle.bullet(DebugStyle.pluginName(namespace)
                        .clickEvent(ClickEvent.runCommand("/blockengine debug plugins " + namespace))
                        .hoverEvent(HoverEvent.showText(Component.text("Inspect " + namespace)))
                        .append(Component.space())
                        .append(DebugStyle.value(blocks(namespace).size() + " blocks"))
                        .append(Component.space())
                        .append(DebugStyle.action("blocks", "/blockengine debug blocks " + namespace, "List this plugin's blocks"))
                        .append(Component.space())
                        .append(DebugStyle.action("catalog", "/blockengine catalog " + namespace, "Open this plugin's catalog"))));
            }
            return;
        }
        if (args[1].equalsIgnoreCase("catalog")) {
            if (!(sender instanceof Player player)) {
                DebugStyle.error(sender, "Only players can open the BlockEngine catalog.");
                return;
            }
            if (args.length < 3) {
                DebugStyle.usage(sender, "/blockengine catalog <namespace>");
                return;
            }
            CatalogListeners.open(player, args[2]);
            return;
        }
        if (args[1].equalsIgnoreCase("blocks")) {
            blockList(sender, args.length >= 3 ? args[2] : null);
            return;
        }
        DebugStyle.usage(sender, "/blockengine debug plugins [namespace|list|catalog|blocks]");
    }

    private void pluginInfo(@NotNull CommandSender sender, @NotNull String namespace) {
        BlockEngineChat.send(sender, DebugStyle.header("plugin " + namespace));
        BlockEngineChat.send(sender, DebugStyle.row("namespace", DebugStyle.pluginName(namespace)));
        BlockEngineChat.send(sender, DebugStyle.row("blocks", blocks(namespace).size()));
        BlockEngineChat.send(sender, DebugStyle.row("pack", ResourcePackManager.getInstance().packIds().contains(namespace)
                ? DebugStyle.status("ready", true)
                : DebugStyle.status("none", false)));
        BlockEngineChat.send(sender, DebugStyle.action("blocks", "/blockengine debug blocks " + namespace, "List this plugin's blocks")
                .append(Component.space())
                .append(DebugStyle.action("catalog", "/blockengine catalog " + namespace, "Open this plugin's catalog"))
                .append(Component.space())
                .append(DebugStyle.action("resend", "/blockengine packs resend " + namespace, "Resend this plugin's pack")));
    }

    private void catalog(@NotNull CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            DebugStyle.error(sender, "Only players can open the BlockEngine catalog.");
            return;
        }
        if (args.length < 2) {
            CatalogListeners.open(player);
        } else {
            CatalogListeners.open(player, args[1]);
        }
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
        BlockEngineChat.send(sender, DebugStyle.header("chunk " + chunk.getX() + "," + chunk.getZ()));
        BlockEngineChat.send(sender, DebugStyle.row("loaded", DebugStyle.status(String.valueOf(loaded != null), loaded != null)));
        BlockEngineChat.send(sender, DebugStyle.row("stored blocks", loaded == null ? 0 : loaded.blocks().size()));
        BlockEngineChat.send(sender, DebugStyle.row("exposed blocks", loaded == null ? 0 : loaded.exposedBlocks().size()));
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
        recordTiming("validation", System.nanoTime() - started);
        BlockEngineChat.send(sender, DebugStyle.header("validation"));
        BlockEngineChat.send(sender, DebugStyle.row("location", block.getX() + " " + block.getY() + " " + block.getZ()));
        BlockEngineChat.send(sender, DebugStyle.row("real type", block.getType()));
        if (view == null) {
            BlockEngineChat.send(sender, DebugStyle.row("record", DebugStyle.status("none", false)));
            return;
        }
        BlockDefinition definition = BlockRegistry.getBlock(view.storedBlock().blockId());
        BlockEngineChat.send(sender, DebugStyle.row("record", definition == null
                ? DebugStyle.value(view.storedBlock().blockId())
                : DebugStyle.blockName(definition)));
        BlockEngineChat.send(sender, DebugStyle.row("state", view.storedBlock().stateId()));
        BlockEngineChat.send(sender, DebugStyle.row("exposed", DebugStyle.status(String.valueOf(view.exposed()), view.exposed())));
    }

    private void events(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("tail")) {
            if (eventTail.isEmpty()) {
                DebugStyle.warn(sender, "No BlockEngine events captured.");
                return;
            }
            BlockEngineChat.send(sender, DebugStyle.header("events tail"));
            for (String event : eventTail) {
                BlockEngineChat.send(sender, DebugStyle.bullet(DebugStyle.value(event)));
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
            default -> DebugStyle.usage(sender, "/blockengine debug events <on|off|tail|clear>");
        }
    }

    private void startLive(@NotNull Player player, @NotNull String target) {
        stopLive(player);
        BossBar bar = Bukkit.createBossBar(bossBarTitle(target), BarColor.GREEN, BarStyle.SOLID);
        bar.addPlayer(player);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            TimingSnapshot snapshot = timings.snapshot(target);
            ActivitySnapshot activitySnapshot = activity.snapshot(target);
            if (target.equals("overall")) {
                PerformanceOverview overview = overview();
                ActivitySnapshot events = activity.snapshot("events");
                TimingSnapshot commands = timings.snapshot("commands");
                double pressure = Math.max(events.ratePerSecond() / 20.0, commands.avgNanos() / 10_000_000.0);
                bar.setColor(commands.avgNanos() > 5_000_000L ? BarColor.RED : events.active() ? BarColor.GREEN : BarColor.WHITE);
                bar.setProgress(Math.max(0.05, Math.min(1.0, pressure)));
                bar.setTitle(overallBossBarTitle(overview, commands, events));
            } else if (snapshot.samples() > 0) {
                double avgMs = snapshot.avgNanos() / 1_000_000.0;
                bar.setColor(snapshot.stale() ? BarColor.WHITE : avgMs > 5.0 ? BarColor.RED : avgMs > 1.0 ? BarColor.YELLOW : BarColor.GREEN);
                bar.setProgress(snapshot.stale() ? 0.05 : Math.max(0.05, Math.min(1.0, avgMs / 10.0)));
                bar.setTitle(bossBarTitle(target, snapshot, activitySnapshot));
            } else {
                bar.setColor(activitySnapshot.active() ? BarColor.GREEN : BarColor.WHITE);
                bar.setProgress(Math.max(0.05, Math.min(1.0, activitySnapshot.ratePerSecond() / 20.0)));
                bar.setTitle(bossBarTitle(target, snapshot, activitySnapshot));
            }
        }, 1L, 20L);
        liveProfiles.put(player.getUniqueId(), new LiveProfile(bar, task));
    }

    private @NotNull String bossBarTitle(@NotNull String target) {
        if (target.equals("overall")) {
            return overallBossBarTitle(overview(), timings.snapshot("commands"), activity.snapshot("events"));
        }
        return bossBarTitle(target, timings.snapshot(target), activity.snapshot(target));
    }

    private @NotNull String overallBossBarTitle(
            @NotNull PerformanceOverview overview,
            @NotNull TimingSnapshot commands,
            @NotNull ActivitySnapshot events
    ) {
        return ChatColor.GOLD + "" + ChatColor.BOLD + "BlockEngine "
                + ChatColor.DARK_GRAY + "» "
                + ChatColor.YELLOW + "overall"
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + "cmd " + speedColor(commands.avgNanos()) + ms(commands.avgNanos()) + "ms"
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + "events " + ChatColor.GREEN + rate(events) + "/s"
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + "blocks " + ChatColor.WHITE + overview.storedBlocks()
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + "chunks " + ChatColor.WHITE + overview.loadedChunks()
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + "displays " + ChatColor.WHITE + overview.displays();
    }

    private @NotNull String bossBarTitle(@NotNull String target, @NotNull TimingSnapshot snapshot,
                                         @NotNull ActivitySnapshot activitySnapshot) {
        if (snapshot.samples() <= 0) {
            return ChatColor.GOLD + "" + ChatColor.BOLD + "BlockEngine "
                    + ChatColor.DARK_GRAY + "» "
                    + ChatColor.YELLOW + target
                    + ChatColor.DARK_GRAY + " | "
                    + ChatColor.GRAY + "rate " + ChatColor.GREEN + rate(activitySnapshot) + "/s"
                    + ChatColor.DARK_GRAY + " | "
                    + ChatColor.GRAY + "events " + ChatColor.WHITE + activitySnapshot.samples()
                    + ChatColor.DARK_GRAY + " | "
                    + ChatColor.GRAY + "last " + ChatColor.WHITE + age(activitySnapshot.lastEventAgeNanos());
        }
        ChatColor speedColor = speedColor(snapshot.avgNanos());
        return ChatColor.GOLD + "" + ChatColor.BOLD + "BlockEngine "
                + ChatColor.DARK_GRAY + "» "
                + ChatColor.YELLOW + target
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + "avg " + speedColor + ms(snapshot.avgNanos()) + "ms"
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + "p95 " + speedColor(snapshot.p95Nanos()) + ms(snapshot.p95Nanos()) + "ms"
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + "max " + speedColor(snapshot.maxNanos()) + ms(snapshot.maxNanos()) + "ms"
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + "ops " + ChatColor.WHITE + ops(snapshot) + "/s"
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + "n " + ChatColor.WHITE + snapshot.samples()
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + "last " + ChatColor.WHITE + age(snapshot.lastSampleAgeNanos());
    }

    private @NotNull ChatColor speedColor(long nanos) {
        double ms = nanos / 1_000_000.0;
        if (ms > 5.0) {
            return ChatColor.RED;
        }
        if (ms > 1.0) {
            return ChatColor.YELLOW;
        }
        return ChatColor.GREEN;
    }

    private @NotNull TextColor speedTextColor(long nanos) {
        double ms = nanos / 1_000_000.0;
        if (ms > 5.0) {
            return BlockEngineChat.ERROR;
        }
        if (ms > 1.0) {
            return BlockEngineChat.WARNING;
        }
        return BlockEngineChat.SUCCESS;
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
        if (performanceTrackingEnabled) {
            activity.record(target);
            if (!target.equals("events")) {
                activity.record("events");
            }
        }
        if (!eventTailEnabled) {
            return;
        }
        eventTail.add(text);
        while (eventTail.size() > MAX_EVENT_TAIL) {
            eventTail.poll();
        }
    }

    private void recordTiming(@NotNull String target, long nanos) {
        if (performanceTrackingEnabled) {
            timings.record(target, nanos);
        }
    }

    private @NotNull Collection<String> suggestPack(String[] args) {
        if (args.length == 2) {
            return matching(List.of("reload", "resend", "download", "list"), args[1]);
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("resend") || args[1].equalsIgnoreCase("download"))) {
            return matching(downloadPackIds(), args[2]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("resend")) {
            return matching(playerTargets(), args[3]);
        }
        return List.of();
    }

    private @NotNull Collection<String> suggestProfile(String[] args) {
        if (args.length == 2) {
            List<String> options = new ArrayList<>(PROFILE_TARGETS);
            options.add("on");
            options.add("off");
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

    private @NotNull List<String> downloadPackIds() {
        List<String> packs = new ArrayList<>();
        packs.add("*");
        packs.addAll(ResourcePackManager.getInstance().packIds());
        return packs;
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

    static @NotNull Collection<String> matching(@NotNull Collection<String> values, @NotNull String prefix) {
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

    private static @NotNull String rate(@NotNull ActivitySnapshot snapshot) {
        return String.format(Locale.ROOT, "%.2f", snapshot.ratePerSecond());
    }

    private static @NotNull String age(long nanos) {
        if (nanos < 0L) {
            return "never";
        }
        if (nanos < 1_000_000_000L) {
            return ms(nanos) + "ms";
        }
        return String.format(Locale.ROOT, "%.1fs", nanos / 1_000_000_000.0);
    }

    private static @NotNull String bytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    private record LiveProfile(@NotNull BossBar bar, @NotNull BukkitTask task) {
    }

    private @NotNull PerformanceOverview overview() {
        int loadedChunks = 0;
        int storedBlocks = 0;
        int exposedBlocks = 0;
        int displays = 0;
        for (ChunkEngine.LoadedChunk chunk : ChunkEngine.chunks()) {
            loadedChunks++;
            storedBlocks += chunk.blocks().size();
            exposedBlocks += chunk.exposedBlocks().size();
            for (RuntimeBlockView block : chunk.blocks()) {
                displays += block.storedBlock().displays().size();
            }
        }
        return new PerformanceOverview(loadedChunks, storedBlocks, exposedBlocks, displays);
    }

    private record PerformanceOverview(int loadedChunks, int storedBlocks, int exposedBlocks, int displays) {
    }

    private static final class TimingRegistry {
        private static final int WINDOW = 120;
        private static final long STALE_NANOS = 5_000_000_000L;
        private final Map<String, ArrayDeque<Long>> samples = new HashMap<>();
        private final Map<String, Long> lastRecorded = new HashMap<>();

        private void record(@NotNull String target, long nanos) {
            ArrayDeque<Long> queue = samples.computeIfAbsent(target, ignored -> new ArrayDeque<>());
            queue.add(nanos);
            lastRecorded.put(target, System.nanoTime());
            while (queue.size() > WINDOW) {
                queue.poll();
            }
        }

        private @NotNull TimingSnapshot snapshot(@NotNull String target) {
            List<Long> values = new ArrayList<>(samples.getOrDefault(target, new ArrayDeque<>()));
            if (values.isEmpty()) {
                return new TimingSnapshot(0, 0, 0, 0, -1L);
            }
            values.sort(Long::compareTo);
            long total = 0;
            long max = 0;
            for (long value : values) {
                total += value;
                max = Math.max(max, value);
            }
            long p95 = values.get(Math.min(values.size() - 1, (int) Math.floor(values.size() * 0.95)));
            long lastAge = System.nanoTime() - lastRecorded.getOrDefault(target, System.nanoTime());
            return new TimingSnapshot(values.size(), total / values.size(), p95, max, lastAge);
        }
    }

    private record TimingSnapshot(int samples, long avgNanos, long p95Nanos, long maxNanos, long lastSampleAgeNanos) {
        private boolean stale() {
            return lastSampleAgeNanos > TimingRegistry.STALE_NANOS;
        }
    }

    private static final class ActivityRegistry {
        private static final int WINDOW = 240;
        private static final long WINDOW_NANOS = 10_000_000_000L;
        private static final long ACTIVE_NANOS = 2_000_000_000L;
        private final Map<String, ArrayDeque<Long>> samples = new HashMap<>();

        private void record(@NotNull String target) {
            long now = System.nanoTime();
            ArrayDeque<Long> queue = samples.computeIfAbsent(target, ignored -> new ArrayDeque<>());
            queue.add(now);
            trim(queue, now);
        }

        private @NotNull ActivitySnapshot snapshot(@NotNull String target) {
            long now = System.nanoTime();
            ArrayDeque<Long> queue = samples.getOrDefault(target, new ArrayDeque<>());
            trim(queue, now);
            if (queue.isEmpty()) {
                return new ActivitySnapshot(0, -1L, 0.0);
            }
            long last = queue.peekLast() == null ? now : queue.peekLast();
            return new ActivitySnapshot(queue.size(), now - last, queue.size() / (WINDOW_NANOS / 1_000_000_000.0));
        }

        private void trim(@NotNull ArrayDeque<Long> queue, long now) {
            long cutoff = now - WINDOW_NANOS;
            while (!queue.isEmpty() && (queue.peekFirst() < cutoff || queue.size() > WINDOW)) {
                queue.poll();
            }
        }
    }

    private record ActivitySnapshot(int samples, long lastEventAgeNanos, double ratePerSecond) {
        private boolean active() {
            return lastEventAgeNanos >= 0L && lastEventAgeNanos <= ActivityRegistry.ACTIVE_NANOS;
        }
    }

    private static final class DebugStyle {
        private DebugStyle() {
        }

        private static @NotNull Component header(@NotNull String title) {
            return BlockEngineChat.header(title);
        }

        private static @NotNull Component bullet(@NotNull Component value) {
            return BlockEngineChat.bullet(value);
        }

        private static @NotNull Component row(@NotNull String label, @NotNull Object value) {
            return BlockEngineChat.row(label, value);
        }

        private static @NotNull Component row(@NotNull String label, @NotNull Component value) {
            return BlockEngineChat.row(label, value);
        }

        private static @NotNull Component action(
                @NotNull String label,
                @NotNull String command,
                @NotNull String hover
        ) {
            return BlockEngineChat.action(label, command, hover);
        }

        private static @NotNull Component pluginName(@NotNull String namespace) {
            return BlockEngineChat.pluginName(namespace);
        }

        private static @NotNull Component blockName(@NotNull BlockDefinition block) {
            return BlockEngineChat.blockName(block);
        }

        private static @NotNull Component status(@NotNull String value, boolean good) {
            return BlockEngineChat.status(value, good);
        }

        private static @NotNull Component value(@NotNull Object value) {
            return BlockEngineChat.value(value);
        }

        private static @NotNull Component dim(@NotNull String value) {
            return BlockEngineChat.dim(value);
        }

        private static void usage(@NotNull CommandSender sender, @NotNull String usage) {
            BlockEngineChat.usage(sender, usage);
        }

        private static void success(@NotNull CommandSender sender, @NotNull String message) {
            BlockEngineChat.success(sender, message);
        }

        private static void warn(@NotNull CommandSender sender, @NotNull String message) {
            BlockEngineChat.warn(sender, message);
        }

        private static void error(@NotNull CommandSender sender, @NotNull String message) {
            BlockEngineChat.error(sender, message);
        }
    }
}
