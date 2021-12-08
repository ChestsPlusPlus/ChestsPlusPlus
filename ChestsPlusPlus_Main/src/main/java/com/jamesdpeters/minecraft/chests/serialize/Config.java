package com.jamesdpeters.minecraft.chests.serialize;

import com.google.common.base.Charsets;
import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.storage.abstracts.AbstractStorage;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageType;
import com.jamesdpeters.minecraft.chests.storage.autocraft.AutoCraftingStorageType;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorageType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Config {

    private static ConfigStorage store;
    private static FileConfiguration config;

    private static final String saveName = "/data/storage.yml";

    /* ALL STORAGE TYPES */
    private static ChestLinkStorageType chestLinkStorageType;
    private static AutoCraftingStorageType autoCraftingStorageType;

    private static List<StorageType<? extends AbstractStorage>> storageTypes;

    public Config() {
        legacyConverter();
        configConverter();

        try {
            config = YamlConfiguration.loadConfiguration(getStorageFile());
        } catch (IllegalArgumentException | IOException e) {
            ChestsPlusPlus.PLUGIN.getLogger().severe("Config was null or couldn't be read!");
            config = new YamlConfiguration();
        }
        try {
            store = (ConfigStorage) config.get("chests++", new ConfigStorage());
        } catch (Exception e) {
            store = new ConfigStorage();
            saveASync();
        }
        chestLinkStorageType = new ChestLinkStorageType(store);
        autoCraftingStorageType = new AutoCraftingStorageType(store);

        //Add each storage type to a list.
        storageTypes = new ArrayList<>();
        storageTypes.add(chestLinkStorageType);
        storageTypes.add(autoCraftingStorageType);

        storageTypes.forEach(StorageType::onConfigLoad);
    }

    public static void save() {
        if (config == null) config = new YamlConfiguration();
        config.set("chests++", store);
        try {
            config.save(getStorageFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveASync() {
        Bukkit.getScheduler().runTaskAsynchronously(ChestsPlusPlus.PLUGIN, Config::save);
    }

    public static AutoCraftingStorageType getAutoCraft() {
        return autoCraftingStorageType;
    }

    public static ChestLinkStorageType getChestLink() {
        return chestLinkStorageType;
    }

    public static List<StorageType<? extends AbstractStorage>> getStorageTypes() {
        return storageTypes != null ? storageTypes : new ArrayList<>();
    }

    public static ConfigStorage getStore() {
        return store;
    }

    //TODO This needs improving
    public static OfflinePlayer getOfflinePlayer(String name) {
        for (String uuid : store.chests.keySet()) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            if (offlinePlayer.getName() != null && offlinePlayer.getName().equals(name)) return offlinePlayer;
        }
        return null;
    }

    private static File getStorageFile() throws IOException {
        File pluginDataFolder = ChestsPlusPlus.PLUGIN.getDataFolder();
        File file = new File(pluginDataFolder, saveName);
        file.getParentFile().mkdirs();
        if (!file.exists()) file.createNewFile();
        return file;
    }

    private File getLegacyFile() {
        return new File("chests.yml");
    }

    private void legacyConverter() {
        File legacyFile = getLegacyFile();
        if (!legacyFile.exists()) return;
        ChestsPlusPlus.PLUGIN.getLogger().info("Found a Legacy config! Converting to new data-format and moving to: /plugins/ChestsPlusPlus/data/storage.yml");
        ChestsPlusPlus.PLUGIN.getLogger().info("If you are having issues with data-loss the plugin may not have permissions to delete the legacy file 'chests.yml'");

        try {
            Path path = Paths.get(legacyFile.toURI());
            String content = Files.readString(path, Charsets.UTF_8);
            content = legacyContentConverter(content);
            Files.writeString(getStorageFile().toPath(), content, Charsets.UTF_8);
            legacyFile.createNewFile();
            legacyFile.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String legacyContentConverter(String content) {
        content = content.replaceAll("==: LinkedChest", "==: ConfigStorage");
        content = content.replaceAll("==: com.jamesdpeters.minecraft.chests.storage.InventoryStorage", "==: ChestLinkStorage");
        content = content.replaceAll("==: com.jamesdpeters.minecraft.chests.serialize.InventoryStorage", "==: ChestLinkStorage");
        return content;
    }

    private void configConverter() {
        try {
            Path path = Paths.get(getStorageFile().toURI());
            String content = Files.readString(path, Charsets.UTF_8);
            content = content.replaceAll("==: Recipe", "==: C++Recipe");
            Files.writeString(getStorageFile().toPath(), content, Charsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}