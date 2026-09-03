package dev.auto.blockengine.commands;

import dev.auto.blockengine.catalog.CatalogListeners;
import dev.auto.blockengine.chat.BlockEngineChat;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class CatalogCommand implements BasicCommand {
    @Override
    public void execute(CommandSourceStack source, String[] args) {
        var sender = source.getSender();
        if (!sender.hasPermission("blockengine.debug")) {
            BlockEngineChat.error(sender, "You don't have permission to use this command!");
            return;
        }
        if (!(sender instanceof Player player)) {
            BlockEngineChat.error(sender, "Only players can open the BlockEngine catalog.");
            return;
        }

        if (args.length >= 1 && (args[0].equalsIgnoreCase("sudo") || args[0].equalsIgnoreCase("structure"))) {
            if (!sender.hasPermission("blockengine.structure")) {
                BlockEngineChat.error(sender, "You don't have permission to open the BlockEngine structure catalog.");
                return;
            }
            CatalogListeners.openSudo(player);
            return;
        }
        if (!sender.hasPermission("blockengine.debug")) {
            BlockEngineChat.error(sender, "You don't have permission to use this command!");
            return;
        }
        CatalogListeners.open(player);
    }

    @Override
    public @Nullable String permission() {
        return null;
    }
}
