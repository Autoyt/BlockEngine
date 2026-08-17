package dev.auto.blockengine.commands;

import dev.auto.blockengine.catalog.CatelogListeners;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class CatelogCommand implements BasicCommand {
    @Override
    public void execute(CommandSourceStack source, String[] args) {
        var sender = source.getSender();
        if (!sender.hasPermission("blockengine.debug")) {
            sender.sendMessage("You don't have permission to use this command!");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the BlockEngine catelog.");
            return;
        }

        CatelogListeners.open(player);
    }

    @Override
    public @Nullable String permission() {
        return "blockengine.debug";
    }
}
