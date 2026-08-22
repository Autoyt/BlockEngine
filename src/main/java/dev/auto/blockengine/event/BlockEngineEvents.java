package dev.auto.blockengine.event;

import dev.auto.blockengine.api.event.BlockEngineModificationEvent;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BlockEngineEvents {
    private BlockEngineEvents() {
    }

    public static void call(@NotNull Event event) {
        Bukkit.getPluginManager().callEvent(event);
    }

    public static boolean callCancellable(@NotNull Event event) {
        call(event);
        return event instanceof Cancellable cancellable && cancellable.isCancelled();
    }

    public static void modification(
            @NotNull BlockEngineModificationEvent.Action action,
            @NotNull Block block,
            @Nullable String previousBlockId,
            @Nullable String previousStateId,
            @Nullable String newBlockId,
            @Nullable String newStateId
    ) {
        call(new BlockEngineModificationEvent(
                action,
                block,
                previousBlockId,
                previousStateId,
                newBlockId,
                newStateId
        ));
    }
}
