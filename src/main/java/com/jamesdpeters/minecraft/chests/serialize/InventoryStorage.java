package com.jamesdpeters.minecraft.chests.serialize;


import com.jamesdpeters.minecraft.chests.Messages;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualInventoryHolder;
import com.jamesdpeters.minecraft.chests.runnables.VirtualChestToHopper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Array;
import java.util.*;

public class InventoryStorage implements ConfigurationSerializable {

    Inventory inventory;
    ArrayList<Location> locationsList;
    String inventoryName = "Chest";
    VirtualChestToHopper chestToHopper;
    Player player;
    UUID playerUUID;

    @Override
    public Map<String, Object> serialize() {
        LinkedHashMap<String, Object> hashMap = new LinkedHashMap<>();
        hashMap.put("inventory",inventory.getContents());
        hashMap.put("locations",locationsList);
        hashMap.put("inventoryName",inventoryName);
        hashMap.put("playerUUID",playerUUID.toString());
        return hashMap;
    }

    @SuppressWarnings("unchecked")
    public InventoryStorage(Map<String, Object> map){
        String tempName = (String) map.get("inventoryName");
        if(tempName != null) inventoryName = tempName;

        inventory = initInventory();
        ItemStack[] itemStacks = ((ArrayList<ItemStack>) map.get("inventory")).toArray(new ItemStack[0]);

        inventory.setContents(itemStacks);
        locationsList = (ArrayList<Location>) map.get("locations");

        playerUUID = UUID.fromString((String) map.get("playerUUID"));
        player = Bukkit.getOfflinePlayer(playerUUID).getPlayer();

        init();
    }

    public InventoryStorage(Player player, String group, Location location){
        this.inventoryName = group;
        this.player = player;
        this.playerUUID = player.getUniqueId();
        locationsList = new ArrayList<>(Collections.singleton(location));

        Block block = location.getBlock();
        if(block.getState() instanceof Chest){
            Chest chest = (Chest) block.getState();
            inventory = initInventory();
            inventory.setContents(chest.getInventory().getContents());
            chest.getInventory().clear();
        }

        init();
    }

    private void init(){
        chestToHopper = new VirtualChestToHopper(this);
        chestToHopper.start();
    }

    private Inventory initInventory(){
        return Bukkit.createInventory(new VirtualInventoryHolder(this), 54,inventoryName);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public List<Location> getLocations() {
        return locationsList;
    }

    public void dropInventory(Location location){
        for(ItemStack item : inventory.getContents()) {
            if(location.getWorld() != null){
                if(item != null) {
                    location.getWorld().dropItemNaturally(location, item);
                    inventory.remove(item);
                }
            }
        }
    }

    public String getIdentifier() {
        return inventoryName;
    }

    public Player getOwner() {
        return player;
    }

    @Override
    public String toString() {
        return inventoryName+": "+locationsList.toString();
    }
}
