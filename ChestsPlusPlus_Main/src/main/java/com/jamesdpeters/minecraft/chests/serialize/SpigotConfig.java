package com.jamesdpeters.minecraft.chests.serialize;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;

public class SpigotConfig {

    private static HashMap<String,WorldSettings> worlds;
    private static WorldSettings default_;

    public static void load(JavaPlugin plugin){
        worlds = new HashMap<>();
        try {
            //Check if we're using the Spigot API.
            Class.forName("org.spigotmc.SpigotConfig");
            ConfigurationSection worldSettings = plugin.getServer().spigot().getConfig().getConfigurationSection("world-settings");
            if(worldSettings != null) {
                worldSettings.getValues(false).forEach((worldName, o) -> {
                    ConfigurationSection worldSetting = worldSettings.getConfigurationSection(worldName);
                    if (!worldName.equals("default") && worldSetting != null)
                        worlds.put(worldName, new WorldSettings(worldSetting));
                });
                ConfigurationSection section = worldSettings.getConfigurationSection("default");
                if(section != null) default_ = new WorldSettings(section);
            }
            if(default_ == null) default_ = new WorldSettings();
        } catch (ClassNotFoundException e){
            //Not using the Spigot API so fallback to defaults
            default_ = new WorldSettings();
        }
    }

    public static WorldSettings getDefault(){ return default_; }

    public static WorldSettings getWorldSettings(String worldName){
        return worlds.getOrDefault(worldName,default_);
    }

}
