package com.jamesdpeters.minecraft.chests;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public class Settings {

    private static String CHECK_UPDATE = "update-checker";

    private static Settings cf;
    private FileConfiguration configuration;
    private Plugin plugin;

    public static void initConfig(Plugin plugin){
        cf = new Settings();
        cf.plugin = plugin;
        cf.configuration = plugin.getConfig();

        //DEFAULT VALUES
        cf.configuration.addDefault(CHECK_UPDATE,true);

        cf.configuration.options().copyDefaults(true);
        cf.plugin.saveConfig();
    }

    private static void save(){
        cf.plugin.saveConfig();;
    }

    public static void reloadConfig(){
        cf.configuration = cf.plugin.getConfig();
    }

    /**
     * GETTERS AND SETTERS
     */
    public static boolean isUpdateCheckEnabled() {
        return cf.configuration.getBoolean(CHECK_UPDATE);
    }
}
