package com.jamesdpeters.minecraft.chests.commands;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class ServerCommand implements CommandExecutor, TabCompleter {

    /**
     * @param plugin Registers command to given plugin.
     */
    public void register(JavaPlugin plugin){
        plugin.getCommand(getCommandName()).setExecutor(this);
        plugin.getCommand(getCommandName()).setTabCompleter(this);
    }

    public abstract String getCommandName();

}
