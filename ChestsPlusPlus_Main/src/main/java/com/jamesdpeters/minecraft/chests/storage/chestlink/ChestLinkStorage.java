package com.jamesdpeters.minecraft.chests.storage.chestlink;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualInventoryHolder;
import com.jamesdpeters.minecraft.chests.menus.ChestLinkMenu;
import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.runnables.VirtualChestToHopper;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.PluginConfig;
import com.jamesdpeters.minecraft.chests.sort.InventorySorter;
import com.jamesdpeters.minecraft.chests.sort.SortMethod;
import com.jamesdpeters.minecraft.chests.storage.abstracts.AbstractStorage;
import fr.minuskube.inv.ClickableItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SerializableAs("ChestLinkStorage")
public class ChestLinkStorage extends AbstractStorage implements ConfigurationSerializable {

    private String inventoryName;
    private SortMethod sortMethod;

    public ChestLinkStorage(Map<String, Object> map) {
        super(map);
    }

    public ChestLinkStorage(OfflinePlayer player, String group, Location location, Location signLocation) {
        super(player, group, location, signLocation);
        this.inventoryName = group;
        this.sortMethod = SortMethod.OFF;

        Block block = location.getBlock();
        if (block.getState() instanceof Container container) {
            getInventory().setContents(container.getInventory().getContents());
            container.getInventory().clear();
            updateDisplayItem();
        }
        init();
    }

    @Override
    protected void serialize(Map<String, Object> hashMap) {
        hashMap.put("inventoryName", inventoryName);
        hashMap.put("sortMethod", sortMethod.toString());
    }

    @Override
    protected void deserialize(Map<String, Object> map) {
        String tempName = (String) map.get("inventoryName");
        if (tempName != null) inventoryName = tempName;

        if (map.containsKey("sortMethod")) sortMethod = Enum.valueOf(SortMethod.class, (String) map.get("sortMethod"));
        else sortMethod = SortMethod.OFF;

        init();
    }

    private void init() {
        VirtualChestToHopper chestToHopper = new VirtualChestToHopper(this);
        chestToHopper.start();
    }

    @Override
    public ChestLinkStorageType getStorageType() {
        return Config.getChestLink();
    }

    @Override
    public boolean storeInventory() {
        return true;
    }

    @Override
    protected Inventory initInventory() {
        return Bukkit.createInventory(new VirtualInventoryHolder(this), 54, inventoryName);
    }

    @Override
    protected void setIdentifier(String newName) {
        inventoryName = newName;
    }

    @Override
    public void onStorageAdded(Block block, Player player) {
        //Migrates that chest into InventoryStorage and if full drops it at the chest location.
        if (block.getState() instanceof Container chest) {
            boolean hasOverflow = false;
            for (ItemStack chestItem : chest.getInventory().getContents()) {
                if (chestItem != null) {
                    HashMap<Integer, ItemStack> overflow = getInventory().addItem(chestItem);
                    for (ItemStack item : overflow.values())
                        if (item != null) {
                            player.getWorld().dropItemNaturally(block.getLocation(), item);
                            hasOverflow = true;
                        }
                }
            }
            if (hasOverflow) Messages.CHEST_HAD_OVERFLOW(player);
            chest.getInventory().clear();
        }
    }

    public ItemStack getIventoryIcon(Player player) {
        ItemStack mostCommon = InventorySorter.getMostCommonItem(getInventory());
        ItemStack toReturn;
        if (mostCommon == null) toReturn = new ItemStack(Material.CHEST);
        else toReturn = mostCommon.clone();

        ItemMeta meta = toReturn.getItemMeta();
        if (meta != null) {
            String dispName = ChatColor.GREEN + "" + getIdentifier() + ": " + ChatColor.WHITE + "" + getTotalItems() + " items";
            if (player.getUniqueId().equals(getPlayerUUID())) meta.setDisplayName(dispName);
            else meta.setDisplayName(getOwner().getName() + ": " + dispName);

            if (getMembers() != null) {
                List<String> memberNames = new ArrayList<>();
                if (isPublic()) memberNames.add(ChatColor.WHITE + "Public Chest");
                memberNames.add(ChatColor.BOLD + "" + ChatColor.UNDERLINE + "Members:");
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
            InventoryHolder inventoryHolder = getInventory().getHolder();
            if (inventoryHolder instanceof VirtualInventoryHolder virtualInventoryHolder) {
                virtualInventoryHolder.setPreviousInventory(() ->
                        Bukkit.getScheduler().runTask(ChestsPlusPlus.PLUGIN, () -> ChestLinkMenu.getMenu(player).openLastPage(player)));
            }
            Utils.openChestInventory(player, getInventory());
        });
    }

    public int getTotalItems() {
        int total = 0;
        if (getInventory() != null) {
            for (ItemStack itemStack : getInventory().getContents()) {
                if (itemStack != null) total += itemStack.getAmount();
            }
        }
        return total;
    }

    public void setSortMethod(SortMethod sortMethod) {
        this.sortMethod = sortMethod;
    }

    public SortMethod getSortMethod() {
        return sortMethod;
    }

    public void sort() {
        ItemStack[] sortedInventory = InventorySorter.sort(getInventory(), sortMethod);
        getInventory().setContents(sortedInventory);
    }

    public void updateDisplayItem() {
        onItemDisplayUpdate(InventorySorter.getMostCommonItem(getInventory()));
    }

    @Override
    public String getIdentifier() {
        return inventoryName;
    }

    @Override
    public boolean shouldDisplayArmourStands() {
        return PluginConfig.DISPLAY_CHESTLINK_ARMOUR_STAND.get();
    }

    @Override
    public void postConfigLoad() {
        super.postConfigLoad();
        onItemDisplayUpdate(InventorySorter.getMostCommonItem(getInventory()));
    }

    @Override
    public boolean doesDropInventory() {
        return true;
    }

    @Override
    public double getBlockOffset(Block block) {
        if (block.getState() instanceof Chest) return 0;
            //Barrel is full block.
        else return -0.07;
    }

    @Override
    public String toString() {
        return inventoryName + ": " + getLocations().toString();
    }
}
