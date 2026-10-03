package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.command.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/** Paper bootstrapper: registers the Brigadier command trees before the plugin is constructed. */
public final class ChestsPlusPlusBootstrap implements PluginBootstrap {

    private @Nullable ChestsPlusPlus plugin;

    @Override
    public void bootstrap(BootstrapContext context) {
        Commands commands = new Commands(() -> plugin, context.getPluginMeta().getVersion());
        context.getLifecycleManager()
                .registerEventHandler(LifecycleEvents.COMMANDS, event -> commands.register(event.registrar()));
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        ChestsPlusPlus instance = new ChestsPlusPlus();
        plugin = instance;
        return instance;
    }
}
