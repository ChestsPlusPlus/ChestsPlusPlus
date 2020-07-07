package com.jamesdpeters.minecraft.chests.commands;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.maventemplates.BuildConstants;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChestsPlusPlusCommand extends ServerCommand  {

    private enum OPTIONS {
        VERSION("/chestsplusplus version", "Display the current version of the plugin."),
        RELOAD("/chestsplusplus reload", "Reloads the plugin.");

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
        return "chests++";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args != null &&  args.length > 0) {
            switch (OPTIONS.valueOf(args[0].toUpperCase())){
                case VERSION:
                    sender.sendMessage("ChestsPlusPlus Version: "+ BuildConstants.VERSION);
                    sender.sendMessage("Server Version: "+ Bukkit.getVersion());
                    sender.sendMessage("CraftBukkit Version: "+ Bukkit.getBukkitVersion());
                    sender.sendMessage("Detected API Version: "+ ApiSpecific.getApiVersion());
                    return true;

                case RELOAD:
                    ChestsPlusPlus.PLUGIN.onDisable();
                    ChestsPlusPlus.PLUGIN.onEnable();
                    return true;

                default:
                    for(ChestsPlusPlusCommand.OPTIONS option : ChestsPlusPlusCommand.OPTIONS.values()){
                        sender.sendMessage(ChatColor.RED + option.commandHelp);
                        sender.sendMessage(ChatColor.WHITE + option.description);
                    }
            }
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if((sender instanceof Player)) {
            if (args.length == 1) {
                return OPTIONS.valuesList;
            }
            if(args.length == 2) {
                try {
                    switch (OPTIONS.valueOf(args[0].toUpperCase())) {

                    }
                } catch (IllegalArgumentException ignored) { }
            }
            if(args.length == 3) {
                try {

                } catch (IllegalArgumentException ignored) { }
            }
            if(args.length == 4) {
                try {

                } catch (IllegalArgumentException ignored) { }
            }
        }
        return null;
    }
}
