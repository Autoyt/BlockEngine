package dev.auto.blockengine.flare;

import dev.auto.blockengine.api.BlockEngine;
import dev.auto.blockengine.api.inspection.BlockInspection;
import dev.auto.blockengine.api.inspection.InspectorCommand;
import dev.auto.flare.api.AddonDescriptor;
import dev.auto.flare.api.FlareAddonProvider;
import dev.auto.flare.api.FlareAddonRegistrar;
import dev.auto.flare.api.FlareApi;
import dev.auto.flare.api.event.FlareAddonsChangedEvent;
import dev.auto.flare.api.event.FlarePackStatusEvent;
import dev.auto.flare.api.schema.Layout;
import dev.auto.flare.api.widget.WidgetId;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Independently hosted visual consumer. It depends exclusively on BlockEngine and Flare public APIs. */
public final class BlockEngineInspectorPlugin extends JavaPlugin implements Listener, FlareAddonProvider {
    public static final String ADDON = "blockengine_inspector";
    private static final String PERMISSION = InspectorCommand.PERMISSION;
    private static final String WIDGETS_PERMISSION = "flare.widgets.edit";
    private static final String USAGE = "Usage: /be inspector [info|on|off|resend]";
    private static final Key HUD = Key.key("blockengine_inspector:hud");
    private static final TextColor BLUE = TextColor.color(0x89A7D2);
    private static final TextColor VIOLET = TextColor.color(0xAD9BCB);
    private static final TextColor PEARL = TextColor.color(0xF0EEEB);
    private static final TextColor GOLD = TextColor.color(0xD9BC82);
    private static final TextColor ROSE = TextColor.color(0xC89BB1);

    private final Map<UUID, InspectorSession> sessions = new HashMap<>();
    private FlareApi flare;
    private InspectorState state;
    private ConsumerPackHost host;
    private URI packUrl;
    private int range;
    private int interval;
    private boolean started;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        range = getConfig().getInt("target-range", 8);
        interval = getConfig().getInt("update-interval-ticks", 2);
        if (range < 1 || range > 64 || interval < 1 || interval > 1200) {
            throw new IllegalArgumentException("target-range must be 1..64; update-interval-ticks must be 1..1200");
        }
        flare = getServer().getServicesManager().load(FlareApi.class);
        if (flare == null) throw new IllegalStateException("Flare's service is unavailable");

