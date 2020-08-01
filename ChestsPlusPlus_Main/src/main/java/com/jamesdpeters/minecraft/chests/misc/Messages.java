package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.lang.Message;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class Messages {

    private static final String TAG = "[Chests++]";

    public static void CHEST_HAD_OVERFLOW(Player target) {
        target.sendMessage(ChatColor.GOLD + TAG + " " + Message.CHEST_HAD_OVERFLOW.getString());
    }

    public static void MUST_HOLD_SIGN(Player target) {
        target.sendMessage(ChatColor.RED + TAG + " " + Message.MUST_HOLD_SIGN.getString());
    }

    public static void NO_PERMISSION(Player target) {
        target.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + TAG + " " + Message.NO_PERMISSION.getString());
    }

    public static void SORT(Player target, ChestLinkStorage storage) {
        target.sendMessage(ChatColor.GREEN + Message.SORT.getString(ChatColor.WHITE + storage.getIdentifier() + ChatColor.GREEN, ChatColor.WHITE + storage.getSortMethod().toString()));
    }

    public static void CANNOT_RENAME_GROUP_ALREADY_EXISTS(Player target, String newidentifier) {
        target.sendMessage(ChatColor.RED + Message.CANNOT_RENAME_GROUP_ALREADY_EXISTS.getString(ChatColor.WHITE + newidentifier + ChatColor.RED));
    }

    public static void CANNOT_RENAME_GROUP_DOESNT_EXIST(Player target, String oldidentifier) {
        target.sendMessage(ChatColor.RED + Message.CANNOT_RENAME_GROUP_DOESNT_EXIST.getString(ChatColor.WHITE + oldidentifier + ChatColor.RED));
    }

    public static void OWNER_HAS_TOO_MANY_CHESTS(Player target, OfflinePlayer owner) {
        target.sendMessage(ChatColor.RED + Message.OWNER_HAS_TOO_MANY_CHESTS.getString(ChatColor.WHITE + owner.getName() + ChatColor.RED));
    }

    public static void ALREADY_PART_OF_GROUP(Player target, String type) {
        target.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + TAG + " " + Message.ALREADY_PART_OF_GROUP);
    }
}
