package dev.auto.blockengine.api.inspection;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.plugin.ServicesManager;
import org.jetbrains.annotations.NotNull;

/**
 * Optional handler for {@code /be inspector}. The inspector add-on registers its handler with
 * Bukkit's {@link ServicesManager}. Its argument arrays begin after the {@code inspector} subcommand.
 */
public interface InspectorCommand extends BasicCommand {
    String PERMISSION = "blockengine.inspector";

    @Override
    void execute(@NotNull CommandSourceStack source, @NotNull String[] args);

    @Override
    default @NotNull String permission() {
        return PERMISSION;
    }
}
