package com.jamesdpeters.minecraft.chests.commands;

import com.jamesdpeters.minecraft.chests.inventories.ChestLinkMenu;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import com.jamesdpeters.minecraft.chests.sort.SortMethod;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RemoteChestCommand extends ServerCommand  {

    private enum OPTIONS {
        ADD("/chestlink add <group>", "Create/add a chest to a ChestLink group"),
        HELP("/chestlink help","List of commands and their uses!"),
        LIST("/chestlink list","Lists all ChestLinks that you own!"),
        MEMBER("/chestlink member [add/remove <group> <player>] or [list <group>]","Add, remove or list members of a group"),
        MENU("/chestlink menu","Open the ChestLink menu to display all groups!"),
        OPEN("/chestlink open <Group>","Open the inventory of a ChestLink group"),
        REMOVE("/chestlink remove <Group>", "Delete a ChestLink and drop its inventory at your feet!"),
        RENAME("/chestlink rename <group> <new-name>","Rename a ChestLink."),
        SETPUBLIC("/chestlink setpublic <group> <true/false>", "Set a ChestLink to be accessible by anyone."),
        SORT("/chestlink sort <group> <sort-method>","Set the sorting option for the given ChestLink.");


        String description, commandHelp;
        static List<String> valuesList;

        static {
            valuesList = Stream.of(OPTIONS.values()).map(OPTIONS::toString).collect(Collectors.toList());
        }

        OPTIONS( String commandHelp, String description){
            this.commandHelp = commandHelp;
            this.description = description;
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

    }

    @Override
    public String getCommandName() {
        return "chestlink";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player)){
            sender.sendMessage("Only a player can use this command");
            return false;
        }

        Player player = (Player) sender;
        if(args != null &&  args.length > 0) {
            switch (OPTIONS.valueOf(args[0].toUpperCase())){
                case HELP:
                    for(OPTIONS option : OPTIONS.values()){
                        if(!option.equals(OPTIONS.HELP)) {
                            player.sendMessage(ChatColor.RED + option.commandHelp);
                            player.sendMessage(ChatColor.WHITE + option.description);
                        }
                    }
                    return true;
                case ADD:
                    if(args.length > 1){
                        if(sender.hasPermission(Permissions.ADD)) {
                            Block targetBlock = player.getTargetBlockExact(5);
//                            if(!Utils.validateChestID(args[1])){
//                                Messages.INVALID_CHESTID(player);
//                                return true;
//                            }
                            if (targetBlock != null) Utils.createChestLink(player, targetBlock, args[1]);
                            else Messages.MUST_LOOK_AT_CHEST(player);
                            return true;
                        } else {
                            Messages.NO_PERMISSION(player);
                            return true;
                        }
                    } else {
                        player.sendMessage(ChatColor.RED+OPTIONS.ADD.commandHelp);
                        player.sendMessage(ChatColor.RED+OPTIONS.ADD.description);
                        return true;
                    }
                case OPEN:
                    if(args.length > 1){
                        if(sender.hasPermission(Permissions.OPEN)) {
                            InventoryStorage invs;
                            if(args[1].contains(":")){
                                invs = Config.getInventoryStorage(player,args[1]);
                            } else {
                                invs = Config.getInventoryStorage(player.getUniqueId(), args[1]);
                            }
                            if(invs != null) Utils.openInventory(player, invs.getInventory());
                            return true;
                        } else {
                            Messages.NO_PERMISSION(player);
                            return true;
                        }
                    } else {
                        player.sendMessage(ChatColor.RED+OPTIONS.OPEN.commandHelp);
                        player.sendMessage(ChatColor.RED+OPTIONS.OPEN.description);
                        return true;
                    }
                case MENU:
                    if(sender.hasPermission(Permissions.MENU)) {
                        ChestLinkMenu.getMenu(player).open(player);
                        return true;
                    } else {
                        Messages.NO_PERMISSION(player);
                        return true;
                    }
                case LIST:
                    Messages.LIST_CHESTLINKS(player);
                    return true;
                case REMOVE:
                    if(args.length > 1) {
                        if (sender.hasPermission(Permissions.REMOVE)) {
                            Config.removeChestLink(player, args[1]);
                            return true;
                        } else {
                            Messages.NO_PERMISSION(player);
                            return true;
                        }
                    } else {
                        player.sendMessage(ChatColor.RED+OPTIONS.REMOVE.commandHelp);
                        player.sendMessage(ChatColor.RED+OPTIONS.REMOVE.description);
                        return true;
                    }
                case SORT:
                    if(args.length > 1) {
                        if (sender.hasPermission(Permissions.SORT)) {
                            InventoryStorage storage = Config.getInventoryStorage(player.getUniqueId(),args[1]);
                            if(storage != null) {
                                storage.setSortMethod(Enum.valueOf(SortMethod.class, args[2]));
                                storage.sort();
                                Messages.SORT(player,storage);
                            }
                            return true;
                        } else {
                            Messages.NO_PERMISSION(player);
                            return true;
                        }
                    } else {
                        player.sendMessage(ChatColor.RED+OPTIONS.SORT.commandHelp);
                        player.sendMessage(ChatColor.RED+OPTIONS.SORT.description);
                        return true;
                    }
                case MEMBER:
                    if(args.length > 3){
                        if(sender.hasPermission(Permissions.MEMBER)){
                            if(args[1].equals("add")) {
                                Player toAdd = Bukkit.getPlayer(args[3]);
                                InventoryStorage storage = Config.getInventoryStorage(player.getUniqueId(), args[2]);
                                if (storage != null && storage.addMember(toAdd))
                                    Messages.ADDED_MEMBER(player, storage, args[3]);
                                else Messages.UNABLE_TO_ADD_MEMBER(player, args[3]);
                            } else if(args[1].equals("remove")){
                                Player toAdd = Bukkit.getPlayer(args[3]);
                                InventoryStorage storage = Config.getInventoryStorage(player.getUniqueId(),args[2]);
                                if(storage != null && storage.removeMember(toAdd))
                                    Messages.REMOVE_MEMBER(player, storage, args[3]);
                                else Messages.UNABLE_TO_REMOVE_MEMBER(player,args[3]);
                            } else {
                                player.sendMessage(ChatColor.RED+OPTIONS.MEMBER.commandHelp);
                                player.sendMessage(ChatColor.RED+OPTIONS.MEMBER.description);
                            }
                        } else {
                            Messages.NO_PERMISSION(player);
                        }
                        return true;
                    } else if(args.length > 2){
                        if(sender.hasPermission(Permissions.MEMBER)){
                            if(args[1].equals("list")) {
                                InventoryStorage storage = Config.getInventoryStorage(player.getUniqueId(), args[2]);
                                if(storage != null){
                                    Messages.LIST_MEMBERS(player,storage);
                                    return true;
                                }
                            }
                        }
                    } else {
                        player.sendMessage(ChatColor.RED+OPTIONS.MEMBER.commandHelp);
                        player.sendMessage(ChatColor.RED+OPTIONS.MEMBER.description);
                        return true;
                    }
                case SETPUBLIC: {
                    if (args.length > 2) {
                        InventoryStorage storage = Config.getInventoryStorage(player.getUniqueId(), args[1]);
                        if (storage != null) {
                            boolean setpublic = Boolean.parseBoolean(args[2]);
                            storage.setPublic(setpublic);
                            Messages.SET_PUBLIC(player, storage);
                            return true;
                        } else {
                            Bukkit.broadcastMessage("Storage null");
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + OPTIONS.SETPUBLIC.commandHelp);
                        player.sendMessage(ChatColor.RED + OPTIONS.SETPUBLIC.description);
                        return true;
                    }
                }
                case RENAME: {
                    if(args.length > 2){
                        String group = args[1];
                        String newIdentifier = args[2];
                        if(!Config.renameInventoryStorage(player,group,newIdentifier)){
                            player.sendMessage(ChatColor.RED + OPTIONS.RENAME.commandHelp);
                            player.sendMessage(ChatColor.RED + OPTIONS.RENAME.description);
                        }
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if((sender instanceof Player)) {
            Player player = (Player) sender;

            if (args.length == 1) {
                return OPTIONS.valuesList;
            }
            if(args.length == 2) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                        case ADD:
                        case OPEN:
                            return Utils.getInvetoryStorageOpenableList(player);
                        case REMOVE:
                        case SORT:
                        case RENAME:
                            return Utils.getInventoryStorageList(player);
                        case MEMBER:
                            return Arrays.asList("add","remove","list");
                    }
                } catch (IllegalArgumentException ignored) { }
            }
            if(args.length == 3) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                        case MEMBER:
                            return Utils.getInventoryStorageList(player);
                        case SORT:
                            return SortMethod.valuesList;
                    }
                } catch (IllegalArgumentException ignored) { }
            }
            if(args.length == 4) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                        case MEMBER:
                            return Utils.getOnlinePlayers();
                    }
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return null;
    }
}
