package com.jamesdpeters.minecraft.chests;

import com.jamesdpeters.minecraft.chests.api_interfaces.ApiSpecific;
import com.jamesdpeters.minecraft.chests.commands.AutoCraftCommand;
import com.jamesdpeters.minecraft.chests.commands.ChestLinkCommand;
import com.jamesdpeters.minecraft.chests.crafting.Crafting;
import com.jamesdpeters.minecraft.chests.listeners.StorageListener;
import com.jamesdpeters.minecraft.chests.listeners.HopperListener;
import com.jamesdpeters.minecraft.chests.listeners.InventoryListener;
import com.jamesdpeters.minecraft.chests.listeners.WorldListener;
import com.jamesdpeters.minecraft.chests.maventemplates.BuildConstants;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Settings;
import com.jamesdpeters.minecraft.chests.misc.Stats;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.storage.autocraft.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import com.jamesdpeters.minecraft.chests.serialize.ConfigStorage;
import com.jamesdpeters.minecraft.chests.serialize.LocationInfo;
import com.jamesdpeters.minecraft.chests.serialize.MaterialSerializer;
import com.jamesdpeters.minecraft.chests.serialize.RecipeSerializable;
import com.jamesdpeters.minecraft.chests.serialize.SpigotConfig;
import com.jamesdpeters.minecraft.chests.versionchecker.UpdateCheck;
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
@ApiVersion(ApiVersion.Target.v1_14)
@Description(value = "Minecraft Spigot mod that enhances chests and hoppers, with ChestLinks and Hopper filters!")
@Author(value = "jameslfc19")
@Commands({
        @Command(name = "chestlink", desc = "Chests++ ChestLink Commands.", aliases = {"cl"}, usage = "Use /chestlink help for more info."),
        @Command(name = "autocraft", desc = "Chests++ AutoCraft Commands.", aliases = {"ac"}, usage = "Use /autocraft help for more info.")})
@Permission(name = Permissions.ADD, desc = "Gives permission to add ChestLinks!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.OPEN, desc = "Gives permission to open ChestLinks!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.MENU, desc = "Gives permission to open the ChestLink menu!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.REMOVE, desc = "Gives permission to remove a ChestLink!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.OPEN_ANY, desc = "Gives permission to open all chests, for admin use.", defaultValue = PermissionDefault.OP)
@Permission(name = Permissions.MEMBER, desc = "Gives permission to add/remove a member to/from their chestlink.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.SORT, desc = "Set the sorting option for the given ChestLink.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.AUTOCRAFT_OPEN, desc = "Gives permission to open AutoCrafting stations.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.AUTOCRAFT_ADD, desc = "Gives permission to add AutoCrafting stations.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.AUTOCRAFT_REMOVE, desc = "Gives permission to remove AutoCrafting stations.", defaultValue = PermissionDefault.TRUE)
public class ChestsPlusPlus extends JavaPlugin {

    public static JavaPlugin PLUGIN;
    public static InventoryManager INVENTORY_MANAGER;
    private static boolean boot = false;

    static {
        ConfigurationSerialization.registerClass(ConfigStorage.class, "ConfigStorage");
        ConfigurationSerialization.registerClass(ChestLinkStorage.class, "ChestLinkStorage");
        ConfigurationSerialization.registerClass(MaterialSerializer.class, "Material");
        ConfigurationSerialization.registerClass(AutoCraftingStorage.class, "AutoCraftingStorage");
        ConfigurationSerialization.registerClass(RecipeSerializable.class, "Recipe");
        ConfigurationSerialization.registerClass(LocationInfo.class, "LocationInfo");

    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onEnable() {
        int pluginId = 7166;
        Metrics metrics = new Metrics(this, pluginId);
        Stats.addCharts(metrics);

        Settings.initConfig(this);
        Crafting.load();
        PLUGIN = this;

        //API initialisation
        API.register(this);
        ApiSpecific.init();

        //Remove entities that could have been left behind from bad save files/crashes etc.
        Utils.removeEntities();

        new ChestLinkCommand().register(this);
        new AutoCraftCommand().register(this);
        getServer().getPluginManager().registerEvents(new StorageListener(),this);
        getServer().getPluginManager().registerEvents(new InventoryListener(),this);
        getServer().getPluginManager().registerEvents(new HopperListener(),this);
        getServer().getPluginManager().registerEvents(new WorldListener(),this);

        SpigotConfig.load(this);
        new Config();

        INVENTORY_MANAGER = new InventoryManager(this);
        INVENTORY_MANAGER.init();

        boolean isDev = BuildConstants.VERSION.contains("DEV");
        boolean isBeta = BuildConstants.VERSION.contains("BETA");
        if(isDev) getLogger().warning("You are currently running a Dev build - update checker disabled! Build: "+BuildConstants.VERSION);
        if(isBeta) getLogger().warning("You are currently running a Beta build - update checker disabled! Build: "+BuildConstants.VERSION);

        if(Settings.isUpdateCheckEnabled() && !isDev && !isBeta) {
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
