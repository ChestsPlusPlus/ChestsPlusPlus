package com.jamesdpeters.minecraft.chests.misc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class Settings {

    private static String CHECK_UPDATE = "update-checker";
    private static String CHECK_UPDATE_PERIOD = "update-checker-period";

    private static Settings cf;
    private FileConfiguration configuration;
    private Plugin plugin;

    public static void initConfig(Plugin plugin){
        cf = new Settings();
        cf.plugin = plugin;
        cf.configuration = plugin.getConfig();

        //DEFAULT VALUES
        cf.configuration.addDefault(CHECK_UPDATE,true);
        cf.configuration.addDefault(CHECK_UPDATE_PERIOD,60*60);

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
    public static int getUpdateCheckerPeriodTicks() { return 20*cf.configuration.getInt(CHECK_UPDATE_PERIOD);}
}
