package com.jamesdpeters.minecraft.chests;

import com.jamesdpeters.minecraft.chests.commands.RemoteChestCommand;
import com.jamesdpeters.minecraft.chests.listeners.ChestLinkListener;
import com.jamesdpeters.minecraft.chests.listeners.HopperListener;
import com.jamesdpeters.minecraft.chests.listeners.InventoryListener;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import com.jamesdpeters.minecraft.chests.serialize.LinkedChest;
import com.jamesdpeters.minecraft.chests.versionchecker.UpdateCheck;
import fr.minuskube.inv.InventoryManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public class ChestsPlusPlus extends JavaPlugin {

    public static JavaPlugin PLUGIN;
    public static InventoryManager INVENTORY_MANAGER;

    static {
        ConfigurationSerialization.registerClass(LinkedChest.class, "LinkedChest");
        ConfigurationSerialization.registerClass(InventoryStorage.class, "InventoryStorage");
    }

    @Override
    public void onEnable() {
        int pluginId = 7166;
        Metrics metrics = new Metrics(this, pluginId);

        Settings.initConfig(this);

        PLUGIN = this;
        new RemoteChestCommand().register(this);
        getServer().getPluginManager().registerEvents(new ChestLinkListener(),this);
        getServer().getPluginManager().registerEvents(new InventoryListener(),this);
        getServer().getPluginManager().registerEvents(new HopperListener(),this);

        new Config();

        INVENTORY_MANAGER = new InventoryManager(this);
        INVENTORY_MANAGER.init();

        if(Settings.isUpdateCheckEnabled()) {
            String SPIGOT_URL = "https://www.spigotmc.org/resources/chests-chest-linking-hopper-filtering-remote-chests-menus.71355/";
            UpdateCheck
                    .of(this)
                    .resourceId(71355)
                    .currentVersion("1.15 v1.2.2")
                    .handleResponse((versionResponse, version) -> {
                        switch (versionResponse) {
                            case FOUND_NEW:
                                getLogger().warning("New version of the plugin has been found: " + version);
                                getLogger().warning("Download at: https://www.spigotmc.org/resources/chests-chest-linking-hopper-filtering-remote-chests-menus.71355/");
                                Bukkit.broadcastMessage(ChatColor.RED + "[Chests++] New version of the plugin was found: " + version);
                                break;
                            case LATEST:
                                getLogger().info("Plugin is up to date! Thank you for supporting Chests++!");
                                break;
                            case UNAVAILABLE:
                                Bukkit.broadcastMessage("Unable to perform an update check.");
                        }
                    })
                    .check();
        }

        getLogger().info("Chests++ enabled!");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        Config.save();
    }

}
