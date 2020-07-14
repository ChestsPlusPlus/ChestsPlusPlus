package com.jamesdpeters.minecraft.chests.misc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class Settings {

    private static String CHECK_UPDATE = "update-checker";
    private static String CHECK_UPDATE_PERIOD = "update-checker-period";
    private static String LIMIT_CHESTS = "limit-chestlinks";
    private static String LIMIT_CHESTS_NUMBER = "limit-chestlinks-amount";
    private static String SHOULD_ANIMATE_ALL_CHESTS = "should-animate-all-chests";
    private static String RUN_HOPPERS_UNLOADED_CHUNKS = "run-hoppers-unloaded-chunks";
    private static String SHOULD_CHEST_ARMOUR_STAND = "display_chestlink_armour_stands";
    private static String SHOULD_AUTOCRAFT_ARMOUR_STAND = "display_chestlink_armour_stands";
    private static String INVISIBLE_FILTER_ITEM_FRAMES = "set-filter-itemframe-invisible";

    private static Settings cf;
    private FileConfiguration configuration;
    private Plugin plugin;

    private static boolean isUpdateCheckEnabled;
    private static int updateCheckerPeriod;
    private static boolean limitChests;
    private static int limitChestsAmount;
    private static boolean shouldAnimateAllChests;
    private static boolean runHoppersInUnloadedChunks;
    private static boolean shouldDisplayChestLinkStand;
    private static boolean shouldDisplayAutoCraftStand;
    private static boolean filterItemFrameInvisible;

    public static void initConfig(Plugin plugin){
        cf = new Settings();
        cf.plugin = plugin;
        cf.configuration = plugin.getConfig();

        //DEFAULT VALUES
        cf.configuration.addDefault(CHECK_UPDATE,true);
        cf.configuration.addDefault(CHECK_UPDATE_PERIOD,60*60);
        cf.configuration.addDefault(LIMIT_CHESTS,false);
        cf.configuration.addDefault(LIMIT_CHESTS_NUMBER,0);
        cf.configuration.addDefault(SHOULD_ANIMATE_ALL_CHESTS,true);
        cf.configuration.addDefault(RUN_HOPPERS_UNLOADED_CHUNKS,false);
        cf.configuration.addDefault(SHOULD_CHEST_ARMOUR_STAND,true);
        cf.configuration.addDefault(SHOULD_AUTOCRAFT_ARMOUR_STAND,true);
        cf.configuration.addDefault(INVISIBLE_FILTER_ITEM_FRAMES, false);

        cf.configuration.options().copyDefaults(true);
        cf.plugin.saveConfig();

        reloadConfig();
    }

    private static void save(){
        cf.plugin.saveConfig();
    }

    public static void reloadConfig(){
        cf.configuration = cf.plugin.getConfig();

        isUpdateCheckEnabled = cf.configuration.getBoolean(CHECK_UPDATE);
        updateCheckerPeriod  = cf.configuration.getInt(CHECK_UPDATE_PERIOD);
        limitChests = cf.configuration.getBoolean(LIMIT_CHESTS);
        limitChestsAmount = cf.configuration.getInt(LIMIT_CHESTS_NUMBER);
        shouldAnimateAllChests = cf.configuration.getBoolean(SHOULD_ANIMATE_ALL_CHESTS);
        runHoppersInUnloadedChunks = cf.configuration.getBoolean(RUN_HOPPERS_UNLOADED_CHUNKS);
        shouldDisplayChestLinkStand = cf.configuration.getBoolean(SHOULD_CHEST_ARMOUR_STAND);
        shouldDisplayAutoCraftStand = cf.configuration.getBoolean(SHOULD_AUTOCRAFT_ARMOUR_STAND);
        filterItemFrameInvisible = cf.configuration.getBoolean(INVISIBLE_FILTER_ITEM_FRAMES);
    }

    /**
     * GETTERS AND SETTERS
     */
    public static boolean isUpdateCheckEnabled() {
        return isUpdateCheckEnabled;
    }
    public static int getUpdateCheckerPeriodTicks() { return 20*updateCheckerPeriod;}
    public static boolean isLimitChests() { return limitChests; }
    public static int getLimitChestsAmount() { return  limitChestsAmount; }
    public static boolean isShouldAnimateAllChests() {
        return shouldAnimateAllChests;
    }
    public static boolean isRunHoppersInUnloadedChunks() {
        return runHoppersInUnloadedChunks;
    }
    public static boolean isShouldDisplayChestLinkStand() {
        return shouldDisplayChestLinkStand;
    }
    public static boolean isShouldDisplayAutoCraftStand() {
        return shouldDisplayAutoCraftStand;
    }
    public static boolean isFilterItemFrameInvisible() {
        return filterItemFrameInvisible;
    }
}
