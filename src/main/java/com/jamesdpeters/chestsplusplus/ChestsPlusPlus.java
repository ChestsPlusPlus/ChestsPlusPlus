package com.jamesdpeters.chestsplusplus;

import org.bukkit.plugin.java.JavaPlugin;

/** Plugin entry point. Owns the enable/disable lifecycle; services are wired in from Phase 1. */
// Not final: MockBukkit loads plugins through a generated subclass.
public class ChestsPlusPlus extends JavaPlugin {

    @Override
    public void onEnable() {
        getSLF4JLogger().info("ChestsPlusPlus {} enabled", getPluginMeta().getVersion());
    }

    @Override
    public void onDisable() {
        getSLF4JLogger().info("ChestsPlusPlus disabled");
    }
}
