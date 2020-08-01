package com.jamesdpeters.minecraft.chests.commands;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.inventories.ChestLinkMenu;
import com.jamesdpeters.minecraft.chests.lang.Message;
import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import com.jamesdpeters.minecraft.chests.sort.SortMethod;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChestLinkCommand extends ServerCommand  {

    private enum OPTIONS {
        ADD("/chestlink add <group>", Message.COMMAND_CHESTLINK_ADD.getString()),
        HELP("/chestlink help", Message.COMMAND_HELP.getString()),
        LIST("/chestlink list", Message.COMMAND_CHESTLINK_LIST.getString()),
        MEMBER("/chestlink member [add/remove <group> <player>] or [list <group>]", Message.COMMAND_MEMBER.getString()),
        MENU("/chestlink menu", Message.COMMAND_CHESTLINK_MENU.getString()),
        OPEN("/chestlink open <Group>", Message.COMMAND_CHESTLINK_OPEN.getString()),
        REMOVE("/chestlink remove <Group>", Message.COMMAND_CHESTLINK_REMOVE.getString()),
        RENAME("/chestlink rename <group> <new-name>", Message.COMMAND_CHESTLINK_RENAME.getString()),
        SETPUBLIC("/chestlink setpublic <group> <true/false>", Message.COMMAND_CHESTLINK_SETPUBLIC.getString()),
        SORT("/chestlink sort <group> <sort-method>", Message.COMMAND_CHESTLINK_SORT.getString());

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
            try {
                switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                    case HELP:
                        for (OPTIONS option : OPTIONS.values()) {
                            if (!option.equals(OPTIONS.HELP)) {
                                player.sendMessage(ChatColor.RED + option.commandHelp);
                                player.sendMessage(ChatColor.WHITE + option.description);
                            }
                        }
                        return true;
                    case ADD:
                        if (args.length > 1) {
                            if (sender.hasPermission(Permissions.ADD) && !Utils.isBlacklistedWorld(player.getWorld())) {
                                Block targetBlock = player.getTargetBlockExact(5);
                                if (targetBlock != null)
                                    Config.getChestLink().createStorage(player, targetBlock, args[1], true);
                                else Config.getChestLink().getMessages().mustLookAtBlock(player);
                                return true;
                            } else {
                                Messages.NO_PERMISSION(player);
                                return true;
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + OPTIONS.ADD.commandHelp);
                            player.sendMessage(ChatColor.RED + OPTIONS.ADD.description);
                            return true;
                        }
                    case OPEN:
                        if (args.length > 1) {
                            if (sender.hasPermission(Permissions.OPEN) && sender.hasPermission(Permissions.OPEN_REMOTE) && !Utils.isBlacklistedWorld(player.getWorld())) {
                                ChestLinkStorage invs;
                                if (args[1].contains(":")) {
                                    invs = Config.getChestLink().getStorage(player, args[1]);
                                } else {
                                    invs = Config.getChestLink().getStorage(player.getUniqueId(), args[1]);
                                }
                                if (invs != null) Utils.openChestInventory(player, invs.getInventory());
                                return true;
                            } else {
                                Messages.NO_PERMISSION(player);
                                return true;
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + OPTIONS.OPEN.commandHelp);
                            player.sendMessage(ChatColor.RED + OPTIONS.OPEN.description);
                            return true;
                        }
                    case MENU:
                        if (sender.hasPermission(Permissions.MENU) && !Utils.isBlacklistedWorld(player.getWorld())) {
                            ChestLinkMenu.getMenu(player).open(player);
                            return true;
                        } else {
                            Messages.NO_PERMISSION(player);
                            return true;
                        }
                    case LIST:
                        Config.getChestLink().getMessages().listStorageGroups(player);
                        return true;
                    case REMOVE:
                        if (args.length > 1) {
                            if (sender.hasPermission(Permissions.REMOVE)) {
                                Config.getChestLink().removeStorage(player, args[1]);
                                return true;
                            } else {
                                Messages.NO_PERMISSION(player);
                                return true;
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + OPTIONS.REMOVE.commandHelp);
                            player.sendMessage(ChatColor.RED + OPTIONS.REMOVE.description);
                            return true;
                        }
                    case SORT:
                        if (args.length > 1) {
                            if (sender.hasPermission(Permissions.SORT)) {
                                ChestLinkStorage storage = Config.getChestLink().getStorage(player.getUniqueId(), args[1]);
                                if (storage != null) {
                                    storage.setSortMethod(Enum.valueOf(SortMethod.class, args[2]));
                                    storage.sort();
                                    Messages.SORT(player, storage);
                                }
                                return true;
                            } else {
                                Messages.NO_PERMISSION(player);
                                return true;
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + OPTIONS.SORT.commandHelp);
                            player.sendMessage(ChatColor.RED + OPTIONS.SORT.description);
                            return true;
                        }
                    case MEMBER:
                        return memberCommand(args, sender);
                    case SETPUBLIC: {
                        if (args.length > 2) {
                            ChestLinkStorage storage = Config.getChestLink().getStorage(player.getUniqueId(), args[1]);
                            if (storage != null) {
                                boolean setpublic = Boolean.parseBoolean(args[2]);
                                storage.setPublic(setpublic);
                                storage.getStorageType().getMessages().setPublic(player, storage);
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
                        if (args.length > 2) {
                            String group = args[1];
                            String newIdentifier = args[2];
                            if (!Config.getChestLink().renameStorage(player, group, newIdentifier)) {
                                player.sendMessage(ChatColor.RED + OPTIONS.RENAME.commandHelp);
                                player.sendMessage(ChatColor.RED + OPTIONS.RENAME.description);
                            }
                            return true;
                        }
                    }
                }
            } catch (IllegalArgumentException exception){
                return false;
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
                            return Config.getChestLink().getOpenableStorageList(player, args[1]);
                        case REMOVE:
                        case SORT:
                        case RENAME:
                            return Config.getChestLink().getStorageList(player, args[1]);
                        case MEMBER:
                            return Stream.of("add","remove","list","add-to-all","remove-from-all").filter(s -> s.contains(args[1])).collect(Collectors.toList());
                    }
                } catch (IllegalArgumentException ignored) { }
            }
            if(args.length == 3) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                        case MEMBER:
                            if(args[1].equals("add-to-all")) return Utils.filterList(Utils.getAllPlayers(), args[2]);
                            if(args[1].equals("remove-from-all")) return Utils.filterList(Utils.getAllPlayers(), args[2]);
                            return Config.getChestLink().getStorageList(player, args[2]);
                        case SORT:
                            return SortMethod.valuesList;
                    }
                } catch (IllegalArgumentException ignored) { }
            }
            if(args.length == 4) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                        case MEMBER:
                            return Utils.filterList(Utils.getAllPlayers(), args[3]);
                    }
                } catch (IllegalArgumentException ignored) { }
            }
            return Collections.singletonList("");
        }
        return null;
    }

    private static boolean memberCommand(String[] args, CommandSender sender){
        Player player = (Player) sender;
        if(args.length > 3){
            if(sender.hasPermission(Permissions.MEMBER)){
                if(args[1].equals("add")) {
                    Bukkit.getScheduler().runTaskAsynchronously(ChestsPlusPlus.PLUGIN, () -> {
                        OfflinePlayer toAdd = Bukkit.getOfflinePlayer(args[3]);
                        ChestLinkStorage storage = Config.getChestLink().getStorage(player.getUniqueId(), args[2]);
                        if (storage != null && storage.addMember(toAdd))
                            storage.getStorageType().getMessages().addedMember(player, storage, args[3]);
                        else Config.getChestLink().getMessages().unableToAddMember(player,args[3]);
                    });
                    return true;
                } else if(args[1].equals("remove")){
                    Bukkit.getScheduler().runTaskAsynchronously(ChestsPlusPlus.PLUGIN, () -> {
                        OfflinePlayer toAdd = Bukkit.getOfflinePlayer(args[3]);
                        ChestLinkStorage storage = Config.getChestLink().getStorage(player.getUniqueId(), args[2]);
                        if (storage != null && storage.removeMember(toAdd))
                            storage.getStorageType().getMessages().removedMember(player, storage, args[3]);
                        else Config.getChestLink().getMessages().unableToRemoveMember(player, args[3]);
                    });
                    return true;
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
                    ChestLinkStorage storage = Config.getChestLink().getStorage(player.getUniqueId(), args[2]);
                    if(storage != null){
                        storage.getStorageType().getMessages().listMembers(player, storage);
                        return true;
                    }
                } else if(args[1].equals("add-to-all")){
                    Bukkit.getScheduler().runTaskAsynchronously(ChestsPlusPlus.PLUGIN, () -> {
                        OfflinePlayer toAdd = Bukkit.getOfflinePlayer(args[2]);
                        Config.getChestLink().getStorageMap(player.getUniqueId()).forEach((s, storage) -> storage.addMember(toAdd));
                        Config.getChestLink().getMessages().addMemberToAll(player, toAdd);
                    });
                    return true;
                } else if(args[1].equals("remove-from-all")){
                    Bukkit.getScheduler().runTaskAsynchronously(ChestsPlusPlus.PLUGIN, () -> {
                        OfflinePlayer toAdd = Bukkit.getOfflinePlayer(args[2]);
                        Config.getChestLink().getStorageMap(player.getUniqueId()).forEach((s, storage) -> storage.removeMember(toAdd));
                        Config.getChestLink().getMessages().removeMemberFromAll(player, toAdd);
                    });
                    return true;
                }
            }
        } else {
            player.sendMessage(ChatColor.RED+OPTIONS.MEMBER.commandHelp);
            player.sendMessage(ChatColor.RED+OPTIONS.MEMBER.description);
            return true;
        }
        return false;
    }
}
