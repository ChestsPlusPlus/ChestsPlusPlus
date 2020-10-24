package com.jamesdpeters.minecraft.chests;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class Api {

    private static Plugin plugin;

    public static void register(Plugin plugin) {
        Api.plugin = plugin;
    }

    public static Plugin getPlugin() {
        return plugin;
    }

    public static NMSProvider getNMSProvider() {
        String packageName = NMSProvider.class.getPackage().getName();
        String nmsVersion = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        String nmsProvider = packageName + "." + nmsVersion + ".NMSProviderImpl";
        plugin.getLogger().info("Found API version: " + nmsVersion);
        try {
            return (NMSProvider) Class.forName(nmsProvider).newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            plugin.getLogger().warning("A valid server implementation wasn't found for: " + nmsVersion);
            plugin.getLogger().warning("You may be running an outdated version of the plugin or it needs to be updated to the latest version!");

            // Currently depends on NMS for any Crafting abilities
            throw new RuntimeException("Currently no NMS version support for "+nmsVersion);
        }
    }
}
