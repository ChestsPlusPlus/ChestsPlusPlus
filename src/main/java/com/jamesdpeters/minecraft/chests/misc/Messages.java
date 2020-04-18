package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class Messages {

    private static String TAG = "[Chests++]";

    public static void CHEST_REMOVED(Player target, String group, String player){
        target.sendMessage(ChatColor.RED+TAG+" Succesfully removed a chest from group: "+ChatColor.WHITE+group+ChatColor.RED+" for "+ChatColor.WHITE+player);
    }

    public static void CHEST_ADDED(Player target, String group, String player){
        target.sendMessage(ChatColor.GREEN+TAG+" Succesfully added a chest to group: "+ChatColor.WHITE+group+ChatColor.RED+" for "+ChatColor.WHITE+player);
    }

    public static void CHEST_HAD_OVERFLOW(Player target){
        target.sendMessage(ChatColor.GOLD+TAG+" Chest item's wouldn't all fit into ChestLink!");
    }

    public static void MUST_LOOK_AT_CHEST(Player target){
        target.sendMessage(ChatColor.RED+TAG+" You must be looking at the chest you want to ChestLink!");
    }

    public static void MUST_HOLD_SIGN(Player target){
        target.sendMessage(ChatColor.RED+TAG+" You must be hold a sign to do that!");
    }

    public static void NO_PERMISSION(Player target){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" You don't have permission to do that!");
    }

    public static void SIGN_FRONT_OF_CHEST(Player target){
        target.sendMessage(ChatColor.GOLD+""+ChatColor.BOLD+TAG+" You must place the sign on the front of the chest!");
    }

    public static void NO_SPACE_FOR_SIGN(Player target){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" There is no space to place a sign on the front of the chest there!");
    }

    public static void ADDED_MEMBER(Player target, InventoryStorage storage, String added){
        target.sendMessage(ChatColor.GREEN+""+ChatColor.BOLD+TAG+" Succesfully added "+ChatColor.WHITE+added+ChatColor.GREEN+" to group "+ChatColor.WHITE+storage.getIdentifier());
        target.sendMessage(ChatColor.GREEN+""+ChatColor.BOLD+TAG+" Current Members: "+Utils.prettyPrintPlayers(ChatColor.GREEN,storage.getMembers()));
    }

    public static void UNABLE_TO_ADD_MEMBER(Player target, String toAdd){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" Unable to add player "+toAdd+" to ChestLink!");
    }

    public static void REMOVE_MEMBER(Player target, InventoryStorage storage, String removed){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" Succesfully removed "+ChatColor.WHITE+removed+ChatColor.RED+" from group "+ChatColor.WHITE+storage.getIdentifier());
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" Current Members: "+Utils.prettyPrintPlayers(ChatColor.RED,storage.getMembers()));
    }

    public static void UNABLE_TO_REMOVE_MEMBER(Player target, String toAdd){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" Unable to remove player "+toAdd+" from ChestLink! Were they already removed?");
    }

    public static void REMOVED_GROUP(Player target, String toRemove){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" Succesfully removed group "+toRemove+" from your ChestLink!");
    }

    public static void GROUP_DOESNT_EXIST(Player target, String toRemove){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" "+toRemove+" isn't a valid group to remove!");
    }
}
