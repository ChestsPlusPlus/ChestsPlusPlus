package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.containers.ChestLinkInfo;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import com.jamesdpeters.minecraft.chests.serialize.LinkedChest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Config {

    private static LinkedChest store;

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

    public static List<InventoryStorage> getInventoryStorageMemberOf(Player player){
        return store.chests.entrySet().stream().flatMap(map -> map.getValue().values().stream().filter(storage -> {
            if(storage.isPublic()) return false;
            if(storage.getOwner().getUniqueId().equals(player.getUniqueId())) return false;
            if(storage.getMembers() == null) return false;
            return storage.getMembers().stream().anyMatch(p -> p.getUniqueId().equals(player.getUniqueId()));
        })).collect(Collectors.toList());
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

    public static void addChest(Player player, String identifier, Location chestLocation, OfflinePlayer owner){
        //List of groups this player has.
        HashMap<String, InventoryStorage> map = getInventoryStorageMap(owner.getUniqueId());

        //Get Inventory Storage for the given group or create it if it doesnt exist.
        if(!map.containsKey(identifier)){
            InventoryStorage storage = new InventoryStorage(owner,identifier,chestLocation);
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
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP,1.0f,1f);
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

    public static InventoryStorage removeChest(OfflinePlayer owner, String identifier, Location chestLocation){
        return removeChest(getInventoryStorageMap(owner.getUniqueId()).get(identifier),chestLocation);
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

    public static InventoryStorage getInventoryStorage(Player member, String playerChestID){
        if(playerChestID.contains(":")) {
            String[] args = playerChestID.split(":");
            String playerName = args[0];
            String chestlinkID = args[1];
            Optional<InventoryStorage> invStorage = getInventoryStorageMemberOf(member).stream().filter(storage -> {
                if (storage.getOwner().getName().equals(playerName) && storage.getIdentifier().equals(chestlinkID)) return true;
                return false;
            }).findFirst();
            if(invStorage.isPresent()) return invStorage.get();
        }
        return null;
    }

    public static OfflinePlayer getOfflinePlayer(String name){
        for(String uuid : store.chests.keySet()){
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            if(offlinePlayer.getName() != null && offlinePlayer.getName().equals(name)) return offlinePlayer;
        }
        return null;
    }

    public static boolean renameInventoryStorage(Player player, String oldIdentifier, String newIdentifier){
        HashMap<String,InventoryStorage> map = getInventoryStorageMap(player.getUniqueId());
        if(!map.containsKey(oldIdentifier)){
            Messages.CANNOT_RENAME_GROUP_DOESNT_EXIST(player,oldIdentifier);
            return false;
        }
        if(map.containsKey(newIdentifier)){
            Messages.CANNOT_RENAME_ALREADY_EXISTS(player,newIdentifier);
            return false;
        }
        InventoryStorage storage = map.get(oldIdentifier);
        storage.rename(newIdentifier);
        map.remove(oldIdentifier);
        map.put(newIdentifier,storage);
        save();
        return true;
    }

}
