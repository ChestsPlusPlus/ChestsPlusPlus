package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.containers.ChestLinkInfo;
import com.jamesdpeters.minecraft.chests.filters.Filter;
import com.jamesdpeters.minecraft.chests.filters.HopperFilter;
import com.jamesdpeters.minecraft.chests.runnables.ChestLinkVerifier;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.apache.commons.lang.StringUtils;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

public class Utils {

    public static ChestLinkInfo getChestLinkInfo(Sign sign){ return getChestLinkInfo(sign,sign.getLines());}

    public static ChestLinkInfo getChestLinkInfo(Sign sign, String[] lines){
        return getChestLinkInfo(sign, lines,null);
    }

    public static ChestLinkInfo getChestLinkInfo(Sign sign, String[] lines, UUID uuid){
        if(lines != null) {
            if (lines.length >= 2 && lines[0].contains(Values.signTag)) {
                String playerUUID = sign.getPersistentDataContainer().get(Values.playerUUID, PersistentDataType.STRING);
                String group = ChatColor.stripColor(StringUtils.substringBetween(lines[1], "[", "]"));
                if(playerUUID == null){
                    playerUUID = uuid.toString();
                    if(lines[2] != null){
                        OfflinePlayer owner = Config.getOfflinePlayer(lines[2]);
                        if(owner != null){
                            InventoryStorage storage = Config.getInventoryStorage(owner.getUniqueId(),group);
                            if(storage.hasPermission(Bukkit.getPlayer(uuid))) playerUUID = owner.getUniqueId().toString();
                        }
                    }
                }
                return new ChestLinkInfo(playerUUID, group);
            }
        }
        return null;
    }

    /**
     * Returns ChestLinkInfo for a sign.
     * @param location - Location of ChestLink to find.
     * @return @{@link ChestLinkInfo}
     */
    public static ChestLinkInfo getChestLinkInfo(Location location){
        Block block = location.getBlock();
        if(block.getBlockData() instanceof Directional) {
            Directional chest = (Directional) block.getBlockData();
            BlockFace facing = chest.getFacing();
            Block sign = block.getRelative(facing);

            if (sign.getState() instanceof Sign) {
                Sign s = (Sign) sign.getState();
                return getChestLinkInfo(s);
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

                    if(player.getGameMode() != GameMode.CREATIVE) {
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

                    String uuid, group, owner = null;
                    if(identifier.contains(":")){
                        String[] args = identifier.split(":");
                        owner = args[0];
                        group = args[1];
                        OfflinePlayer ownerPlayer = Config.getOfflinePlayer(owner);
                        if(ownerPlayer != null){
                            uuid = ownerPlayer.getUniqueId().toString();
                        } else {
                            Messages.INVALID_CHESTID(player);
                            return;
                        }
                    } else {
                        group = identifier;
                        uuid = player.getUniqueId().toString();
                    }


                    String[] lines = new String[4];
                    lines[0] = Values.signTag;
                    lines[1] = Values.identifier(group);
                    if(owner != null) {
                        lines[2] = owner;
                    }

                    toReplace.setType(Material.OAK_WALL_SIGN);
                    Sign sign = (Sign) toReplace.getState();
                    WallSign signBlockData = (WallSign) sign.getBlockData();
                    signBlockData.setFacing(facing);
                    sign.setBlockData(signBlockData);
                    sign.getPersistentDataContainer().set(Values.playerUUID, PersistentDataType.STRING, uuid);
                    sign.update();

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

//    public static List<ItemStack> hasFilter(Block block){
//        List<ItemStack> filters = new ArrayList<>();
//        addIfNotNull(filters,getFilter(block,1,0));
//        addIfNotNull(filters,getFilter(block,-1,0));
//        addIfNotNull(filters,getFilter(block,0,1));
//        addIfNotNull(filters,getFilter(block,0,-1));
//        return filters;
//    }
//
//    private static ItemStack getFilter(Block block, int xOffset, int zOffset){
//        Block frame = block.getRelative(xOffset, 0,zOffset);
//        if(frame.getState() instanceof ItemFrame){
//            return ((ItemFrame) frame.getState()).getItem();
//        }
//        return null;
//    }
//
//    private static <T> void addIfNotNull(List<T> list, T element){
//        if(element != null) list.add(element);
//    }

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
//
//        StringBuilder str = new StringBuilder();
//        str.append(players.size());
//        if(players.size() > 0) {
//            str.append(" - " + highlight + "[");
//            for (OfflinePlayer player : players) {
//                str.append(ChatColor.WHITE + ChatColor.stripColor(player.getName())).append(", ");
//            }
//            str.delete(str.length() - 2, str.length());
//            str.append(highlight + "]");
//        }
//        return str.toString();
    }

    public static List<String> getInventoryStorageList(Player player){
        return Config.getInventoryStorageMap(player.getUniqueId()).values().stream().map(InventoryStorage::getIdentifier).collect(Collectors.toList());
    }

    public static List<String> getInvetoryStorageOpenableList(Player player){
        List<String> playerList = getInventoryStorageList(player);
        List<String> memberList = Config.getInventoryStorageMemberOf(player).stream().map(storage -> storage.getOwner().getName()+":"+storage.getIdentifier()).collect(Collectors.toList());
        playerList.addAll(memberList);
        return playerList;
    }

    public static boolean validateChestID(String id){
        return !id.contains(":");
    }

}
