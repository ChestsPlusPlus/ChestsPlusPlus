package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.filters.Filter;
import com.jamesdpeters.minecraft.chests.filters.HopperFilter;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualInventoryHolder;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class Utils {

    public static void openChestInventory(Player player, ChestLinkStorage storage, Location openedChestLocation){
        //Check if all chests should perform open animation.
        if(Settings.isShouldAnimateAllChests()) {
            storage.getLocations().forEach(locationInfo -> {
                if (locationInfo.getLocation() != null) {
                    chestOpenAnimation(storage.getInventory(), locationInfo.getLocation());
                }
            });
        } else {
            chestOpenAnimation(storage.getInventory(), openedChestLocation);
        }
        player.openInventory(storage.getInventory());
    }

    private static void chestOpenAnimation(Inventory inventory, Location location){
        if (location != null) {
            Block block = location.getBlock();
            if (block.getState() instanceof Chest) {
                Chest chest = (Chest) block.getState();
//                ApiSpecific.getChestOpener().setLidOpen(inventory, chest, true);
                Bukkit.getScheduler().scheduleSyncDelayedTask(ChestsPlusPlus.PLUGIN,() -> ApiSpecific.getChestOpener().setLidOpen(inventory, chest, true),1);
            }
        }
    }

    public static void openChestInventory(Player player, Inventory inventory){
        VirtualInventoryHolder holder = (VirtualInventoryHolder) inventory.getHolder();
        if (holder != null) holder.onPlayerRemoteOpened(player.getUniqueId());
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN,0.5f,1f);
        player.openInventory(inventory);
    }

    public static void closeInventorySound(Player player, Inventory inventory){
        if(inventory.getLocation() != null) player.getWorld().playSound(inventory.getLocation(), Sound.BLOCK_CHEST_CLOSE,0.5f,1f);
        else player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE,0.5f,1f);
        //player.closeInventory();
    }

    public static ItemStack removeStackFromInventory(Inventory inventory, int amount, List<Filter> filters){
        ItemStack toRemove;
        for(int i=0; i<inventory.getContents().length;  i++){
            ItemStack stack = inventory.getItem(i);
            if((stack != null) && (HopperFilter.isInFilter(filters,stack))){
                toRemove = stack.clone();
                toRemove.setAmount(Math.min(stack.getAmount(),amount));
                stack.setAmount(stack.getAmount()-toRemove.getAmount());
                return toRemove;
            }
        }
        return null;
    }

    public static boolean moveToOtherInventory(Inventory from, int amount, Inventory to, List<Filter> filters){
        ItemStack removed = removeStackFromInventory(from,amount,filters);
        if(removed != null) {
            HashMap<Integer, ItemStack> leftOvers = to.addItem(removed);
            for (ItemStack leftOver : leftOvers.values()) {
                from.addItem(leftOver);
                if(removed.equals(leftOver)) return false;
            }
            return true;
        }
        return false;
    }

    public static boolean moveToOtherInventory(Inventory from, int amount, Inventory to) {
        return moveToOtherInventory(from,amount,to,null);
    }

    public static ItemStack getNamedItem(ItemStack item, String name){
        ItemMeta meta = item.getItemMeta();
        if(meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static List<String> getOnlinePlayers(){
        return getPlayersAsNameList(Bukkit.getOnlinePlayers());
    }

    public static List<String> getAllPlayers(){
        return getPlayersAsNameList(Arrays.asList(Bukkit.getOfflinePlayers()));
    }

    public static List<String> getPlayersAsNameList(Collection<? extends OfflinePlayer> players){
        List<String> arr = new ArrayList<>();
        for(OfflinePlayer player : players){
            arr.add(ChatColor.stripColor(player.getName()));
        }
        return arr;
    }

    public static String prettyPrintPlayers(ChatColor highlight, List<OfflinePlayer> players){
        String playerString = players.stream().map(OfflinePlayer::getName).collect(Collectors.joining(","));
        return highlight+"["+ChatColor.WHITE+playerString+highlight+"]";
    }

    /**
     * Gets the BlockFace of the block the player is currently targeting.
     *
     * @param player the player's whos targeted blocks BlockFace is to be checked.
     * @return the BlockFace of the targeted block, or null if the targeted block is non-occluding.
     */
    public static BlockFace getBlockFace(Player player) {
        List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, 100);
        if (lastTwoTargetBlocks.size() != 2 || !lastTwoTargetBlocks.get(1).getType().isOccluding()) return null;
        Block targetBlock = lastTwoTargetBlocks.get(1);
        Block adjacentBlock = lastTwoTargetBlocks.get(0);
        return targetBlock.getFace(adjacentBlock);
    }

    public static BlockFace getNearestBlockFace(Player player, Location blockPlaced){
        Vector blockCentre = blockPlaced.add(0.5, 0, 0.5).toVector().setY(0);
        Vector directionLoc = player.getEyeLocation().toVector().setY(0);
        double angle = Math.toDegrees(calculateXZAngle(blockCentre,directionLoc));
        if(angle <= 45 && angle > -45) return BlockFace.EAST;
        if(angle <= 135 && angle > 45) return BlockFace.SOUTH;
        if(angle <= -45 && angle > -135) return BlockFace.NORTH;
        if(angle <= -135 || angle > 135) return BlockFace.WEST;
        return null;
    }

    private static double calculateXZAngle(Vector origin, Vector to){
        Vector vec = to.subtract(origin);
        return Math.atan2(vec.getZ(), vec.getX());
    }

    public static boolean isSideFace(BlockFace face){
        if(face == BlockFace.NORTH) return true;
        if(face == BlockFace.EAST) return true;
        if(face == BlockFace.SOUTH) return true;
        return face == BlockFace.WEST;
    }

    public static Inventory copyInventory(Inventory inventory){
        Inventory tempInv;
        if(inventory.getType() != InventoryType.CHEST) {
            tempInv = Bukkit.createInventory(null, inventory.getType());
        } else {
            tempInv = Bukkit.createInventory(null, inventory.getSize());
        }
        tempInv.setContents(inventory.getContents());
        return tempInv;
    }

    /**
     * Removes all entities that contain a value of 1 under the Values.PluginKey key.
     */
    public static void removeEntities(){
        Bukkit.getServer().getWorlds().forEach(Utils::removeEntities);
    }

    public static void removeEntities(World world){
        world.getEntities().forEach(entity -> {
            Integer val = entity.getPersistentDataContainer().get(Values.PluginKey, PersistentDataType.INTEGER);
            if(val != null && val == 1) entity.remove();
        });
    }

    public static List<String> filterList(List<String> list, String phrase){
        return list.stream().filter(s -> s.contains(phrase)).collect(Collectors.toList());
    }
}
