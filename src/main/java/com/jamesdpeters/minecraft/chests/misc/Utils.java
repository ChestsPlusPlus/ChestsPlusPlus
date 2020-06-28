package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.containers.AutoCraftInfo;
import com.jamesdpeters.minecraft.chests.containers.ChestLinkInfo;
import com.jamesdpeters.minecraft.chests.filters.Filter;
import com.jamesdpeters.minecraft.chests.filters.HopperFilter;
import com.jamesdpeters.minecraft.chests.runnables.ChestLinkVerifier;
import com.jamesdpeters.minecraft.chests.serialize.AutoCraftingStorage;
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
import org.bukkit.event.inventory.InventoryType;
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
            if (lines.length >= 2 && lines[0].contains(Values.ChestLinkTag)) {
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

    public static void openChestInventory(Player player, Inventory inventory){
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
                placeSign(block,toReplace,facing,player,identifier,Values.ChestLinkTag);
            }
        }
    }


    private static void placeSign(Block placedAgainst, Block toReplace, BlockFace facing, Player player, String identifier, String linkTag){
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
            lines[0] = linkTag;
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

            BlockPlaceEvent event = new BlockPlaceEvent(sign.getBlock(),replacedBlockState,placedAgainst,new ItemStack(Material.AIR),player,true, EquipmentSlot.HAND);
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
    }

    public static List<String> getAutoCraftStorageList(Player player){
        return Config.getAutoCraftTableMap(player.getUniqueId()).values().stream().map(AutoCraftingStorage::getIdentifier).collect(Collectors.toList());
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

    public static List<String> getAutoCraftStorageOpenableList(Player player){
        List<String> playerList = getAutoCraftStorageList(player);
        List<String> memberList = Config.getInventoryStorageMemberOf(player).stream().map(storage -> storage.getOwner().getName()+":"+storage.getIdentifier()).collect(Collectors.toList());
        playerList.addAll(memberList);
        return playerList;
    }

    /*
    AUTO CRAFT UTILS
     */

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

    public static boolean isSideFace(BlockFace face){
        if(face == BlockFace.NORTH) return true;
        if(face == BlockFace.EAST) return true;
        if(face == BlockFace.SOUTH) return true;
        return face == BlockFace.WEST;
    }

    public static void createAutoCraftChest(Player player, Block block, String identifier){
        if(block.getType() == Material.CRAFTING_TABLE){
            BlockFace facing = getBlockFace(player);
            if(facing != null) {
                if(isSideFace(facing)) {
                    Block toReplace = block.getRelative(facing);
                    AutoCraftInfo info = Utils.getAutoCraftInfo(block);
                    if(info != null){
                        Messages.ALREADY_PART_OF_GROUP(player,"Crafting Table");
                        return;
                    }
                    placeSign(block, toReplace, facing, player, identifier, Values.AutoCraftTag);
                }
            }
        }
    }

    public static AutoCraftInfo getAutoCraftInfo(Sign sign, String[] lines, UUID uuid){
        if(lines != null) {
            if (lines.length >= 2 && lines[0].contains(Values.AutoCraftTag)) {
                String playerUUID = sign.getPersistentDataContainer().get(Values.playerUUID, PersistentDataType.STRING);
                String group = ChatColor.stripColor(StringUtils.substringBetween(lines[1], "[", "]"));
                if(playerUUID == null){
                    playerUUID = uuid.toString();
                    if(lines[2] != null){
                        OfflinePlayer owner = Config.getOfflinePlayer(lines[2]);
                        if(owner != null){
                            AutoCraftingStorage storage = Config.getAutoCraftStorage(owner.getUniqueId(),group);
                            if(storage.hasPermission(Bukkit.getPlayer(uuid))) playerUUID = owner.getUniqueId().toString();
                        }
                    }
                }
                return new AutoCraftInfo(playerUUID, group);
            }
        }
        return null;
    }

    public static AutoCraftInfo getAutoCraftInfo(Sign sign, String[] lines){
        return getAutoCraftInfo(sign, lines,null);
    }

    private static final BlockFace[] blockfaces = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    public static BlockFace getBlockFaceContaingSign(Location location){
        Block block = location.getBlock();
        for(BlockFace face : blockfaces){
            AutoCraftInfo info = getAutoCraftInfoFromSign(block.getRelative(face));
            if(info != null) return face;
        }
        return null;
    }

    public static AutoCraftInfo getAutoCraftInfo(Block block){
        for(BlockFace face : blockfaces){
            Block relative = block.getRelative(face);
            if(relative.getBlockData() instanceof Directional){
                //Check if the sign is attached to the given block.
                Directional directional = (Directional) relative.getBlockData();
                if(directional.getFacing() != face) continue;
                //If it is we can extract info from it.
                AutoCraftInfo info = getAutoCraftInfoFromSign(block.getRelative(face));
                if(info != null) return info;
            }
        }
        return null;
    }

    private static AutoCraftInfo getAutoCraftInfoFromSign(Block sign){
        if(sign.getState() instanceof Sign){
            Sign s = (Sign) sign.getState();
            return getAutoCraftInfo(s);
        }
        return null;
    }

    public static AutoCraftInfo getAutoCraftInfo(Sign sign){ return getAutoCraftInfo(sign,sign.getLines());}

    /**
     * Checks if the block a sign is placed on is a Crafting Table and doesn't already belong to a group.
     * @param signLocation location of the Sign being placed
     * @return true if valid
     */
    public static boolean isValidAutoCraftSignPosition(Location signLocation){
        Block block = signLocation.getBlock();
        if(block.getBlockData() instanceof Directional) {
            Directional sign = (Directional) block.getBlockData();
            BlockFace facing = sign.getFacing().getOppositeFace();
            Block craftingTable = block.getRelative(facing);

            //Return if block isn't Crafting Table
            if(craftingTable.getType() != Material.CRAFTING_TABLE) return false;

            //Check if Crafting Table is already part of a group.
            AutoCraftInfo info = getAutoCraftInfo(craftingTable);
            return (info == null);
        }
        return false;
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

}
