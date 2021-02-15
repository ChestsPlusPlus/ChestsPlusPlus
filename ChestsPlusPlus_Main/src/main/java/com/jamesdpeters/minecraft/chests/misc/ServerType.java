package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import lombok.Getter;
import lombok.extern.java.Log;

@Log
public class ServerType {

    public enum Type {
        BUKKIT,
        SPIGOT,
        PAPER
    }

    @Getter
    private static Type type;

    public static void init() {
        //Default to Bukkit.
        type = Type.BUKKIT;

        try {
            Class.forName("org.spigotmc.SpigotConfig");
            // If reached here class exists
            type = Type.SPIGOT;
        } catch (Exception ignored){}

        try {
            Class.forName("com.destroystokyo.paper.VersionHistoryManager$VersionData");
            // If reached here class exists
            type = Type.PAPER;
        } catch (Exception ignored){}

        ChestsPlusPlus.PLUGIN.getLogger().info("Detected Server Type: "+getType());
    }
}
