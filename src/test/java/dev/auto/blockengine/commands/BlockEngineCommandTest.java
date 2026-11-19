package dev.auto.blockengine.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockEngineCommandTest {
    @Test
    void buildsMainMenuSummaryWithRuntimeStatistics() {
        // Run the formerly failing component assembly against the supported Paper runtime API.
        Component summary = assertDoesNotThrow(() -> BlockEngineCommand.infoSummary("1.0.7-RELEASE", 42, 3, 2));
        String text = PlainTextComponentSerializer.plainText().serialize(summary);

        assertTrue(text.contains("Version: 1.0.7-RELEASE"));
        assertTrue(text.contains("Registered blocks: 42"));
        assertTrue(text.contains("Loaded namespaces: 3"));
        assertTrue(text.contains("Generated resource packs: 2"));
        assertTrue(text.contains("Start Here"));
        assertTrue(text.contains("Project"));
        assertTrue(text.contains("Commands"));
    }

    @Test
    void keepsDocumentationLinksAndCommandActions() {
        Component summary = BlockEngineCommand.infoSummary("test", 0, 0, 0);
        List<ClickEvent<?>> actions = descendants(summary)
                .map(Component::clickEvent)
                .filter(Objects::nonNull)
                .toList();

        assertEquals(List.of(
                ClickEvent.openUrl("https://autoyt.github.io/BlockEngine/"),
                ClickEvent.openUrl("https://autoyt.github.io/BlockEngine/concepts/how-it-works/"),
                ClickEvent.openUrl("https://autoyt.github.io/BlockEngine/guides/create-a-data-pack/"),
                ClickEvent.openUrl("https://autoyt.github.io/BlockEngine/guides/register-blocks-from-java/"),
                ClickEvent.openUrl("https://autoyt.github.io/BlockEngine/api/"),
                ClickEvent.openUrl("https://github.com/Autoyt/BlockEngine"),
                ClickEvent.openUrl("https://github.com/Autoyt/BlockEngine/releases"),
                ClickEvent.runCommand("/be catalog"),
                ClickEvent.runCommand("/be packs"),
                ClickEvent.runCommand("/be wand"),
                ClickEvent.runCommand("/be debug")
        ), actions);
    }

    private static Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.children().stream()
                .flatMap(BlockEngineCommandTest::descendants));
    }
}
