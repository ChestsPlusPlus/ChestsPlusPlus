package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import org.bukkit.NamespacedKey;

public class Values {
    public final static String ChestLinkTag = "[ChestLink]";
    public final static String AutoCraftTag = "[AutoCraft]";

    public static String identifier(String identifier) {
        return "[" + identifier + "]";
    }

    public final static NamespacedKey playerUUID = new NamespacedKey(ChestsPlusPlus.PLUGIN, "playerUUID");
    public final static NamespacedKey PluginKey = new NamespacedKey(ChestsPlusPlus.PLUGIN, "ChestsPlusPlus");
    public final static NamespacedKey storageID = new NamespacedKey(ChestsPlusPlus.PLUGIN, "storageID");
}
