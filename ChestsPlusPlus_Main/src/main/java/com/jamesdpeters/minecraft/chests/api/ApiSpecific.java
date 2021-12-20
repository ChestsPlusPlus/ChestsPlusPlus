package com.jamesdpeters.minecraft.chests.api;

import com.jamesdpeters.minecraft.chests.Api;
import com.jamesdpeters.minecraft.chests.ChestOpener;
import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.NMSProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;


public class ApiSpecific {

    private static MaterialChecker materialChecker;
    private static ChestOpener chestOpener;
    private static NMSProvider nmsProvider;

    public static void init(Plugin plugin) {
        nmsProvider = Api.init(plugin, NMSProviderDefault::new);
        materialChecker = nmsProvider.getMaterialChecker();
        chestOpener = nmsProvider.getChestOpener();
    }

    public static MaterialChecker getMaterialChecker() {
        return materialChecker;
    }

    public static ChestOpener getChestOpener() {
        return chestOpener;
    }

    public static NMSProvider getNmsProvider() {
        return nmsProvider;
    }

    public static String getApiVersion() {
        return Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
    }
}
