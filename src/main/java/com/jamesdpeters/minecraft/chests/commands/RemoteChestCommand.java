package com.jamesdpeters.minecraft.chests.commands;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.Config;
import com.jamesdpeters.minecraft.chests.Messages;
import com.jamesdpeters.minecraft.chests.Utils;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RemoteChestCommand extends ServerCommand  {

    private enum OPTIONS {
        ADD("/chestlink add <Group>", "Create/add a chest to a ChestLink group"),
        OPEN("/chestlink open <Group>","Open the inventory of a ChestLink group");

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

        String getCommandHelp(){
            return commandHelp;
        }

        String getDescription(){
            return description;
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
                        Block targetBlock = player.getTargetBlockExact(5);
                        if(targetBlock != null) Utils.createChestLink(player,targetBlock,args[1]);
                        else Messages.MUST_LOOK_AT_CHEST(player);
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED+OPTIONS.ADD.commandHelp);
                        player.sendMessage(ChatColor.RED+OPTIONS.ADD.description);
                        return true;
                    }
                case OPEN:
                    if(args.length > 1){
                        InventoryStorage invs = Config.getInventoryStorage(player,args[1]);
                        Utils.openInventory(player,invs.getInventory());
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED+OPTIONS.OPEN.commandHelp);
                        player.sendMessage(ChatColor.RED+OPTIONS.OPEN.description);
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
            try {
                switch (OPTIONS.valueOf(args[0].toUpperCase())) {
                    case ADD:
                        return null;
                    case OPEN:
                        return new ArrayList<>(Config.getPlayer(player).keySet());
                }
            } catch (IllegalArgumentException e){

            }
        }
        return null;
    }
}
