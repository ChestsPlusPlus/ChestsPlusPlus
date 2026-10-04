package com.jamesdpeters.chestsplusplus.testing;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.command.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MockBukkit does not run {@code PluginBootstrap}s (spike S5), so integration tests load this stand-in, which
 * registers the real command trees through the plugin lifecycle manager during {@code onEnable}.
 */
public class CommandHostPlugin extends JavaPlugin {

    public static final String TEST_VERSION = "3.0.0-test";

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> new Commands(() -> (ChestsPlusPlus) getServer().getPluginManager().getPlugin("ChestsPlusPlus"), TEST_VERSION)
                        .register(event.registrar()));
    }
}
