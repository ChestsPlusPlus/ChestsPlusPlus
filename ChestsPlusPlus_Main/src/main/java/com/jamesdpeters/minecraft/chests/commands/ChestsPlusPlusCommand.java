package com.jamesdpeters.minecraft.chests.commands;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.inventories.PartyMenu;
import com.jamesdpeters.minecraft.chests.lang.Message;
import com.jamesdpeters.minecraft.chests.maventemplates.BuildConstants;
import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.party.PartyUtils;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChestsPlusPlusCommand extends ServerCommand {

    private enum OPTIONS {
        VERSION("/chestsplusplus version", "Display the current version of the plugin."),
        PARTY("/chestsplusplus party <create/delete/invite/remove-member/list>", "Create, delete or manage members of your party"),
        PARTY_MENU("/chestsplusplus party <create/delete/invite/remove-member/list>", "Create, delete or manage members of your party"),
        RELOAD("/chestsplusplus reload", "Reloads the plugin.");

        String description, commandHelp;
        static List<String> valuesList;

        static {
            valuesList = Stream.of(OPTIONS.values()).map(OPTIONS::toString).collect(Collectors.toList());
        }

        OPTIONS(String commandHelp, String description) {
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
        return "chests++";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only a player can use this command");
            return false;
        }
        Player player = (Player) sender;
        if (args != null && args.length > 0) {
            switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                case VERSION:
                    sender.sendMessage("ChestsPlusPlus Version: " + BuildConstants.VERSION);
                    sender.sendMessage("Server Version: " + Bukkit.getVersion());
                    sender.sendMessage("CraftBukkit Version: " + Bukkit.getBukkitVersion());
                    sender.sendMessage("Detected API Version: " + ApiSpecific.getApiVersion());
                    return true;

                case RELOAD:
                    ChestsPlusPlus.PLUGIN.onEnable();
                    return true;

                case PARTY_MENU:
                    PartyMenu.getMenu(player).getMenu().open(player);
                    return true;

                case PARTY:
                    if (args.length > 1) {
                        if (sender.hasPermission(Permissions.PARTY_ACCEPT_INVITE)) {
                            if (args[1].toLowerCase().equals("accept-invite")) {
                                PartyUtils.acceptInvite(player);
                                return true;
                            }
                        } else {
                            Messages.NO_PERMISSION(player);
                            return true;
                        }
                        if (args[1].toLowerCase().equals("list")) {
                            // TODO Print list of owned parties.
                            return true;
                        }
                    }
                    if (args.length > 2) {
                        if(sender.hasPermission(Permissions.PARTY_CREATE)){
                            if (args[1].toLowerCase().equals("create")){
                                String partyName = args[2];
                                boolean result = PartyUtils.createParty(player, partyName);
                                if (result){
                                    sender.sendMessage(ChatColor.GREEN+Message.PARTY_CREATED.getString(ChatColor.WHITE+partyName+ChatColor.GREEN));
                                } else {
                                    sender.sendMessage(ChatColor.RED+Message.PARTY_ALREADY_EXISTS.getString(ChatColor.WHITE+partyName+ChatColor.RED));
                                }
                                return true;
                            }
                            if (args[1].toLowerCase().equals("delete")){
                                String partyName = args[2];
                                boolean result = PartyUtils.deleteParty(player, partyName);
                                if (result){
                                    sender.sendMessage(ChatColor.GREEN+Message.PARTY_DELETED.getString(ChatColor.WHITE+partyName+ChatColor.GREEN));
                                } else {
                                    sender.sendMessage(ChatColor.RED+Message.PARTY_DOESNT_EXIST.getString(ChatColor.WHITE+partyName+ChatColor.RED));
                                }
                                return true;
                            }
                        } else {
                            Messages.NO_PERMISSION(player);
                            return true;
                        }
                        if (args.length > 3) {
                            if (sender.hasPermission(Permissions.PARTY_INVITE)) {
                                if (args[1].toLowerCase().equals("invite")) {
                                    String partyName = args[2];
                                    String playerToInvite = args[3];
                                    PartyUtils.invitePlayer(player, Bukkit.getOfflinePlayer(playerToInvite), partyName);
                                    return true;
                                }
                            } else {
                                Messages.NO_PERMISSION(player);
                                return true;
                            }
                            if (args[1].toLowerCase().equals("remove-member")) {
                                String partyName = args[2];
                                String playerToInvite = args[3];
                                PartyUtils.removePlayer(player, Bukkit.getOfflinePlayer(playerToInvite), partyName);
                                return true;
                            }
                        }
                    }

                default:
                    for (ChestsPlusPlusCommand.OPTIONS option : ChestsPlusPlusCommand.OPTIONS.values()) {
                        sender.sendMessage(ChatColor.RED + option.commandHelp);
                        sender.sendMessage(ChatColor.WHITE + option.description);
                    }
            }
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ((sender instanceof Player)) {
            Player player = (Player) sender;
            if (args.length == 1) {
                return OPTIONS.valuesList;
            }
            if (args.length == 2) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                        case PARTY:
                            return Stream.of("create", "invite", "delete", "remove-member", "accept-invite", "list").filter(s -> s.contains(args[1])).collect(Collectors.toList());
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (args.length == 3) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                        case PARTY: {
                            String arg = args[1];
                            if (arg.equals("delete") || arg.equals("invite") || arg.equals("remove-member")) {
                                List<String> strings = PartyUtils.getPlayerPartyStorage(player).getOwnedPartiesAsStrings();
                                return Utils.filterList(strings, args[2]);
                            }
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (args.length == 4) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                        case PARTY: {
                            String arg = args[2];
                            if (arg.equals("invite") || arg.equals("remove-member")) {
                                return Utils.filterList(Utils.getAllPlayers(), args[3]);
                            }

                        }
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }
}
