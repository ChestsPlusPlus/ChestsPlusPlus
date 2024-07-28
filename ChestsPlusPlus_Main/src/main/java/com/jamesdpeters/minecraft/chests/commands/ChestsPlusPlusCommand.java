package com.jamesdpeters.minecraft.chests.commands;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.VersionMatcher;
import com.jamesdpeters.minecraft.chests.maventemplates.BuildConstants;
import com.jamesdpeters.minecraft.chests.menus.PartyMenu;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.party.PartyUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChestsPlusPlusCommand extends ServerCommand {

    private enum OPTIONS {
        VERSION("/chestsplusplus version", "Display the current version of the plugin."),
        PARTY("/chestsplusplus party", "Open the party menu to create, add, remove and invite parties and players."),
        RELOAD("/chestsplusplus reload", "Reloads the plugin.");

        final String description;
        final String commandHelp;
        static final List<String> valuesList;

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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can use this command");
            return false;
        }
        if (args != null && args.length > 0) {
            switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                case VERSION:
                    sender.sendMessage("ChestsPlusPlus Version: " + BuildConstants.VERSION);
                    sender.sendMessage("Server Version: " + Bukkit.getVersion());
                    sender.sendMessage("CraftBukkit Version: " + Bukkit.getBukkitVersion());
                    sender.sendMessage("Detected API Version: " + VersionMatcher.match());
                    return true;

                case RELOAD:
                    ChestsPlusPlus.PLUGIN.onEnable();
                    return true;

                case PARTY:
                    PartyMenu.getMenu(player).getMenu().open(player);
                    return true;

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
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if ((sender instanceof Player player)) {
            if (args.length == 1) {
                return OPTIONS.valuesList;
            }
            if (args.length == 2) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                        case PARTY:
                            return Stream.of("menu", "create", "invite", "delete", "remove-member", "view-invites", "list").filter(s -> s.contains(args[1])).collect(Collectors.toList());
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (args.length == 3) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                        case PARTY -> {
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
                        case PARTY -> {
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
