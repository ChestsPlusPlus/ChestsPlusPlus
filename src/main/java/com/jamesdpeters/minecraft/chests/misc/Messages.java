package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
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

    public static void LIST_MEMBERS(Player target, InventoryStorage storage){
        if(storage.getMembers() != null){
            target.sendMessage(ChatColor.GREEN+"Members of group "+ChatColor.WHITE+storage.getIdentifier()+": "+Utils.prettyPrintPlayers(ChatColor.GREEN,storage.getMembers()));
        } else {
            target.sendMessage(ChatColor.YELLOW+"There are no additional members in the group: "+ChatColor.WHITE+storage.getIdentifier());
        }
    }

    public static void SET_PUBLIC(Player target, InventoryStorage storage){
        target.sendMessage(ChatColor.GREEN+"Publicity for ChestLink "+storage.getIdentifier()+" is set to: "+ChatColor.WHITE+storage.isPublic());
    }

    public static void LIST_CHESTLINKS(Player target){
        target.sendMessage(ChatColor.GREEN+""+ChatColor.BOLD+"List of your ChestLinks:");
        for(InventoryStorage storage : Config.getInventoryStorageMap(target.getUniqueId()).values()){
            if(storage != null){
                target.sendMessage(ChatColor.GREEN+storage.getIdentifier()+ChatColor.WHITE+" - "+storage.getTotalItems()+" items");
            }
        }
    }

    public static void INVALID_CHESTID(Player target){
        target.sendMessage(ChatColor.RED+"Invalid ChestLink ID! Must not contain a colon ':' unless you are referencing another players group that you are a member off");
        target.sendMessage(ChatColor.RED+"/chestlink add <owner>:<group>");
    }

    public static void SORT(Player target, InventoryStorage storage){
        target.sendMessage(ChatColor.GREEN+"Sort method for "+ChatColor.WHITE+storage.getIdentifier()+ChatColor.GREEN+" has been set to "+ChatColor.WHITE+storage.getSortMethod().toString());
    }

    public static void CANNOT_RENAME_ALREADY_EXISTS(Player target, String newidentifier){
        target.sendMessage(ChatColor.RED+"Error renaming chest! "+ChatColor.WHITE+newidentifier+ChatColor.RED+" already exists!");
    }

    public static void CANNOT_RENAME_GROUP_DOESNT_EXIST(Player target, String oldidentifier){
        target.sendMessage(ChatColor.RED+"Error renaming chest! "+ChatColor.WHITE+oldidentifier+ChatColor.RED+" doesn't exist!");
    }

    public static void OWNER_HAS_TOO_MANY_CHESTS(Player target, OfflinePlayer owner){
        target.sendMessage(ChatColor.RED+"Owner: "+ChatColor.WHITE+owner.getName()+ChatColor.RED+" has reached the limit of groups allowed!");
    }

    public static void ALREADY_PART_OF_GROUP(Player target, String type){
        target.sendMessage(ChatColor.RED+""+ChatColor.BOLD+TAG+" This "+type+" is already apart of a group!");
    }
}