        try (var resource = getResource("blockengine-inspector.zip")) {
            if (resource == null) throw new IOException("Bundled inspector pack is missing");
            host = new ConsumerPackHost(new InetSocketAddress(getConfig().getString("listen-address", "0.0.0.0"),
                    getConfig().getInt("port", 8128)), resource.readAllBytes());
            packUrl = host.downloadUri(getConfig().getString("public-url", "http://127.0.0.1:8128"));
        } catch (IOException | IllegalArgumentException failure) {
            if (host != null) host.close();
            host = null;
            throw new IllegalStateException("Cannot host the BlockEngine inspector resource pack", failure);
        }

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getServicesManager().register(InspectorCommand.class, new InspectorControls(), this,
                ServicePriority.Normal);
        getLogger().info("Inspector pack: " + packUrl + "; default enabled for blockengine.inspector (operators).");
    }

    @Override
    public void registerFlareAddons(FlareAddonRegistrar registrar) {
        var registration = registrar.registerAddon(new AddonDescriptor(ADDON,
                "BlockEngine wand inspector", "Live target identity, coordinates and stored runtime diagnostics.",
                true, PERMISSION, PERMISSION));
        state = new InspectorState(registration);
        registration.registerWidget(new InspectorWidget());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void ready(ServerLoadEvent event) {
        if (started || state == null) return;

        Layout layout;
        try {
            layout = flare.layout();
        } catch (IllegalStateException failure) {
            getLogger().warning("The inspector cannot start without a frozen Flare catalog: " + failure.getMessage());
            return;
        }

        String placement = new WidgetId(ADDON, InspectorWidget.ID).placementSchema();
        if (!hasSchema(layout, InspectorState.SCHEMA) || !hasSchema(layout, placement)) {
            getLogger().warning("The inspector target or widget schema is missing from Flare's startup catalog.");
            return;
        }

        started = true;
        getServer().getScheduler().runTaskTimer(this, this::tick, 1, interval);
    }

    private boolean hasSchema(Layout layout, String localName) {
        String key = ADDON + ":" + localName;
        return layout.schemas().stream().anyMatch(schema -> schema.owner().equals(getName()) && schema.key().equals(key));
    }

    private void tick() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (!enabled(player)) {
                remove(player);
                continue;
            }

            boolean wand = holdingWand(player);
            BlockInspection target = wand ? inspect(player) : null;
            state.write(player.getUniqueId(), wand, target);
            activate(player);
        }
    }

    private boolean enabled(Player player) {
        return player.hasPermission(PERMISSION) && flare.isAddonEnabled(player.getUniqueId(), ADDON);
    }

    private boolean holdingWand(Player player) {
        return BlockEngine.inspection().isWand(player.getInventory().getItemInMainHand());
    }

    private BlockInspection inspect(Player player) {
        var block = player.getTargetBlockExact(range);
        return block == null ? null : BlockEngine.inspection().inspect(block);
    }

    @EventHandler
    public void changed(FlareAddonsChangedEvent event) {
        if (!started) return;

        Player player = event.getPlayer();
        if (!event.enabledAddons().contains(ADDON) || !player.hasPermission(PERMISSION)) {
            remove(player);
            return;
        }

        if (event.reason() == FlareAddonsChangedEvent.Reason.RESEND) remove(player);
        if (flare.isPackLoaded(player)) offerOrActivate(player);
    }

    @EventHandler
    public void transport(FlarePackStatusEvent event) {
        if (!started) return;

        Player player = event.getPlayer();
        if (!event.isLoaded() || !enabled(player)) {
            player.postEffects().remove(HUD);
            return;
        }
        offerOrActivate(player);
    }

    private void offerOrActivate(Player player) {
        InspectorSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new InspectorSession());
        if (session.offerId() == null) {
            UUID id = session.offer();
            player.addResourcePack(id, packUrl.toString(), host.hash(), "BlockEngine wand inspector", false);
        }
        activate(player);
    }

    @EventHandler
    public void packStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        InspectorSession session = sessions.get(player.getUniqueId());
        if (session == null || !event.getID().equals(session.offerId())) return;
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.ACCEPTED
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.DOWNLOADED) return;

        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            session.loaded(event.getID());
            activate(player);
        } else {
            session.failed(event.getID());
            player.postEffects().remove(HUD);
        }
    }

    private void activate(Player player) {
        InspectorSession session = sessions.get(player.getUniqueId());
        boolean show = session != null && session.ready(enabled(player), flare.isPackLoaded(player)) && holdingWand(player);
        if (show) {
            if (!player.postEffects().values().contains(HUD)) player.postEffects().add(HUD);
        } else {
            player.postEffects().remove(HUD);
        }
    }

    private void remove(Player player) {
        player.postEffects().remove(HUD);
        InspectorSession session = sessions.remove(player.getUniqueId());
        UUID id = session == null ? null : session.clear();
        if (id != null) player.removeResourcePack(id);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        getServer().getScheduler().cancelTasks(this);
        if (started) for (Player player : getServer().getOnlinePlayers()) remove(player);
        started = false;
        state = null;
        sessions.clear();
        if (host != null) host.close();
    }

    private void showInfo(Player player) {
        var target = inspect(player);
        var session = sessions.get(player.getUniqueId());
        Component body = row("Enabled / wand held", enabled(player) + " / " + holdingWand(player))
                .append(row("Packs loaded (Flare / inspector)",
                        flare.isPackLoaded(player) + " / " + (session != null && session.ready(true, true))))
                .append(row("Pack offer", session == null ? "none" : session.offerId()))
                .append(row("Pack URL", packUrl))
                .append(row("Poll / range", interval + " ticks / " + range + " blocks"))
                .append(Component.newline());

        if (target == null) {
            body = body.append(row("Target", "No block in range"));
        } else {
            body = body.append(row("Target kind", target.kind()))
                    .append(row("Block", target.blockId()))
                    .append(row("State", target.stateId().isEmpty() ? "N/A" : target.stateId()))
                    .append(row("Backing material", target.backingMaterial()))
                    .append(row("World", target.worldName() + " / " + target.worldId()))
                    .append(row("XYZ", target.x() + " / " + target.y() + " / " + target.z()))
                    .append(row("Chunk XZ / local XZ", target.chunkX() + ", " + target.chunkZ()
                            + " / " + target.localX() + ", " + target.localZ()));
            if (target.kind() == BlockInspection.Kind.CUSTOM) {
                body = body.append(row("Stored displays / payload bytes", target.displayCount() + " / " + target.payloadBytes()))
                        .append(row("Exposed / unbreakable", target.exposed() + " / " + target.unbreakable()))
                        .append(row("Hardness / mining speed", target.hardness() + " / " + target.miningSpeed()));
            }
        }

        body = body.append(Component.newline()).append(action("Enable", "/be inspector on"))
                .append(Component.text("  ")).append(action("Disable", "/be inspector off"))
                .append(Component.text("  ")).append(action("Resend packs", "/be inspector resend"))
                .append(Component.newline()).append(action("Get wand", "/be wand"))
                .append(Component.text("  ")).append(action("Refresh", "/be inspector"));
        if (player.hasPermission(WIDGETS_PERMISSION)) {
            body = body.append(Component.text("  ")).append(action("Move or resize HUD", "/flare widgets"));
        }

        Component contents = body;
        player.showDialog(Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("BlockEngine · Inspector", BLUE))
                        .externalTitle(Component.text("Wand inspector", VIOLET)).canCloseWithEscape(true).pause(false)
                        .body(List.of(DialogBody.plainMessage(contents, 450))).build())
                .type(DialogType.notice())));
    }

    private static Component row(String name, Object value) {
        return Component.text(name + ": ", VIOLET).append(Component.text(String.valueOf(value), PEARL))
                .append(Component.newline());
    }

    private static Component action(String label, String command) {
        return Component.text("[" + label + "]", GOLD).clickEvent(ClickEvent.runCommand(command));
    }

    private final class InspectorControls implements InspectorCommand {
        @Override
        public void execute(CommandSourceStack source, String[] args) {
            if (!(source.getSender() instanceof Player player)) {
                source.getSender().sendMessage("Use /flare admin addons add|remove " + ADDON
                        + " <player> or /flare admin addons resend <player>.");
                return;
            }
            if (!player.hasPermission(PERMISSION)) {
                player.sendMessage(Component.text("Missing permission: " + PERMISSION, ROSE));
                return;
            }
            if (!started) {
                player.sendMessage(Component.text("The inspector's Flare add-on is unavailable.", ROSE));
                return;
            }

            try {
                if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("info"))) {
                    showInfo(player);
                    return;
                }
                if (args.length != 1) throw new IllegalArgumentException(USAGE);
                CompletionStage<Void> result = switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "on" -> flare.setAddonEnabled(player, ADDON, true);
                    case "off" -> flare.setAddonEnabled(player, ADDON, false);
                    case "resend" -> flare.resendAddons(player);
                    default -> throw new IllegalArgumentException(USAGE);
                };
                result.whenComplete((ignored, failure) -> {
                    if (!isEnabled()) return;
                    getServer().getScheduler().runTask(BlockEngineInspectorPlugin.this, () -> {
                        if (!player.isOnline()) return;
                        player.sendMessage(Component.text(failure == null ? "Inspector add-on updated." : "Inspector update failed: " + failure.getMessage(),
                                failure == null ? BLUE : ROSE));
                    });
                });
            } catch (IllegalArgumentException | IllegalStateException failure) {
                player.sendMessage(Component.text(failure.getMessage(), ROSE));
            }
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            if (!source.getSender().hasPermission(PERMISSION) || args.length > 1) return List.of();
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("info", "on", "off", "resend").stream().filter(value -> value.startsWith(prefix)).toList();
        }
    }
}
