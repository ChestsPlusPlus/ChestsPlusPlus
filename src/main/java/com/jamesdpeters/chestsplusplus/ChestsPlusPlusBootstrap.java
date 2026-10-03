package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.command.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

/** Paper bootstrapper: registers the Brigadier command trees before the plugin is constructed. */
public final class ChestsPlusPlusBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        String version = context.getPluginMeta().getVersion();
        context.getLifecycleManager()
                .registerEventHandler(LifecycleEvents.COMMANDS, event -> Commands.register(event.registrar(), version));
    }
}
