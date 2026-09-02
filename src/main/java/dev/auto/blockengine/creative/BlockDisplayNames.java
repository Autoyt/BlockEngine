package dev.auto.blockengine.creative;

import dev.auto.blockengine.types.BlockDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class BlockDisplayNames {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private BlockDisplayNames() {
    }

    public static @NotNull Component itemName(@Nullable String name, @NotNull BlockDefinition block) {
        if (name != null && !name.isBlank()) {
            return rich(name);
        }
        return Component.translatable(CreativeInventoryManager.blockTranslationKey(block.id()))
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static @NotNull Component rich(@NotNull String text) {
        return MINI.deserialize(text)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static @NotNull String plain(@Nullable String name, @NotNull BlockDefinition block) {
        if (name != null && !name.isBlank()) {
            return PLAIN.serialize(MINI.deserialize(name));
        }
        return fallback(block);
    }

    public static @NotNull String fallback(@NotNull BlockDefinition block) {
        return fallback(block.apiDefinition().name(), block.id());
    }

    public static @NotNull String fallback(@NotNull String path, @NotNull String fallback) {
        int slash = path.lastIndexOf('/');
        String base = slash == -1 ? path : path.substring(slash + 1);
        StringBuilder result = new StringBuilder();
        for (String part : base.split("[_\\-.]+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                result.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.isEmpty() ? fallback : result.toString();
    }
}
