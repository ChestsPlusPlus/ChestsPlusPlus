package com.jamesdpeters.minecraft.chests.serialize;

import com.jamesdpeters.minecraft.chests.crafting.Crafting;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualCraftingHolder;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SerializableAs("AutoCraftingStorage")
public class AutoCraftingStorage implements ConfigurationSerializable  {

    private ArrayList<Location> locationsList;
    private OfflinePlayer player;
    private UUID playerUUID;
    private RecipeSerializable recipeSerializable;
    private String identifier;
    private VirtualCraftingHolder virtualCraftingHolder;

    private ArrayList<String> members; //Members UUID
    private List<OfflinePlayer> bukkitMembers;
    private boolean isPublic;

    private Inventory inventory;

    @Override
    public Map<String, Object> serialize() {
        LinkedHashMap<String, Object> hashMap = new LinkedHashMap<>();
        hashMap.put("recipe",recipeSerializable);
        hashMap.put("locations",locationsList);
        hashMap.put("playerUUID",playerUUID.toString());
        hashMap.put("identifier", identifier);
        hashMap.put("members", members);
        hashMap.put("isPublic", isPublic);
        return hashMap;
    }

    @SuppressWarnings("unchecked")
    public AutoCraftingStorage(Map<String,Object> map){
        recipeSerializable = (RecipeSerializable) map.get("recipe");
        locationsList = (ArrayList<Location>) map.get("locations");
        playerUUID = UUID.fromString((String) map.get("playerUUID"));
        identifier = (String) map.get("identifier");
        player = Bukkit.getOfflinePlayer(playerUUID);

        if(map.containsKey("isPublic")) isPublic = (boolean) map.get("isPublic");
        else isPublic = false;

        if(map.get("members") != null){
            members = (ArrayList<String>) map.get("members");
            bukkitMembers = new ArrayList<>();
            for(String uuid : members){
                bukkitMembers.add(Bukkit.getOfflinePlayer(UUID.fromString(uuid)));
            }
        }

        initInventory();
    }

    public AutoCraftingStorage(OfflinePlayer player, String identifier, Location location){
        locationsList = new ArrayList<>();
        locationsList.add(location);
        this.player = player;
        this.playerUUID = player.getUniqueId();
        this.identifier = identifier;
        this.isPublic = false;

        initInventory();
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

    public void setRecipe(Recipe recipe){
        if(recipe == null){
            recipeSerializable = null;
            return;
        }
        recipeSerializable = new RecipeSerializable(recipe);
    }

    public List<Location> getLocations() {
        return locationsList;
    }

    public OfflinePlayer getOwner() {
        return player;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public VirtualCraftingHolder getVirtualCraftingHolder() {
        return virtualCraftingHolder;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public AutoCraftingStorage setPublic(boolean aPublic) {
        isPublic = aPublic;
        return this;
    }

    private void initInventory(){
        virtualCraftingHolder = new VirtualCraftingHolder(this);
        inventory = virtualCraftingHolder.getInventory();

        if(recipeSerializable != null) {
            Recipe recipe = recipeSerializable.getRecipe();

            if (recipe instanceof ShapelessRecipe) {
                virtualCraftingHolder.setCrafting((ShapelessRecipe) recipe);
            }
            if (recipe instanceof ShapedRecipe) {
                virtualCraftingHolder.setCrafting((ShapedRecipe) recipe);
            }
        } else {
            virtualCraftingHolder.resetChoices();
        }

        virtualCraftingHolder.setUpdatingRecipe(false);
        virtualCraftingHolder.updateGUI();
    }

    public boolean addMember(OfflinePlayer player){
        if(player != null){
            if(members == null) members = new ArrayList<>();
            if(bukkitMembers == null) bukkitMembers = new ArrayList<>();
            String uuid = player.getUniqueId().toString();
            if(members.contains(uuid)) return false;
            members.add(uuid);
            bukkitMembers.add(player);
            return true;
        }
        return false;
    }

    public boolean removeMember(Player player){
        if(player != null){
            if(bukkitMembers != null) bukkitMembers.remove(player);
            if(members != null){
                members.remove(player.getUniqueId().toString());
                return true;
            }
        }
        return false;
    }

    public List<OfflinePlayer> getMembers(){
        return bukkitMembers;
    }

    public void rename(String newIdentifier){
        this.identifier = newIdentifier;
        initInventory();

        locationsList.forEach(location -> {
            Block block = location.getBlock();
            if(block.getType() == Material.CRAFTING_TABLE) {
                BlockFace signFace = Utils.getBlockFaceContaingSign(location);
                //If signFace is null the location no longer contains a sign.
                if(signFace != null) {
                    Block signBlock = block.getRelative(signFace);
                    Sign sign = (Sign) signBlock.getState();
                    sign.setLine(1, ChatColor.GREEN + ChatColor.stripColor("[" + newIdentifier + "]"));
                    sign.update();
                }
            }
        });
    }

}
