package com.jamesdpeters.minecraft.chests;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;

public class Api {

    private static Plugin plugin;

    public static void register(Plugin plugin) {
        Api.plugin = plugin;
    }

    public static Plugin getPlugin() {
        return plugin;
    }

    public static NMSProvider setupNMSProvider() {
        String packageName = NMSProvider.class.getPackage().getName();
        String nmsVersion = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        String nmsProvider = packageName + "." + nmsVersion + ".NMSProviderImpl";
        plugin.getLogger().info("Found API version: " + nmsVersion);
        try {
            return (NMSProvider) Class.forName(nmsProvider).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            // Plugin now doesn't depend on NMS after 1.17.
            // So NMSProviderDefault is used for all versions 1.17+
            return null;
        }
    }
}
