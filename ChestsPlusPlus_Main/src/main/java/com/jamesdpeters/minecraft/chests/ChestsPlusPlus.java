package com.jamesdpeters.minecraft.chests;

import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.commands.AutoCraftCommand;
import com.jamesdpeters.minecraft.chests.commands.ChestLinkCommand;
import com.jamesdpeters.minecraft.chests.commands.ChestsPlusPlusCommand;
import com.jamesdpeters.minecraft.chests.crafting.Crafting;
import com.jamesdpeters.minecraft.chests.lang.LangFileProperties;
import com.jamesdpeters.minecraft.chests.listeners.HopperListener;
import com.jamesdpeters.minecraft.chests.listeners.InventoryListener;
import com.jamesdpeters.minecraft.chests.listeners.StorageListener;
import com.jamesdpeters.minecraft.chests.listeners.WorldListener;
import com.jamesdpeters.minecraft.chests.maventemplates.BuildConstants;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.ServerType;
import com.jamesdpeters.minecraft.chests.misc.Stats;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.party.PlayerParty;
import com.jamesdpeters.minecraft.chests.party.PlayerPartyStorage;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.ConfigStorage;
import com.jamesdpeters.minecraft.chests.serialize.LocationInfo;
import com.jamesdpeters.minecraft.chests.serialize.MaterialSerializer;
import com.jamesdpeters.minecraft.chests.serialize.PluginConfig;
import com.jamesdpeters.minecraft.chests.serialize.RecipeSerializable;
import com.jamesdpeters.minecraft.chests.serialize.SpigotConfig;
import com.jamesdpeters.minecraft.chests.storage.autocraft.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import com.jamesdpeters.minecraft.chests.versionchecker.UpdateChecker;
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
        @Command(name = "autocraft", desc = "Chests++ AutoCraft Commands.", aliases = {"ac"}, usage = "Use /autocraft help for more info."),
        @Command(name = "chests++", desc = "Chests++ Commands.", aliases = {"c++"}, usage = "/chests++ version")})
@Permission(name = Permissions.ADD, desc = "Gives permission to add ChestLinks!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.OPEN, desc = "Gives permission to open ChestLinks!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.OPEN_REMOTE, desc = "Gives permission to remotely open ChestLinks!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.MENU, desc = "Gives permission to open the ChestLink menu!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.REMOVE, desc = "Gives permission to remove a ChestLink!", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.OPEN_ANY, desc = "Gives permission to open all chests/autocraft stations, for admin use.", defaultValue = PermissionDefault.OP)
@Permission(name = Permissions.MEMBER, desc = "Gives permission to add/remove a member to/from their chestlink.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.SORT, desc = "Gives permission to sort ChestLinks.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.AUTOCRAFT_OPEN, desc = "Gives permission to open AutoCrafting stations.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.AUTOCRAFT_OPEN_REMOTE, desc = "Gives permission to remotely open AutoCrafting stations.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.AUTOCRAFT_ADD, desc = "Gives permission to add AutoCrafting stations.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.AUTOCRAFT_REMOVE, desc = "Gives permission to remove AutoCrafting stations.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.PARTY_CREATE, desc = "Gives permission to create Chests++ parties.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.PARTY_ACCEPT_INVITE, desc = "Gives permission to accept Chests++ party invites.", defaultValue = PermissionDefault.TRUE)
@Permission(name = Permissions.PARTY_INVITE, desc = "Gives permission to invite players to Chests++ parties.", defaultValue = PermissionDefault.TRUE)
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
        ConfigurationSerialization.registerClass(PlayerPartyStorage.class, "PlayerPartyStorage");
        ConfigurationSerialization.registerClass(PlayerParty.class, "PlayerParty");

    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onEnable() {
        int pluginId = 7166;
        Metrics metrics = new Metrics(this, pluginId);

        PLUGIN = this;
        Utils.copyFromResources(getFile(), "lang");
        PluginConfig.load(this);
        LangFileProperties.loadLangFile(PluginConfig.LANG_FILE.get());
        Stats.addCharts(metrics);

        //API initialisation
        Api.register(this);
        ApiSpecific.init();

        //Register commands
        new ChestLinkCommand().register(this);
        new AutoCraftCommand().register(this);
        new ChestsPlusPlusCommand().register(this);

        //Load storage
        ServerType.init();
        SpigotConfig.load(this);

        INVENTORY_MANAGER = new InventoryManager(this);
        INVENTORY_MANAGER.init();

        if (PluginConfig.IS_UPDATE_CHECKER_ENABLED.get()) {
            String BUKKIT_URL = "https://dev.bukkit.org/projects/chests-plus-plus/files";
            UpdateChecker.init(this, 71355, UpdateChecker.VERSION_SCHEME_DECIMAL);
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                UpdateChecker.get().requestUpdateCheck().whenCompleteAsync((updateResult, throwable) -> {
                    switch (updateResult.getReason()) {
                        case NEW_UPDATE:
                            Bukkit.broadcastMessage(ChatColor.RED + "[Chests++] New version of the plugin was found: " + updateResult.getNewestVersion());
                            Bukkit.broadcastMessage(ChatColor.RED + "[Chests++] Download at: " + ChatColor.WHITE + BUKKIT_URL);
                            break;
                        case UP_TO_DATE:
                            if (!boot) getLogger().info("Plugin is up to date! Thank you for supporting Chests++!");
                            break;
                    }
                    boot = true;
                });
            }, 0, PluginConfig.UPDATE_CHECKER_PERIOD.get() * 20);
        }

        // Remove armour stands if disabled
        Utils.fixEntities();

        //Load storages after load.
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            Crafting.load();
            new Config();
            getLogger().info("Chests++ Successfully Loaded Config and Recipes");

            //Register event listeners
            getServer().getPluginManager().registerEvents(new StorageListener(), this);
            getServer().getPluginManager().registerEvents(new InventoryListener(), this);
            getServer().getPluginManager().registerEvents(new HopperListener(), this);
            getServer().getPluginManager().registerEvents(new WorldListener(), this);
            Config.getStorageTypes().forEach(storageType -> getServer().getPluginManager().registerEvents(storageType, this));
            getLogger().info("Chests++ enabled!");
        }, 1);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        Config.save();
//        //Remove entities that could have been left behind from bad save files/crashes etc.
//        Utils.fixEntities();
    }

}
