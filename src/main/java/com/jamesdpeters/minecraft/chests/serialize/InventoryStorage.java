package com.jamesdpeters.minecraft.chests.serialize;


import com.jamesdpeters.minecraft.chests.Config;
import com.jamesdpeters.minecraft.chests.Messages;
import com.jamesdpeters.minecraft.chests.Utils;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualInventoryHolder;
import com.jamesdpeters.minecraft.chests.runnables.VirtualChestToHopper;
import fr.minuskube.inv.ClickableItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Array;
import java.util.*;

public class InventoryStorage implements ConfigurationSerializable {

    Inventory inventory; //Old Inventory
    ArrayList<ItemStack> items;
    ArrayList<Location> locationsList;
    ArrayList<String> members; //Members UUID
    List<Player> bukkitMembers;
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
        hashMap.put("members", members);
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
        locationsList.removeAll(Collections.singletonList(null));

        playerUUID = UUID.fromString((String) map.get("playerUUID"));
        player = Bukkit.getOfflinePlayer(playerUUID).getPlayer();

        if(map.get("members") != null){
            members = (ArrayList<String>) map.get("members");
            bukkitMembers = new ArrayList<>();
            for(String uuid : members){
                bukkitMembers.add(Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getPlayer());
            }
        }

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

    public ItemStack getIventoryIcon(){
        ItemStack toReturn = null;
        for(ItemStack item : inventory.getContents()){
            if(item != null){
                toReturn = item.clone();
            }
        }
        if(toReturn == null) toReturn = new ItemStack(Material.CHEST);

        ItemMeta meta = toReturn.getItemMeta();
        if(meta != null) {
            meta.setDisplayName(ChatColor.BOLD + "" + ChatColor.GREEN + "" + getIdentifier());
            toReturn.setItemMeta(meta);
        }
        toReturn.setAmount(1);
        return toReturn;
    }

    public ClickableItem getClickableItem(Player player) {
        return ClickableItem.of(getIventoryIcon(), event -> {
            Utils.openInventory(player,getInventory());
        });
    }

    public boolean hasPermission(Player player){
        if(player.getUniqueId().equals(playerUUID)) return true;
        if(members != null) {
            for (String uuid : members) {
                if (player.getUniqueId().toString().equals(uuid)) return true;
            }
        }
        return false;
    }

    public boolean addMember(Player player){
        if(player != null){
            if(members == null) members = new ArrayList<>();
            if(bukkitMembers == null) bukkitMembers = new ArrayList<>();
            members.add(player.getUniqueId().toString());
            bukkitMembers.add(player);
            Config.save();
            return true;
        }
        return false;
    }

    public boolean removeMember(Player player){
        if(player != null){
            if(bukkitMembers != null) bukkitMembers.remove(player);
            if(members != null){
                members.remove(player.getUniqueId().toString());
                Config.save();
                return true;
            }
        }
        return false;
    }

    public List<Player> getMembers(){
        return bukkitMembers;
    }
}
