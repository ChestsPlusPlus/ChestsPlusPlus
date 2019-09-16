package com.jamesdpeters.minecraft.chests;

import org.bukkit.NamespacedKey;

public class Values {
    public final static String signTag = "[ChestLink]";
    public static String identifier(String identifier){
        return "["+identifier+"]";
    }

    public final static NamespacedKey playerUUID = new NamespacedKey(ChestsPlusPlus.PLUGIN,"playerUUID");
}
