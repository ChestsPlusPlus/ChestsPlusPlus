package com.jamesdpeters.minecraft.chests;

import com.jamesdpeters.minecraft.chests.containers.ChestLinkInfo;
import com.jamesdpeters.minecraft.chests.runnables.ChestLinkVerifier;
import org.apache.commons.lang.StringUtils;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class Utils {

//    public static String[] getChestLinkInfo(String[] lines){
//        if(lines.length < 2){
//            return null;
//        }
//        if(lines[0].contains(Values.signTag)) {
//            String id = StringUtils.substringBetween(lines[1], "[", "]");
//            String username = ChatColor.stripColor(lines[2]);
//            return new String[]{id,username};
//        }
//        return null;
//    }

    public static ChestLinkInfo getChestLinkInfo(Location location){
        return getChestLinkInfo(location,null);
    }
    public static ChestLinkInfo getChestLinkInfo(Sign sign, Player player){ return getChestLinkInfo(sign,sign.getLines(),player);}

    public static ChestLinkInfo getChestLinkInfo(Sign sign, String[] lines, Player player){
        if(lines.length >= 2 && lines[0].contains(Values.signTag)) {
            String playerUUID = sign.getPersistentDataContainer().get(Values.playerUUID, PersistentDataType.STRING);
            String group = ChatColor.stripColor(StringUtils.substringBetween(lines[1], "[", "]"));
            if(playerUUID != null){
                return new ChestLinkInfo(playerUUID, group);
            }
            else if(player != null) return new ChestLinkInfo(player, group);
        }
        return null;
    }

    /**
     * Returns ChestLinkInfo for a sign.
     * @param location - Location of ChestLink to find.
     * @param player - Player that ChestLink belongs to if it doesn't already exist.
     * @return @{@link ChestLinkInfo}
     */
    public static ChestLinkInfo getChestLinkInfo(Location location, Player player){
        Block block = location.getBlock();
        if(block.getBlockData() instanceof Directional) {
            Directional chest = (Directional) block.getBlockData();
            BlockFace facing = chest.getFacing();
            Block sign = block.getRelative(facing);

            if (sign.getState() instanceof Sign) {
                Sign s = (Sign) sign.getState();
                return getChestLinkInfo(s,player);
            }
        }
        return null;
    }

    public static boolean isValidSignPosition(Location chestLocation){
        Block block = chestLocation.getBlock();
        if(block.getBlockData() instanceof Directional) {
            Directional chest = (Directional) block.getBlockData();
            BlockFace facing = chest.getFacing();
            Block sign = block.getRelative(facing);
            return (sign.getState() instanceof Sign);
        }
        return false;
    }

    public static String locationPrettyPrint(Location location){
        return "["+location.getX()+","+location.getY()+","+location.getZ()+"] in "+location.getWorld().getName();
    }

    public static void openInventory(Player player, Inventory inventory){
        if(inventory.getLocation() != null) player.getWorld().playSound(inventory.getLocation(), Sound.BLOCK_CHEST_OPEN,0.5f,1f);
        else player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN,0.5f,1f);
        player.openInventory(inventory);
    }

    public static void closeInventorySound(Player player, Inventory inventory){
        if(inventory.getLocation() != null) player.getWorld().playSound(inventory.getLocation(), Sound.BLOCK_CHEST_CLOSE,0.5f,1f);
        else player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE,0.5f,1f);
        //player.closeInventory();
    }

    public static ItemStack removeStackFromInventory(Inventory inventory, int amount, List<ItemStack> filters){
        ItemStack toRemove;
        for(int i=0; i<inventory.getContents().length;  i++){
            ItemStack stack = inventory.getItem(i);
            if((stack != null) && (isInFilter(filters,stack))){
                toRemove = stack.clone();
                toRemove.setAmount(Math.min(stack.getAmount(),amount));
                stack.setAmount(stack.getAmount()-toRemove.getAmount());
                return toRemove;
            }
        }
        return null;
    }

    public static boolean moveToOtherInventory(Inventory from, int amount, Inventory to, List<ItemStack> filters){
        ItemStack removed = removeStackFromInventory(from,amount,filters);
        if(removed != null) {
            HashMap<Integer, ItemStack> leftOvers = to.addItem(removed);
            for (ItemStack leftOver : leftOvers.values()) {
                from.addItem(leftOver);
            }
            return true;
        }
        return false;
    }

    public static boolean moveToOtherInventory(Inventory from, int amount, Inventory to) {
        return moveToOtherInventory(from,amount,to,null);
    }

        public static void createChestLink(Player player, Block block, String identifier){
        if(block.getState() instanceof Chest){
            new ChestLinkVerifier(block).withDelay(0).check();
            if(block.getBlockData() instanceof Directional) {
                Directional chest = (Directional) block.getBlockData();
                BlockFace facing = chest.getFacing();
                Block toReplace = block.getRelative(facing);

                if(toReplace.getType() == Material.AIR){
                    BlockState replacedBlockState = toReplace.getState();

                    if(player.getGameMode() == GameMode.SURVIVAL) {
                        if (player.getEquipment() != null) {
                            if (!Tag.SIGNS.isTagged(player.getEquipment().getItemInMainHand().getType())) {
                                Messages.MUST_HOLD_SIGN(player);
                                return;
                            }
                            player.getEquipment().getItemInMainHand().setAmount(player.getEquipment().getItemInMainHand().getAmount() - 1);
                        } else {
                            Messages.MUST_HOLD_SIGN(player);
                            return;
                        }
                    }

                    toReplace.setType(Material.OAK_WALL_SIGN);
                    Sign sign = (Sign) toReplace.getState();
                    WallSign signBlockData = (WallSign) sign.getBlockData();
                    signBlockData.setFacing(facing);
                    sign.setBlockData(signBlockData);
                    sign.update();

                    String[] lines = new String[4];
                    lines[0] = Values.signTag;
                    lines[1] = Values.identifier(identifier);

                    BlockPlaceEvent event = new BlockPlaceEvent(sign.getBlock(),replacedBlockState,block,new ItemStack(Material.AIR),player,true, EquipmentSlot.HAND);
                    ChestsPlusPlus.PLUGIN.getServer().getPluginManager().callEvent(event);
                    if(event.isCancelled()){
                        sign.setType(Material.AIR);
                        return;
                    }

                    SignChangeEvent signChangeEvent = new SignChangeEvent(sign.getBlock(),player,lines);
                    ChestsPlusPlus.PLUGIN.getServer().getPluginManager().callEvent(signChangeEvent);
                } else {
                    Messages.NO_SPACE_FOR_SIGN(player);
                }
            }
        }
    }

    public static List<ItemStack> hasFilter(Block block){
        List<ItemStack> filters = new ArrayList<>();
        addIfNotNull(filters,getFilter(block,1,0));
        addIfNotNull(filters,getFilter(block,-1,0));
        addIfNotNull(filters,getFilter(block,0,1));
        addIfNotNull(filters,getFilter(block,0,-1));
        return filters;
    }

    private static ItemStack getFilter(Block block, int xOffset, int zOffset){
        Block frame = block.getRelative(xOffset, 0,zOffset);
        if(frame.getState() instanceof ItemFrame){
            return ((ItemFrame) frame.getState()).getItem();
        }
        return null;
    }

    private static <T> void addIfNotNull(List<T> list, T element){
        if(element != null) list.add(element);
    }

    public static boolean isInFilter(List<ItemStack> filters, ItemStack item){
        if(filters == null) return true;
        if(filters.size() == 0) return true;
        for(ItemStack filter : filters){
            if(filter.isSimilar(item)) return true;
        }
        return false;
    }

    public static List<ItemStack> getHopperFilters(Block block){
        Collection<Entity> ent = block.getLocation().getWorld().getNearbyEntities(block.getLocation(),1.01,1.01,1.01);
        List<ItemStack> filters = new ArrayList<>();
        for(Entity frame : ent){
            if(frame instanceof ItemFrame){
                Block attachedBlock = frame.getLocation().getBlock().getRelative(((ItemFrame) frame).getAttachedFace());
                if(block.equals(attachedBlock)){
                    filters.add(((ItemFrame) frame).getItem());
                }
            }
        }
        return filters;
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

    public static List<String> getPlayersAsNameList(Collection<? extends Player> players){
        List<String> arr = new ArrayList<>();
        for(Player player : players){
            arr.add(ChatColor.stripColor(player.getDisplayName()));
        }
        return arr;
    }

    public static String prettyPrintPlayers(ChatColor highlight, List<Player> players){
        StringBuilder str = new StringBuilder();
        str.append(players.size());
        if(players.size() > 0) {
            str.append(" - " + highlight + "[");
            for (Player player : players) {
                str.append(ChatColor.WHITE + ChatColor.stripColor(player.getDisplayName())).append(", ");
            }
            str.delete(str.length() - 2, str.length());
            str.append(highlight + "]");
        }
        return str.toString();
    }
}
