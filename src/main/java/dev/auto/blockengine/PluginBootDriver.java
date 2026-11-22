package dev.auto.blockengine;

import dev.auto.blockengine.creative.CreativeInventoryManager;
import dev.auto.blockengine.items.ItemManager;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import net.kyori.adventure.text.Component;

public class PluginBootDriver implements PluginBootstrap {

    @Override
    public void bootstrap(final BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose(), event -> {
            event.registry().register(
                    TypedKey.create(RegistryKey.ENCHANTMENT, CreativeInventoryManager.WAND_ENCHANTMENT),
                    builder -> CreativeInventoryManager.configureEnchantment(builder, Component.translatable(
                            CreativeInventoryManager.WAND_TRANSLATION_KEY, ItemManager.WAND_NAME))
            );

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
