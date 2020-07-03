package com.jamesdpeters.minecraft.chests;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class API {

    private static Plugin plugin;

    public static void register(Plugin plugin){
        API.plugin = plugin;
    }

    public static Plugin getPlugin(){
        return plugin;
    }
}
