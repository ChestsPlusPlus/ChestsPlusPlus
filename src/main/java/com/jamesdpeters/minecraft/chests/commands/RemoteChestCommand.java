package com.jamesdpeters.minecraft.chests.commands;

import com.jamesdpeters.minecraft.chests.inventories.ChestLinkMenu;
import com.jamesdpeters.minecraft.chests.misc.Config;
import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
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
        ADD("/chestlink add <Group>", "Create/add a chest to a ChestLink group"),
        REMOVE("/chestlink remove <Group>", "Delete a ChestLink and drop its inventory at your feet!"),
        OPEN("/chestlink open <Group>","Open the inventory of a ChestLink group"),
        MENU("/chestlink menu","Open the ChestLink menu to display all groups!"),
        ADDMEMBER("/chestlink addmember <group> <player>","Add a player to this ChestLink so they can access your chest!"),
        REMOVEMEMBER("/chestlink removemember <group> <player>","Remove a player from this ChestLink!");

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
        if(args != null) {

            switch (OPTIONS.valueOf(args[0].toUpperCase())){
                case ADD:
                    if(args.length > 1){
                        if(sender.hasPermission(Permissions.ADD)) {
                            Block targetBlock = player.getTargetBlockExact(5);
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
                            InventoryStorage invs = Config.getInventoryStorage(player.getUniqueId(), args[1]);
                            Utils.openInventory(player, invs.getInventory());
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
                case ADDMEMBER:
                    if(args.length > 2){
                        if(sender.hasPermission(Permissions.MEMBER)){
                            Player toAdd = Bukkit.getPlayer(args[2]);
                            InventoryStorage storage = Config.getInventoryStorage(player.getUniqueId(),args[1]);
                            if(storage != null && storage.addMember(toAdd)) Messages.ADDED_MEMBER(player,storage,args[2]);
                            else Messages.UNABLE_TO_ADD_MEMBER(player,args[2]);
                        }
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED+OPTIONS.ADDMEMBER.commandHelp);
                        player.sendMessage(ChatColor.RED+OPTIONS.ADDMEMBER.description);
                        return true;
                    }
                case REMOVEMEMBER:
                    if(args.length > 2){
                        if(sender.hasPermission(Permissions.MEMBER)){
                            Player toAdd = Bukkit.getPlayer(args[2]);
                            InventoryStorage storage = Config.getInventoryStorage(player.getUniqueId(),args[1]);
                            if(storage != null && storage.removeMember(toAdd)) Messages.REMOVE_MEMBER(player,storage,args[2]);
                            else Messages.UNABLE_TO_REMOVE_MEMBER(player,args[2]);
                        }
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED+OPTIONS.REMOVEMEMBER.commandHelp);
                        player.sendMessage(ChatColor.RED+OPTIONS.REMOVEMEMBER.description);
                        return true;
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
                            return null;
                        case OPEN:
                        case REMOVE:
                        case ADDMEMBER:
                        case REMOVEMEMBER:
                            return new ArrayList<>(Config.getPlayer(player.getUniqueId()).keySet());
                    }
                } catch (IllegalArgumentException ignored) { }
            }
            if(args.length == 3) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                        case ADDMEMBER:
                            return Utils.getOnlinePlayers();
                        case REMOVEMEMBER:
                            return Utils.getPlayersAsNameList(Config.getInventoryStorage(player.getUniqueId(),args[1]).getMembers());
                    }
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return null;
    }
}
