package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class TempListener implements Listener {

    public TempListener() {
        this(ChestsPlusPlus.PLUGIN);
    }

    public TempListener(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void done() {
        HandlerList.unregisterAll(this);
    }


}
