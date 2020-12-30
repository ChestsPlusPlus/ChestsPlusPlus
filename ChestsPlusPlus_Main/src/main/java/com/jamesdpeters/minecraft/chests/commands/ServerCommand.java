package com.jamesdpeters.minecraft.chests.commands;

import co.aikar.commands.BaseCommand;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class ServerCommand extends BaseCommand implements CommandExecutor, TabCompleter {


    /**
     * @param plugin Registers command to given plugin.
     */
    public void register(JavaPlugin plugin) {
        PluginCommand command = plugin.getCommand(getCommandName());
        if(command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    public abstract String getCommandName();

}
