package com.jamesdpeters.minecraft.chests;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class Messages {

    public static void CHEST_REMOVED(Player target, String group, String player){
        target.sendMessage(ChatColor.RED+"Succesfully removed a chest from group: "+group+" for "+player);
    }

    public static void CHEST_ADDED(Player target, String group, String player){
        target.sendMessage(ChatColor.GREEN+"Succesfully added a chest to group: "+group+" for "+player);
    }

    public static void CHEST_HAD_OVERFLOW(Player target){
        target.sendMessage(ChatColor.GOLD+"Chest item's wouldn't all fit into ChestLink!");
    }

    public static void MUST_LOOK_AT_CHEST(Player target){
        target.sendMessage(ChatColor.RED+"You must be looking at the chest you want to ChestLink!");
    }

    public static void MUST_HOLD_SIGN(Player target){
        target.sendMessage(ChatColor.RED+"You must be hold a sign to do that!");
    }

    public static void NO_PERMISSION(Player target){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+"You don't have permission to do that!");
    }
}
