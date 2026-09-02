package dev.auto.blockengine;

import dev.auto.blockengine.creative.CreativeInventoryManager;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;

public class PluginBootDriver implements PluginBootstrap {

    @Override
    public void bootstrap(final BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose(), event -> {
            for (CreativeInventoryManager.CreativeBlock block
                    : CreativeInventoryManager.bootstrapBlocks(context.getDataDirectory())) {
                event.registry().register(
                        CreativeInventoryManager.enchantmentTypedKey(block.id()),
                        builder -> CreativeInventoryManager.configureEnchantment(builder, block)
                );
            }
        });
    }
}
