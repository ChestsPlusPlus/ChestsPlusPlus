package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class WorldListener implements Listener {

    private static boolean justSaved = false;

    @EventHandler
    public void onWorldSave(WorldSaveEvent event){
        if(!justSaved){
            Config.save();
            justSaved = true;
            new BukkitRunnable(){
                @Override
                public void run() {
                    justSaved = false;
                }
            }.runTaskLater(ChestsPlusPlus.PLUGIN,5);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event){
        Utils.removeEntities(event.getWorld());
    }
}
