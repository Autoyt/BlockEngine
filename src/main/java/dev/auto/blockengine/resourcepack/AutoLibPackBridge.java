package dev.auto.blockengine.resourcepack;

import dev.auto.autolib.lifecycle.Registration;
import dev.auto.autolib.pack.CentralPackService;
import dev.auto.autolib.pack.PackContribution;
import dev.auto.autolib.pack.ResourcePackProvider;
import dev.auto.blockengine.Main;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

final class AutoLibPackBridge implements SharedResourcePacks, ResourcePackProvider {
    private final Main plugin;
    private final CentralPackService service;
    private final Registration registration;
    private BlockEnginePackSnapshot snapshot;

    private AutoLibPackBridge(Main plugin, CentralPackService service) {
        this.plugin = plugin;
        this.service = service;
        registration = service.register(plugin, this);
    }

    static @Nullable SharedResourcePacks connect(Main plugin) {
        CentralPackService service = plugin.getServer().getServicesManager().load(CentralPackService.class);
        if (service == null || service.apiVersion() != 1 || !service.available()) {
            return null;
        }
        return new AutoLibPackBridge(plugin, service);
    }

    @Override
    public boolean available() {
        return service.available();
    }

    @Override
    public boolean ready() {
        return snapshot != null;
    }

    @Override
    public List<PackContribution> resourcePacks() {
        BlockEnginePackSnapshot current = snapshot;
        return current == null ? List.of() : List.of(new PackContribution(
                "resources", current.revision(), current::readEntries, current.required()));
    }

    @Override
    public void publish(List<GeneratedPack> packs) throws IOException {
        snapshot = BlockEnginePackSnapshot.capture(packs);
        service.invalidate(plugin);
    }

    @Override
    public CompletionStage<?> resend(Player player) {
        return service.request(player, true);
    }

    @Override
    public String hostDownload(String id, Path zip) throws IOException {
        return service.hostDownload(plugin, id, zip).toString();
    }

    @Override
    public void close() {
        registration.close();
    }
}
