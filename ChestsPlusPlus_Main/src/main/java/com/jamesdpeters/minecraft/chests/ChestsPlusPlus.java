package com.jamesdpeters.minecraft.chests;

import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.commands.AutoCraftCommand;
import com.jamesdpeters.minecraft.chests.commands.ChestLinkCommand;
import com.jamesdpeters.minecraft.chests.commands.ChestsPlusPlusCommand;
import com.jamesdpeters.minecraft.chests.lang.LangFileProperties;
import com.jamesdpeters.minecraft.chests.listeners.AutoCrafterListener;
import com.jamesdpeters.minecraft.chests.listeners.EntityEventListener;
import com.jamesdpeters.minecraft.chests.listeners.HopperFilterListener;
import com.jamesdpeters.minecraft.chests.listeners.InventoryListener;
import com.jamesdpeters.minecraft.chests.listeners.LinkedChestHopperListener;
import com.jamesdpeters.minecraft.chests.listeners.StorageListener;
import com.jamesdpeters.minecraft.chests.listeners.WorldListener;
import com.jamesdpeters.minecraft.chests.misc.ServerType;
import com.jamesdpeters.minecraft.chests.misc.Stats;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.party.PlayerParty;
import com.jamesdpeters.minecraft.chests.party.PlayerPartyStorage;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.ConfigStorage;
import com.jamesdpeters.minecraft.chests.serialize.LocationInfo;
import com.jamesdpeters.minecraft.chests.serialize.MaterialSerializer;
import com.jamesdpeters.minecraft.chests.serialize.RecipeSerializable;
import com.jamesdpeters.minecraft.chests.serialize.SpigotConfig;
import com.jamesdpeters.minecraft.chests.storage.autocraft.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.storage.autocraft.AutoCraftingStorageType;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorageType;
import com.jamesdpeters.minecraft.chests.versionchecker.UpdateChecker;
import fr.minuskube.inv.InventoryManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

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
        if (!ApiSpecific.init(this)) {
            return;
        }

        //Load storage
        ServerType.init();
        SpigotConfig.load(this);

        INVENTORY_MANAGER = new InventoryManager(this);
        INVENTORY_MANAGER.init();

        if (PluginConfig.IS_UPDATE_CHECKER_ENABLED.get()) {
            String BUKKIT_URL = "https://dev.bukkit.org/projects/chests-plus-plus/files";
            UpdateChecker.init(this, 71355, UpdateChecker.VERSION_SCHEME_DECIMAL);
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> UpdateChecker.get().requestUpdateCheck().whenCompleteAsync((updateResult, throwable) -> {
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
            }), 0, PluginConfig.UPDATE_CHECKER_PERIOD.get() * 20);
        }

        //Load storages after load.
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            new Config();
            getLogger().info("Chests++ Successfully Loaded Config and Recipes");

            //LinkedChest
            if (PluginConfig.CHESTLINKS_ENABLED.get()) {
                new ChestLinkCommand().register(this);
                getServer().getPluginManager().registerEvents(new LinkedChestHopperListener(), this);
            }

            //AutoCrafter
            if (PluginConfig.AUTOCRAFTERS_ENABLED.get()) {
                new AutoCraftCommand().register(this);
                getServer().getPluginManager().registerEvents(new AutoCrafterListener(), this);
            }

            //HopperFilter
            if (PluginConfig.HOPPER_FILTERS_ENABLED.get()) {
                getServer().getPluginManager().registerEvents(new HopperFilterListener(), this);
            }

            //Shared
            if (PluginConfig.CHESTLINKS_ENABLED.get() || PluginConfig.AUTOCRAFTERS_ENABLED.get()) {
                getServer().getPluginManager().registerEvents(new StorageListener(), this);
                getServer().getPluginManager().registerEvents(new InventoryListener(), this);
            }

            //Other
            getServer().getPluginManager().registerEvents(new WorldListener(), this);
            new ChestsPlusPlusCommand().register(this);

            Config.getStorageTypes().forEach(storageType -> {
                if (storageType instanceof AutoCraftingStorageType && PluginConfig.AUTOCRAFTERS_ENABLED.get()) {
                    getServer().getPluginManager().registerEvents(storageType, this);
                }
                if (storageType instanceof ChestLinkStorageType && PluginConfig.CHESTLINKS_ENABLED.get()) {
                    getServer().getPluginManager().registerEvents(storageType, this);
                }
            });

            getServer().getPluginManager().registerEvents(new EntityEventListener(), this);
            Bukkit.getWorlds().forEach(EntityEventListener::fixEntities);
            Config.onPostConfigLoad();
            getLogger().info("Chests++ enabled!");
        }, 1);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        Config.save();
        HandlerList.unregisterAll(this);
//        //Remove entities that could have been left behind from bad save files/crashes etc.
//        Utils.fixEntities();
    }

}
