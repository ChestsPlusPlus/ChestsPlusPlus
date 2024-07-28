package com.jamesdpeters.minecraft.chests;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;

public class Api {

    private static Plugin plugin;

    public static NMSProvider init(Plugin plugin) {
        Api.plugin = plugin;
        Values.init(plugin);
        NMSProvider nmsProvider = setupNMSProvider();
        if (nmsProvider == null) {
            plugin.getLogger().severe("Disabling plugin: Failed to initialize NMS provider");
            Bukkit.getPluginManager().disablePlugin(plugin);
        }

        return nmsProvider;
    }

    public static Plugin getPlugin() {
        return plugin;
    }

    private static NMSProvider setupNMSProvider() {
        String packageName = NMSProvider.class.getPackage().getName();
        String nmsVersion = VersionMatcher.match();
        String nmsProvider = packageName + "." + nmsVersion + ".NMSProviderImpl";
        plugin.getLogger().info("Found API version: " + nmsVersion);

        try {
            return (NMSProvider) Class.forName(nmsProvider).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            plugin.getLogger().severe("§c=======================================================");
            plugin.getLogger().severe("§cThis version is not supported. The plugin most likely needs updating! ");
            plugin.getLogger().severe("§c=======================================================");
            return null;
        }
    }
}
