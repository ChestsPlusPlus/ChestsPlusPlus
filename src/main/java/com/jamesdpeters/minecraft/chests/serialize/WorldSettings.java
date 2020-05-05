package com.jamesdpeters.minecraft.chests.serialize;

import org.bukkit.configuration.ConfigurationSection;

public class WorldSettings{

    //Values
    private int ticksPerHopperTransfer, hopperAmount;

    public WorldSettings(ConfigurationSection settings){
        ticksPerHopperTransfer = settings.getInt("ticks-per.hopper-transfer");
        hopperAmount = settings.getInt("hopper-amount");
    }

    //Default class used as a fallback if Spigot isn't being used etc.
    public WorldSettings(){
        ticksPerHopperTransfer = 8;
        hopperAmount = 1;
    }

    public int getTicksPerHopperTransfer(){
        return ticksPerHopperTransfer;
    }
    public  int getHopperAmount() { return hopperAmount; }
}