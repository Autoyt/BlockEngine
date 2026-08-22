package dev.auto.blockengine.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public final class PerfCommand implements BasicCommand {
    private final @NotNull DebugCommands debug;

    public PerfCommand(@NotNull DebugCommands debug) {
        this.debug = debug;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        debug.perfShortcut(source, args);
    }

    @Override
    public @Nullable String permission() {
        return "blockengine.debug";
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            return DebugCommands.matching(List.of("on", "off", "stop", "overall", "commands", "validation", "placement", "events", "chunk-save"), args.length == 0 ? "" : args[0]);
        }
        return List.of();
    }
}
