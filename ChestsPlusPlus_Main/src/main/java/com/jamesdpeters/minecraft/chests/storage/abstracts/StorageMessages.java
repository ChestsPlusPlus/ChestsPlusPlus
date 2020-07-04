package com.jamesdpeters.minecraft.chests.storage.abstracts;

import com.jamesdpeters.minecraft.chests.misc.Utils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public abstract class StorageMessages {

    protected static String TAG = "[Chests++]";

    public abstract String getStorageName();

    public void storageAdded(Player target, String group, String player){
        target.sendMessage(ChatColor.GREEN+TAG+" Succesfully added "+getStorageName()+" to group: "+ChatColor.WHITE+group+ChatColor.RED+" for "+ChatColor.WHITE+player);
    }

    public void storageRemoved(Player target, String group, String player){
        target.sendMessage(ChatColor.RED+TAG+" Succesfully removed "+getStorageName()+" from group: "+ChatColor.WHITE+group+ChatColor.RED+" for "+ChatColor.WHITE+player);
    }

    public void removedGroup(Player target, String toRemove){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" Succesfully removed group "+toRemove+" from your "+getStorageName()+"'s!");
    }

    public void groupDoesntExist(Player target, String toRemove){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" "+toRemove+" isn't a valid "+getStorageName()+" group to remove!");
    }

    public void foundUnlinkedStorage(Player target, String group){
        target.sendMessage(ChatColor.GOLD+TAG+" This "+getStorageName()+" wasn't linked to your system! It has been added under the "+group+" group!");
    }

    public void addedMember(Player target, AbstractStorage storage, String added){
        target.sendMessage(ChatColor.GREEN+""+ChatColor.BOLD+TAG+" Succesfully added "+ChatColor.WHITE+added+ChatColor.GREEN+" to "+getStorageName()+" group "+ChatColor.WHITE+storage.getIdentifier());
        target.sendMessage(ChatColor.GREEN+""+ChatColor.BOLD+TAG+" Current Members: "+ Utils.prettyPrintPlayers(ChatColor.GREEN,storage.getMembers()));
    }

    public void unableToAddMember(Player target, String toAdd){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" Unable to add player "+toAdd+" to "+getStorageName()+"!");
    }

    public void removedMember(Player target, AbstractStorage storage, String added){
        target.sendMessage(ChatColor.GREEN+""+ChatColor.BOLD+TAG+" Succesfully removed "+ChatColor.WHITE+added+ChatColor.GREEN+" from "+getStorageName()+" group "+ChatColor.WHITE+storage.getIdentifier());
        target.sendMessage(ChatColor.GREEN+""+ChatColor.BOLD+TAG+" Current Members: "+ Utils.prettyPrintPlayers(ChatColor.GREEN,storage.getMembers()));
    }

    public void unableToRemoveMember(Player target, String toAdd){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" Unable to remove player "+toAdd+" from "+getStorageName()+"! Were they already removed?");
    }

    public void listMembers(Player target, AbstractStorage storage){
        if(storage.getMembers() != null){
            target.sendMessage(ChatColor.GREEN+"Members of "+getStorageName()+" group "+ChatColor.WHITE+storage.getIdentifier()+": "+Utils.prettyPrintPlayers(ChatColor.GREEN,storage.getMembers()));
        } else {
            target.sendMessage(ChatColor.YELLOW+"There are no additional members in the group: "+ChatColor.WHITE+storage.getIdentifier());
        }
    }

    public void setPublic(Player target, AbstractStorage storage){
        target.sendMessage(ChatColor.GREEN+"Publicity for "+getStorageName()+" group "+storage.getIdentifier()+" is set to: "+ChatColor.WHITE+storage.isPublic());
    }

    public abstract void invalidID(Player target);
    public abstract void listStorageGroups(Player target);
    public abstract void mustLookAtBlock(Player player);
    public abstract void invalidSignPlacement(Player player);

}
