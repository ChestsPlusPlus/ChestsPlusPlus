package com.jamesdpeters.minecraft.chests.storage.abstracts;

import com.jamesdpeters.minecraft.chests.lang.Message;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public abstract class StorageMessages {

    protected static String TAG = "[Chests++]";

    public abstract String getStorageName();

    public void storageAdded(Player target, String group, String player) {
        target.sendMessage(ChatColor.GREEN + TAG + " " + Message.STORAGE_ADDED.getString(getStorageName(), ChatColor.WHITE + group + ChatColor.GREEN, ChatColor.WHITE + player));
    }

    public void storageRemoved(Player target, String group, String player) {
        target.sendMessage(ChatColor.RED + TAG + " " + Message.STORAGE_REMOVED.getString(getStorageName(), ChatColor.WHITE + group + ChatColor.RED, ChatColor.WHITE + player));
    }

    public void removedGroup(Player target, String toRemove) {
        target.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + TAG + " " + Message.REMOVED_GROUP.getString(toRemove, getStorageName()));
    }

    public void groupDoesntExist(Player target, String toRemove) {
        target.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + TAG + " " + Message.GROUP_DOESNT_EXIST.getString(toRemove, getStorageName()));
    }

    public void foundUnlinkedStorage(Player target, String group) {
        target.sendMessage(ChatColor.GOLD + TAG + " " + Message.FOUND_UNLINKED_STORAGE.getString(getStorageName(), group));
    }

    public void addedMember(Player target, AbstractStorage storage, String added) {
        target.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + TAG + " " + Message.ADDED_MEMBER.getString(ChatColor.WHITE + added + ChatColor.GREEN, getStorageName(), ChatColor.WHITE + storage.getIdentifier()));
        target.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + TAG + " " + Message.CURRENT_MEMBERS.getString(Utils.prettyPrintPlayers(ChatColor.GREEN, storage.getMembers())));
    }

    public void addMemberToAll(Player target, OfflinePlayer added) {
        target.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + TAG + " " + Message.ADDED_MEMBER_TO_ALL.getString(ChatColor.WHITE + added.getName() + ChatColor.GREEN, getStorageName()));
    }

    public void unableToAddMember(Player target, String toAdd) {
        target.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + TAG + " " + Message.UNABLE_TO_ADD_MEMBER_TO_ALL.getString(toAdd, getStorageName()));
    }

    public void removedMember(Player target, AbstractStorage storage, String added) {
        target.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + TAG + " " + Message.REMOVED_MEMBER.getString(ChatColor.WHITE + added + ChatColor.GREEN, getStorageName(), ChatColor.WHITE + storage.getIdentifier()));
        target.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + TAG + " " + Message.CURRENT_MEMBERS.getString(Utils.prettyPrintPlayers(ChatColor.GREEN, storage.getMembers())));
    }

    public void removeMemberFromAll(Player target, OfflinePlayer added) {
        target.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + TAG + " " + Message.REMOVE_MEMBER_FROM_ALL.getString(ChatColor.WHITE + added.getName() + ChatColor.GREEN, getStorageName()));
    }

    public void unableToRemoveMember(Player target, String toAdd) {
        target.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + TAG + " " + Message.UNABLE_TO_REMOVE_MEMBER.getString(toAdd, getStorageName()));
    }

    public void listMembers(Player target, AbstractStorage storage) {
        if (storage.getMembers() != null) {
            target.sendMessage(ChatColor.GREEN + Message.LIST_MEMBERS_OF_GROUP.getString(getStorageName(), ChatColor.WHITE + storage.getIdentifier(), Utils.prettyPrintPlayers(ChatColor.GREEN, storage.getMembers())));
        } else {
            target.sendMessage(ChatColor.YELLOW + Message.NO_ADDITIONAL_MEMBERS.getString(ChatColor.WHITE + storage.getIdentifier()));
        }
    }

    public void setPublic(Player target, AbstractStorage storage) {
        target.sendMessage(ChatColor.GREEN + Message.SET_PUBLICITY.getString(getStorageName(), storage.getIdentifier(), ChatColor.WHITE + "" + storage.isPublic()));
    }

    public abstract void invalidID(Player target);

    public abstract void listStorageGroups(Player target);

    public abstract void mustLookAtBlock(Player player);

    public abstract void invalidSignPlacement(Player player);

}
