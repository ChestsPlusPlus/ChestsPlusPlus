package com.jamesdpeters.minecraft.chests.commands;

import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutoCraftCommand extends ServerCommand  {

    private enum OPTIONS {
        ADD("/autocraft add <group>", "Create/add a Crafting Table to an AutoCraft group"),
        HELP("/autocraft help","List of commands and their uses!"),
        LIST("/autocraft list","Lists all AutoCraft groups that you own!"),
        MEMBER("/autocraft member [add/remove <group> <player>] or [list <group>]","Add, remove or list members of a group"),
        MENU("/autocraft menu","Open the AutoCraft menu to display all groups!"),
        OPEN("/autocraft open <Group>","Open the workbench of an AutoCraft group"),
        REMOVE("/autocraft remove <Group>", "Delete an AutoCraft group and drop all the Crafting Tables!"),
        RENAME("/autocraft rename <group> <new-name>","Rename an AutoCraft group."),
        SETPUBLIC("/autocraft setpublic <group> <true/false>", "Set an AutoCraft group to be accessible by anyone.");

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
        return "autocraft";
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
                        if (player.hasPermission(Permissions.AUTOCRAFT_ADD)) {
                            Block targetBlock = player.getTargetBlockExact(5);
                            if (targetBlock != null) Utils.createAutoCraftChest(player, targetBlock, args[1]);
                            else Messages.MUST_LOOK_AT_CHEST(player);
                        } else {
                            Messages.NO_PERMISSION(player);
                        }
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED+ OPTIONS.ADD.commandHelp);
                        player.sendMessage(ChatColor.RED+ OPTIONS.ADD.description);
                        return true;
                    }
                case OPEN:
                    if(args.length > 1){
                        if(sender.hasPermission(Permissions.AUTOCRAFT_OPEN)) {
                            AutoCraftingStorage invs;
                            if(args[1].contains(":")){
                                invs = Config.getAutoCraftStorage(player,args[1]);
                            } else {
                                invs = Config.getAutoCraftStorage(player.getUniqueId(), args[1]);
                            }
                            if(invs != null) Utils.openInventory(player, invs.getInventory());
                            return true;
                        } else {
                            Messages.NO_PERMISSION(player);
                            return true;
                        }
                    } else {
                        player.sendMessage(ChatColor.RED+ OPTIONS.OPEN.commandHelp);
                        player.sendMessage(ChatColor.RED+ OPTIONS.OPEN.description);
                        return true;
                    }
                case MENU:
                    if(sender.hasPermission(Permissions.MENU)) {
                        //TODO Add AutoCraft menu.
                        player.sendMessage(ChatColor.GOLD+" Currently unimplemented in this BETA!");
                        //ChestLinkMenu.getMenu(player).open(player);
                        return true;
                    } else {
                        Messages.NO_PERMISSION(player);
                        return true;
                    }
                case LIST:
                    Messages.LIST_AUTOCRAFT(player);
                    return true;
                case REMOVE:
                    if(args.length > 1) {
                        if (sender.hasPermission(Permissions.AUTOCRAFT_REMOVE)) {
                            Config.removeAutoCraft(player, args[1]);
                            return true;
                        } else {
                            Messages.NO_PERMISSION(player);
                            return true;
                        }
                    } else {
                        player.sendMessage(ChatColor.RED+ OPTIONS.REMOVE.commandHelp);
                        player.sendMessage(ChatColor.RED+ OPTIONS.REMOVE.description);
                        return true;
                    }
                case MEMBER:
                   return memberCommand(args, sender);
                case SETPUBLIC: {
                    if (args.length > 2) {
                        AutoCraftingStorage storage = Config.getAutoCraftStorage(player.getUniqueId(), args[1]);
                        if (storage != null) {
                            boolean setPublic = Boolean.parseBoolean(args[2]);
                            storage.setPublic(setPublic);
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
                        if(!Config.renameAutoCraftStorage(player,group,newIdentifier)){
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
                            return Utils.getAutoCraftStorageOpenableList(player);
                        case REMOVE:
                        case RENAME:
                            return Utils.getAutoCraftStorageList(player);
                        case MEMBER:
                            return Arrays.asList("add","remove","list");
                    }
                } catch (IllegalArgumentException ignored) { }
            }
            if(args.length == 3) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                        case MEMBER:
                            return Utils.getAutoCraftStorageList(player);
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

    private static boolean memberCommand(String[] args, CommandSender sender){
        Player player = (Player) sender;
        if(args.length > 3){
            if(sender.hasPermission(Permissions.MEMBER)){
                if(args[1].equals("add")) {
                    Player toAdd = Bukkit.getPlayer(args[3]);
                    AutoCraftingStorage storage = Config.getAutoCraftStorage(player.getUniqueId(), args[2]);
                    if (storage != null && storage.addMember(toAdd))
                        Messages.ADDED_MEMBER(player, storage, args[3]);
                    else Messages.UNABLE_TO_ADD_MEMBER(player, args[3]);
                } else if(args[1].equals("remove")){
                    Player toAdd = Bukkit.getPlayer(args[3]);
                    AutoCraftingStorage storage = Config.getAutoCraftStorage(player.getUniqueId(), args[2]);
                    if(storage != null && storage.removeMember(toAdd))
                        Messages.REMOVE_MEMBER(player, storage, args[3]);
                    else Messages.UNABLE_TO_REMOVE_MEMBER(player,args[3]);
                } else {
                    player.sendMessage(ChatColor.RED+ OPTIONS.MEMBER.commandHelp);
                    player.sendMessage(ChatColor.RED+ OPTIONS.MEMBER.description);
                }
            } else {
                Messages.NO_PERMISSION(player);
            }
            return true;
        } else if(args.length > 2){
            if(sender.hasPermission(Permissions.MEMBER)){
                if(args[1].equals("list")) {
                    AutoCraftingStorage storage = Config.getAutoCraftStorage(player.getUniqueId(), args[2]);
                    if(storage != null){
                        Messages.LIST_MEMBERS(player,storage);
                        return true;
                    }
                }
            }
        } else {
            player.sendMessage(ChatColor.RED+ OPTIONS.MEMBER.commandHelp);
            player.sendMessage(ChatColor.RED+ OPTIONS.MEMBER.description);
            return true;
        }
        return false;
    }
}
