package com.jamesdpeters.minecraft.chests;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public class Values {
    public final static String ChestLinkTag = "[ChestLink]";
    public final static String AutoCraftTag = "[AutoCraft]";

    public static String identifier(String identifier) {
        return "[" + identifier + "]";
    }

    public final NamespacedKey playerUUID;
    public final NamespacedKey PluginKey;
    public final NamespacedKey storageID;
    public final NamespacedKey hopperTicked;

    private static Values Instance;

    public Values(Plugin plugin) {
        playerUUID = new NamespacedKey(plugin, "playerUUID");
        PluginKey  = new NamespacedKey(plugin, "ChestsPlusPlus");
        storageID = new NamespacedKey(plugin, "storageID");
        hopperTicked = new NamespacedKey(plugin, "hopperTicked");
    }

    public static void init(Plugin plugin) {
        Instance = new Values(plugin);
    }

    public static Values Instance() {
        return Instance;
    }
}
