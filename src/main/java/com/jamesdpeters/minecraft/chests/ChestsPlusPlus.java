package com.jamesdpeters.minecraft.chests;

import com.jamesdpeters.minecraft.chests.commands.RemoteChestCommand;
import com.jamesdpeters.minecraft.chests.listeners.ChestLinkListener;
import com.jamesdpeters.minecraft.chests.listeners.HopperListener;
import com.jamesdpeters.minecraft.chests.listeners.InventoryListener;
import com.jamesdpeters.minecraft.chests.misc.Config;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Settings;
import com.jamesdpeters.minecraft.chests.misc.Stats;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import com.jamesdpeters.minecraft.chests.serialize.LinkedChest;
import com.jamesdpeters.minecraft.chests.versionchecker.UpdateCheck;
import com.jamesdpeters.minecraft.chests.maventemplates.BuildConstants;
import fr.minuskube.inv.InventoryManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.command.Command;
import org.bukkit.plugin.java.annotation.command.Commands;
import org.bukkit.plugin.java.annotation.permission.Permission;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Description;
import org.bukkit.plugin.java.annotation.plugin.Plugin;
import org.bukkit.plugin.java.annotation.plugin.author.Author;


@Plugin(name = "ChestsPlusPlus", version = BuildConstants.VERSION)
@ApiVersion(ApiVersion.Target.v1_13)
@Description(value = "Minecraft Spigot mod that enhances chests and hoppers, with ChestLinks and Hopper filters!")
@Author(value = "jameslfc19")
@Commands(@Command(name = "chestlink", desc = "Chests++ Commands.", aliases = {"cl"}, usage = "Use /chestlink help for more info."))
@Permission(name = Permissions.ADD, desc = "Gives permission to add ChestLinks!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.OPEN, desc = "Gives permission to open ChestLinks!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.MENU, desc = "Gives permission to open the ChestLink menu!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.REMOVE, desc = "Gives permission to remove a ChestLink!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.OPEN_ANY, desc = "Gives permission to open all chests, for admin use.", defaultValue = PermissionDefault.OP)
@Permission(name = Permissions.MEMBER, desc = "Gives permission to add/remove a member to/from their chestlink.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.SORT, desc = "Set the sorting option for the given ChestLink.", defaultValue = PermissionDefault.TRUE)
public class ChestsPlusPlus extends JavaPlugin {

    public static JavaPlugin PLUGIN;
    public static InventoryManager INVENTORY_MANAGER;
    private static boolean boot = false;

    static {
        ConfigurationSerialization.registerClass(LinkedChest.class, "LinkedChest");
        ConfigurationSerialization.registerClass(InventoryStorage.class, "InventoryStorage");
    }

    @Override
    public void onEnable() {
        int pluginId = 7166;
        Metrics metrics = new Metrics(this, pluginId);
        Stats.addCharts(metrics);

        Settings.initConfig(this);

        PLUGIN = this;
        new RemoteChestCommand().register(this);
        getServer().getPluginManager().registerEvents(new ChestLinkListener(),this);
        getServer().getPluginManager().registerEvents(new InventoryListener(),this);
        getServer().getPluginManager().registerEvents(new HopperListener(),this);

        new Config();

        INVENTORY_MANAGER = new InventoryManager(this);
        INVENTORY_MANAGER.init();

        if(Settings.isUpdateCheckEnabled()) {
            String SPIGOT_URL = "https://www.spigotmc.org/resources/chests-chest-linking-hopper-filtering-remote-chests-menus.71355/";
            UpdateCheck updateChecker = UpdateCheck
                    .of(this)
                    .resourceId(71355)
                    .currentVersion(getDescription().getVersion())
                    .handleResponse((versionResponse, version) -> {
                        switch (versionResponse) {
                            case FOUND_NEW:
                                getLogger().warning("New version of the plugin has been found: " + version);
                                getLogger().warning("Download at: "+SPIGOT_URL);
                                Bukkit.broadcastMessage(ChatColor.RED + "[Chests++] New version of the plugin was found: " + version);
                                break;
                            case LATEST:
                                if(!boot) getLogger().info("Plugin is up to date! Thank you for supporting Chests++!");
                                break;
                            case UNAVAILABLE:
                                Bukkit.broadcastMessage("Unable to perform an update check.");
                        }
                        boot = true;
                    });
            Bukkit.getScheduler().scheduleSyncRepeatingTask(this, updateChecker::check,0,Settings.getUpdateCheckerPeriodTicks());
        }

        getLogger().info("Chests++ enabled!");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        Config.save();
    }

}
