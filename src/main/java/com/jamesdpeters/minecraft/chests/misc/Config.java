package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.containers.ChestLinkInfo;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import com.jamesdpeters.minecraft.chests.serialize.LinkedChest;
import org.bukkit.Location;
import org.bukkit.block.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Config {

    static LinkedChest store;

    public Config(){
        try {
            FileConfiguration configuration = YamlConfiguration.loadConfiguration(new File("chests.yml"));
            store = (LinkedChest) configuration.get("chests++", new HashMap<String, HashMap<String, List<Location>>>());
        } catch (Exception e){
            store = new LinkedChest();
            save();
        }
    }

    public static void save(){
        FileConfiguration config = new YamlConfiguration();

        config.set("chests++", store);
        try {
            config.save("chests.yml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static HashMap<String, InventoryStorage> getInventoryStorageMap(UUID playerUUID){
        String id = playerUUID.toString();
        if(store.chests.containsKey(id)){
            return store.chests.get(id);
        } else {
            HashMap<String, InventoryStorage> hashMap = new HashMap<>();
            store.chests.put(id, hashMap);
            return hashMap;
        }
    }

    public static InventoryStorage getInventoryStorage(UUID playerUUID, String identifier){
        HashMap<String, InventoryStorage> map = getInventoryStorageMap(playerUUID);
        return map.getOrDefault(identifier, null);
    }

    public static InventoryStorage getInventoryStorage(Location location){
        if(location != null) {
            Block block = location.getBlock();
            if (block.getState() instanceof Chest) {
                Chest chest = (Chest) block.getState();
                ChestLinkInfo info = Utils.getChestLinkInfo(chest.getLocation());
                if(info != null){
                    return info.getStorage();
                }
            }
        }
        return null;
    }

    public static void addChest(Player player, String identifier, Location chestLocation){
        //List of groups this player has.
        HashMap<String, InventoryStorage> map = getInventoryStorageMap(player.getUniqueId());

        //Get Inventory Storage for the given group or create it if it doesnt exist.
        if(!map.containsKey(identifier)){
            InventoryStorage storage = new InventoryStorage(player,identifier,chestLocation);
            map.put(identifier, storage);
        }
        InventoryStorage inventoryStorage = map.get(identifier);

        //Migrates that chest into InventoryStorage and if full drops it at the chest location.
        Chest chest = (Chest) chestLocation.getBlock().getState();
        boolean hasOverflow = false;
        for(ItemStack chestItem : chest.getInventory().getContents()) {
            if(chestItem != null) {
                HashMap<Integer, ItemStack> overflow = inventoryStorage.getInventory().addItem(chestItem);
                for (ItemStack item : overflow.values())
                    if (item != null){
                        player.getWorld().dropItemNaturally(chestLocation, item);
                        hasOverflow = true;
                    }
            }
        }
        if(hasOverflow) Messages.CHEST_HAD_OVERFLOW(player);
        chest.getInventory().clear();

        //If the location isn't already part of the system add it.
        if(!inventoryStorage.getLocations().contains(chestLocation)){
            inventoryStorage.getLocations().add(chestLocation);
        }
        save();
    }

    public static InventoryStorage removeChest(InventoryStorage storage, Location location){
        if(storage != null) {
            storage.getLocations().remove(location);
            if (storage.getLocations().size() == 0) {
                storage.dropInventory(location);
                getInventoryStorageMap(storage.getOwner().getUniqueId()).remove(storage.getIdentifier());
            }
            save();
            return storage;
        }
        return null;
    }

    public static void removeChestLink(Player player, String group){
        InventoryStorage storage = getInventoryStorage(player.getUniqueId(),group);
        if(storage != null) {
            storage.getLocations().forEach(location -> {
                if (location != null) {
                    Block block = location.getBlock();
                    block.breakNaturally();
                }
            });
            storage.dropInventory(player.getLocation());
            getInventoryStorageMap(player.getUniqueId()).remove(group);
            Messages.REMOVED_GROUP(player,group);
        } else {
            Messages.GROUP_DOESNT_EXIST(player,group);
        }

        save();
    }

    public static InventoryStorage removeChest(Player player, String identifier, Location chestLocation){
        return removeChest(getInventoryStorageMap(player.getUniqueId()).get(identifier),chestLocation);
    }

    public static InventoryStorage removeChest(Location chestLocation){
        InventoryStorage storage = getInventoryStorage(chestLocation);
        return removeChest(storage,chestLocation);
    }

    public static boolean setChests(Player player, String group, InventoryStorage storage){
        HashMap<String, InventoryStorage> groups = getInventoryStorageMap(player.getUniqueId());
        groups.put(group,storage);
        save();
        return true;
    }

    public static int getTotalChestLinks(){
        AtomicInteger total = new AtomicInteger();
        store.chests.forEach((s, InventoryStorageHashMap) -> {
            InventoryStorageHashMap.forEach((s1, storage) -> {
                if(storage != null) total.getAndIncrement();
            });
        });
        return total.get();
    }

}
