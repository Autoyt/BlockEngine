package dev.auto.blockengine.chat;

import dev.auto.blockengine.types.BlockDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class BlockEngineChat {
    public static final TextColor ORANGE = TextColor.color(0xff9f2e);
    public static final TextColor ORANGE_LIGHT = TextColor.color(0xffc46b);
    public static final TextColor GRAY = TextColor.color(0xa8a8a8);
    public static final TextColor DARK_GRAY = TextColor.color(0x555555);
    public static final TextColor WHITE = TextColor.color(0xf7f7f7);
    public static final TextColor SUCCESS = TextColor.color(0x72d66b);
    public static final TextColor ERROR = TextColor.color(0xff4e4e);
    public static final TextColor WARNING = TextColor.color(0xff6a2e);

    private BlockEngineChat() {
    }

    public static void send(@NotNull CommandSender sender, @NotNull String message) {
        send(sender, Component.text(message, WHITE));
    }

    public static void send(@NotNull CommandSender sender, @NotNull Component message) {
        sender.sendMessage(prefix().append(Component.space()).append(message));
    }

    public static void success(@NotNull CommandSender sender, @NotNull String message) {
        send(sender, status("success", true).append(Component.text(" " + message, WHITE)));
    }

    public static void warn(@NotNull CommandSender sender, @NotNull String message) {
        send(sender, Component.text("warn ", WARNING).append(Component.text(message, WHITE)));
    }

    public static void error(@NotNull CommandSender sender, @NotNull String message) {
        send(sender, Component.text("error ", ERROR).append(Component.text(message, WHITE)));
    }

    public static void usage(@NotNull CommandSender sender, @NotNull String usage) {
        send(sender, row("usage", usage));
    }

    public static @NotNull Component prefix() {
        return Component.text("[", DARK_GRAY)
                .append(Component.text("BlockEngine", ORANGE).decorate(TextDecoration.BOLD))
                .append(Component.text("]", DARK_GRAY))
                .clickEvent(ClickEvent.runCommand("/blockengine info"))
                .hoverEvent(HoverEvent.showText(Component.text("Open BlockEngine info", ORANGE_LIGHT)));
    }

    public static @NotNull Component header(@NotNull String title) {
        return Component.text("----", GRAY)
                .append(Component.text(title.toUpperCase(Locale.ROOT), ORANGE).decorate(TextDecoration.BOLD))
                .append(Component.text("----", GRAY));
    }

    public static @NotNull Component bullet(@NotNull Component value) {
        return Component.text("➤ ", ORANGE).append(value);
    }

    public static @NotNull Component row(@NotNull String label, @NotNull Object value) {
        return row(label, value(value));
    }

    public static @NotNull Component row(@NotNull String label, @NotNull Component value) {
        return Component.text("  ", DARK_GRAY)
                .append(Component.text("▟", GRAY))
                .append(Component.text("▙ ", ORANGE))
                .append(Component.text(label + ": ", GRAY))
                .append(value);
    }

    public static @NotNull Component action(
            @NotNull String label,
            @NotNull String command,
            @NotNull String hover
    ) {
        return Component.text(label, ORANGE_LIGHT)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, ORANGE_LIGHT)));
    }

    public static @NotNull Component pluginName(@NotNull String namespace) {
        return Component.text(namespace, namespaceColor(namespace)).decorate(TextDecoration.BOLD);
    }

    public static @NotNull Component blockName(@NotNull BlockDefinition block) {
        String namespace = namespaceOf(block.id());
        String path = pathOf(block.id());
        TextColor color = namespaceColor(namespace);
        return Component.text(namespace, color).decorate(TextDecoration.BOLD)
                .append(Component.text(":", GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(path, color).decoration(TextDecoration.BOLD, false));
    }

    public static @NotNull Component status(@NotNull String value, boolean good) {
        return Component.text(value, good ? SUCCESS : WARNING);
    }

    public static @NotNull Component value(@NotNull Object value) {
        return Component.text(String.valueOf(value), WHITE);
    }

    public static @NotNull Component dim(@NotNull String value) {
        return Component.text(value, GRAY);
    }

    public static @NotNull TextColor namespaceColor(@NotNull String namespace) {
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
        if (saturation == 0.0f) {
            int value = Math.round(brightness * 255.0f);
            return (value << 16) | (value << 8) | value;
        }
        float scaledHue = (hue - (float) Math.floor(hue)) * 6.0f;
        int sector = (int) scaledHue;
        float fraction = scaledHue - sector;
        float p = brightness * (1.0f - saturation);
        float q = brightness * (1.0f - saturation * fraction);
        float t = brightness * (1.0f - saturation * (1.0f - fraction));
        return switch (sector) {
            case 0 -> rgb(brightness, t, p);
            case 1 -> rgb(q, brightness, p);
            case 2 -> rgb(p, brightness, t);
            case 3 -> rgb(p, q, brightness);
            case 4 -> rgb(t, p, brightness);
            default -> rgb(brightness, p, q);
        };
    }

    private static int rgb(float red, float green, float blue) {
        return (Math.round(red * 255.0f) << 16)
                | (Math.round(green * 255.0f) << 8)
                | Math.round(blue * 255.0f);
    }
}
