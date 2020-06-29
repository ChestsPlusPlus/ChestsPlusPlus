package com.jamesdpeters.minecraft.chests.serialize;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.storage.AutoCraftingStorageType;
import com.jamesdpeters.minecraft.chests.storage.ChestLinkStorageType;
import com.jamesdpeters.minecraft.chests.storage.StorageType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Config {

    private static LinkedChest store;
    private static FileConfiguration config;

    /* ALL STORAGE TYPES */
    private static ChestLinkStorageType chestLinkStorageType;
    private static AutoCraftingStorageType autoCraftingStorageType;

    private static List<StorageType> storageTypes;

    public Config() {
        try {
            config = YamlConfiguration.loadConfiguration(new File("chests.yml"));
            store = (LinkedChest) config.get("chests++", new HashMap<String, HashMap<String, List<Location>>>());
        } catch (Exception e) {
            store = new LinkedChest();
            save();
        }
        chestLinkStorageType = new ChestLinkStorageType(store);
        autoCraftingStorageType = new AutoCraftingStorageType(store);

        //Add each storage type to a list.
        storageTypes = new ArrayList<>();
        storageTypes.add(chestLinkStorageType);
        storageTypes.add(autoCraftingStorageType);
    }

    public static void save() {
        if (config == null) config = new YamlConfiguration();
        config.set("chests++", store);
        try {
            config.save("chests.yml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveASync(){
        Bukkit.getScheduler().runTaskAsynchronously(ChestsPlusPlus.PLUGIN, Config::save);
    }

    public static AutoCraftingStorageType getAutoCraft() {
        return autoCraftingStorageType;
    }

    public static ChestLinkStorageType getChestLink() {
        return chestLinkStorageType;
    }

    public static List<StorageType> getStorageTypes(){
        return storageTypes;
    }

    //TODO This needs improving
    public static OfflinePlayer getOfflinePlayer(String name) {
        for (String uuid : store.chests.keySet()) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            if (offlinePlayer.getName() != null && offlinePlayer.getName().equals(name)) return offlinePlayer;
        }
        return null;
    }
}