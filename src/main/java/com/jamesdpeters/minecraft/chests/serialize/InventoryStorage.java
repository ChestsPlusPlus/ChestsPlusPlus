package com.jamesdpeters.minecraft.chests.serialize;


import com.jamesdpeters.minecraft.chests.misc.Config;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualInventoryHolder;
import com.jamesdpeters.minecraft.chests.runnables.VirtualChestToHopper;
import com.jamesdpeters.minecraft.chests.sort.InventorySorter;
import com.jamesdpeters.minecraft.chests.sort.SortMethod;
import fr.minuskube.inv.ClickableItem;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.Directional;

import java.util.*;

public class InventoryStorage implements ConfigurationSerializable {

    Inventory inventory; //Old Inventory
    ArrayList<Location> locationsList;
    ArrayList<String> members; //Members UUID
    List<OfflinePlayer> bukkitMembers;
    String inventoryName = "Chest";
    VirtualChestToHopper chestToHopper;
    OfflinePlayer player;
    UUID playerUUID;
    boolean isPublic;
    SortMethod sortMethod = SortMethod.OFF;

    @Override
    public Map<String, Object> serialize() {
        LinkedHashMap<String, Object> hashMap = new LinkedHashMap<>();
        hashMap.put("inventory",inventory.getContents());
        hashMap.put("locations",locationsList);
        hashMap.put("inventoryName",inventoryName);
        hashMap.put("playerUUID",playerUUID.toString());
        hashMap.put("members", members);
        hashMap.put("isPublic", isPublic);
        hashMap.put("sortMethod", sortMethod.toString());
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
        player = Bukkit.getOfflinePlayer(playerUUID);

        if(map.containsKey("isPublic")) isPublic = (boolean) map.get("isPublic");
        if(map.containsKey("sortMethod")) sortMethod = Enum.valueOf(SortMethod.class, (String) map.get("sortMethod"));


        if(map.get("members") != null){
            members = (ArrayList<String>) map.get("members");
            bukkitMembers = new ArrayList<>();
            for(String uuid : members){
                bukkitMembers.add(Bukkit.getOfflinePlayer(UUID.fromString(uuid)));
            }
        }

        init();
    }

    public InventoryStorage(OfflinePlayer player, String group, Location location){
        this.inventoryName = group;
        this.player = player;
        this.playerUUID = player.getUniqueId();
        this.isPublic = false;
        this.sortMethod = SortMethod.OFF;
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

    public OfflinePlayer getOwner() {
        return player;
    }

    @Override
    public String toString() {
        return inventoryName+": "+locationsList.toString();
    }

    public ItemStack getIventoryIcon(Player player){
        ItemStack toReturn = null;
        for(ItemStack item : inventory.getContents()){
            if(item != null){
                toReturn = item.clone();
            }
        }
        if(toReturn == null) toReturn = new ItemStack(Material.CHEST);

        ItemMeta meta = toReturn.getItemMeta();
        if(meta != null) {
            String dispName = ChatColor.GREEN + "" + getIdentifier() + ": " +ChatColor.WHITE+ ""+getTotalItems()+" items";
            if(player.getUniqueId().equals(playerUUID)) meta.setDisplayName(dispName);
            else meta.setDisplayName(getOwner().getName()+": "+dispName);

            if(getMembers() != null) {
                List<String> memberNames = new ArrayList<>();
                if(isPublic) memberNames.add(ChatColor.WHITE+"Public Chest");
                memberNames.add(ChatColor.BOLD+""+ChatColor.UNDERLINE+"Members:");
                getMembers().forEach(player1 -> memberNames.add(ChatColor.stripColor(player1.getName())));
                meta.setLore(memberNames);
            }
            toReturn.setItemMeta(meta);
        }
        toReturn.setAmount(1);
        return toReturn;
    }

    public ClickableItem getClickableItem(Player player) {
        return ClickableItem.from(getIventoryIcon(player), event -> {
            Utils.openInventory(player,getInventory());
        });
    }

    public boolean hasPermission(Player player){
        if(isPublic) return true;
        if(player.hasPermission(Permissions.OPEN_ANY)) return true;
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

    public void rename(String newIdentifier){
        this.inventoryName = newIdentifier;
        ItemStack[] items = inventory.getContents();
        inventory = initInventory();
        inventory.setContents(items);
        locationsList.forEach(location -> {
            Block block = location.getBlock();
            if(block.getBlockData() instanceof org.bukkit.block.data.type.Chest) {
                org.bukkit.block.data.type.Chest chest = (org.bukkit.block.data.type.Chest) block.getBlockData();
                BlockFace blockFace = chest.getFacing();
                Block signBlock = block.getRelative(blockFace);
                Sign sign = (Sign) signBlock.getState();
                sign.setLine(1,ChatColor.GREEN + ChatColor.stripColor("[" + newIdentifier + "]"));
                sign.update();
            }
        });
    }

    public List<OfflinePlayer> getMembers(){
        return bukkitMembers;
    }

    public int getTotalItems(){
        int total = 0;
        if(inventory != null) {
            for(ItemStack itemStack : inventory.getContents()){
                if(itemStack != null) total += itemStack.getAmount();
            }
        }
        return total;
    }

    public void setPublic(boolean value){
        this.isPublic = value;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setSortMethod(SortMethod sortMethod){
        this.sortMethod = sortMethod;
    }

    public void sort(){
        InventorySorter.sort(inventory, sortMethod);
    }
}
