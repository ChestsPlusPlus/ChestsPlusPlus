package com.jamesdpeters.minecraft.chests;

import org.bukkit.plugin.Plugin;

public class Api {

    private static Plugin plugin;

    public static void init(Plugin plugin) {
        Api.plugin = plugin;
        Values.init(plugin);
    }

    public static Plugin getPlugin() {
        return plugin;
    }


}
