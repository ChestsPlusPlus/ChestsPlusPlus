package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class Messages {

    private static String TAG = "[Chests++]";

    public static void CHEST_HAD_OVERFLOW(Player target){
        target.sendMessage(ChatColor.GOLD+TAG+" Chest item's wouldn't all fit into ChestLink!");
    }

    public static void MUST_HOLD_SIGN(Player target){
        target.sendMessage(ChatColor.RED+TAG+" You must be hold a sign to do that!");
    }

    public static void NO_PERMISSION(Player target){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" You don't have permission to do that!");
    }

    public static void SORT(Player target, ChestLinkStorage storage){
        target.sendMessage(ChatColor.GREEN+"Sort method for "+ChatColor.WHITE+storage.getIdentifier()+ChatColor.GREEN+" has been set to "+ChatColor.WHITE+storage.getSortMethod().toString());
    }

    public static void CANNOT_RENAME_ALREADY_EXISTS(Player target, String newidentifier){
        target.sendMessage(ChatColor.RED+"Error renaming group! "+ChatColor.WHITE+newidentifier+ChatColor.RED+" already exists!");
    }

    public static void CANNOT_RENAME_GROUP_DOESNT_EXIST(Player target, String oldidentifier){
        target.sendMessage(ChatColor.RED+"Error renaming group! "+ChatColor.WHITE+oldidentifier+ChatColor.RED+" doesn't exist!");
    }

    public static void OWNER_HAS_TOO_MANY_CHESTS(Player target, OfflinePlayer owner){
        target.sendMessage(ChatColor.RED+"Owner: "+ChatColor.WHITE+owner.getName()+ChatColor.RED+" has reached the limit of groups allowed!");
    }

    public static void ALREADY_PART_OF_GROUP(Player target, String type){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" This "+type+" is already apart of a group!");
    }
}
