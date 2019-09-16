package com.jamesdpeters.minecraft.chests;

import com.jamesdpeters.minecraft.chests.commands.RemoteChestCommand;
import com.jamesdpeters.minecraft.chests.listeners.ChestLinkListener;
import com.jamesdpeters.minecraft.chests.listeners.HopperListener;
import com.jamesdpeters.minecraft.chests.listeners.InventoryListener;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import com.jamesdpeters.minecraft.chests.serialize.LinkedChest;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

public class ChestsPlusPlus extends JavaPlugin {

    public static JavaPlugin PLUGIN;

    static {
        ConfigurationSerialization.registerClass(LinkedChest.class, "LinkedChest");
        ConfigurationSerialization.registerClass(InventoryStorage.class, "InventoryStorage");
    }

    @Override
    public void onEnable() {
        PLUGIN = this;
        new RemoteChestCommand().register(this);
        getServer().getPluginManager().registerEvents(new ChestLinkListener(),this);
        getServer().getPluginManager().registerEvents(new InventoryListener(),this);
        getServer().getPluginManager().registerEvents(new HopperListener(),this);

        new Config();
        getLogger().info("Chests++ enabled!");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        Config.save();
    }

}
