package com.jamesdpeters.minecraft.chests.storage.autocraft;

import com.jamesdpeters.minecraft.chests.lang.Message;
import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.Values;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.ConfigStorage;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageInfo;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageMessages;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class AutoCraftingStorageType extends StorageType<AutoCraftingStorage> {

    private static final List<BlockFace> blockfaces = Arrays.asList(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    public AutoCraftingStorageType(ConfigStorage store) {
        super(store);
    }

    @Override
    public HashMap<String, HashMap<String, AutoCraftingStorage>> getStorageMap(ConfigStorage store) {
        return store.autocraftingtables;
    }

    @Override
    public AutoCraftingStorage createNewStorageInstance(OfflinePlayer player, String inventoryName, Location location, Location sign) {
        return new AutoCraftingStorage(player, inventoryName, location, sign);
    }

    @Override
    public boolean isValidBlockType(Block block) {
        return (block.getType() == Material.CRAFTING_TABLE);
    }

    @Override
    public void onSignRemoval(Block block) {
        //Don't need to do anything with the Crafting Table.
    }

    @Override
    public String getSignTag() {
        return Values.Instance().AutoCraftTag;
    }

    @Override
    public boolean hasPermissionToAdd(Player player) {
        return player.hasPermission(Permissions.AUTOCRAFT_ADD) && !Utils.isBlacklistedWorld(player.getWorld());
    }

    @Override
    public void createStorage(Player player, OfflinePlayer owner, Block block, String identifier, boolean requireSign) {
        if (isValidBlockType(block)) {
            BlockFace facing = Utils.getBlockFace(player);
            if (facing != null) {
                createStorageFacing(player, owner, block, identifier, facing, requireSign);
            }
        }
    }

    @Override
    public void createStorageFacing(Player player, OfflinePlayer owner, Block block, String identifier, BlockFace facing, boolean requireSign) {
        if (Utils.isSideFace(facing)) {
            Block toReplace = block.getRelative(facing);
            StorageInfo info = getStorageUtils().getStorageInfo(block.getLocation());
            if (info != null) {
                Messages.ALREADY_PART_OF_GROUP(player, "Crafting Table");
                return;
            }
            placeSign(block, toReplace, facing, player, owner, identifier, Values.Instance().AutoCraftTag, requireSign);
        }
    }

    @Override
    public BlockFace onStoragePlacedBlockFace(Player player, Block placed) {
        return Utils.getNearestBlockFace(player, placed.getLocation());
    }

    @Override
    public BlockFace getStorageFacing(Block block) {
        for (BlockFace face : blockfaces) {
            Block sign = block.getRelative(face);
            if (sign.getState() instanceof Sign) {
                StorageInfo<AutoCraftingStorage> info = Config.getAutoCraft().getStorageUtils().getStorageInfo((Sign) sign.getState());
                if (info != null) return face;
            }
        }
        return null;
    }

    @Override
    public List<BlockFace> getValidBlockFaces(Block block) {
        return blockfaces;
    }

    @Override
    public void validate(Block block) {
        //Doesn't do any validation.
    }

    @Override
    public StorageMessages getMessages() {
        return messages;
    }

    @Override
    public void onBlockRightClick(PlayerInteractEvent event) {
            // AutoCraft Check
            if (event.getClickedBlock() != null && isValidBlockType(event.getClickedBlock())) {
                Location location = event.getClickedBlock().getLocation();
                AutoCraftingStorage storage = getStorage(location);
                if (storage != null) {
                    event.setCancelled(true);
                    if (event.getPlayer().hasPermission(Permissions.AUTOCRAFT_OPEN) && storage.hasPermission(event.getPlayer())) {
                        event.getPlayer().openInventory(storage.getInventory());
                        storage.getVirtualCraftingHolder().startAnimation();
                    }
                }
            }
    }

    private static final AutoCraftMessages messages = new AutoCraftMessages();

    private static class AutoCraftMessages extends StorageMessages {

        @Override
        public String getStorageName() {
            return "AutoCrafter";
        }

        @Override
        public void invalidID(Player target) {
            target.sendMessage(ChatColor.RED + Message.INVALID_ID.getString(getStorageName()));
            target.sendMessage(ChatColor.RED + "/autocraft add <owner>:<group>");
        }

        @Override
        public void listStorageGroups(Player target) {
            target.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + Message.LIST_OF_AUTOCRAFTERS);
            for (AutoCraftingStorage storage : Config.getAutoCraft().getStorageMap(target.getUniqueId()).values()) {
                if (storage != null) {
                    target.sendMessage(ChatColor.GREEN + storage.getIdentifier() + ChatColor.WHITE);
                }
            }
        }

        @Override
        public void mustLookAtBlock(Player player) {
            player.sendMessage(ChatColor.RED + TAG + " " + Message.MUST_LOOK_AT_CRAFTING_TABLE);
        }

        @Override
        public void invalidSignPlacement(Player player) {
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + TAG + " " + Message.INVALID_AUTOCRAFTER);
        }
    }
}
