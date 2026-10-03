package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkHolder;
import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.persistence.Database;
import com.jamesdpeters.chestsplusplus.persistence.PersistenceService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Plugin entry point. Owns the lifecycle in a fixed order (plan §3.4); {@code /cpp reload} only swaps settings and
 * messages and never re-runs enable.
 */
// Not final: MockBukkit loads plugins through a generated subclass.
public class ChestsPlusPlus extends JavaPlugin implements Listener {

    public static final String DATABASE_FILE = "data.db";

    private @Nullable Services services;

    @Override
    public void onEnable() {
        Settings settings;
        Messages messages;
        try {
            settings = loadSettings();
            messages = loadMessages();
        } catch (IOException e) {
            getSLF4JLogger().error("Could not load configuration; disabling ChestsPlusPlus", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Services services = new Services(this, settings, messages);

        PersistenceService persistence;
        try {
            File dataFolder = getDataFolder();
            if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) throw new IOException("Cannot create " + dataFolder);
            Database database = Database.open("jdbc:sqlite:" + new File(dataFolder, DATABASE_FILE).getAbsolutePath());
            persistence = new PersistenceService(
                    database,
                    services.groups(),
                    services.nodes(),
                    services.trust(),
                    getSLF4JLogger(),
                    task -> {
                        if (isEnabled()) getServer().getScheduler().runTask(this, task);
                    },
                    () -> services.settings().storage().maxSerialisationsPerTick());
            int loaded = persistence.load(loadedGroup -> {
                if (loadedGroup.group() instanceof ChestLinkGroup chest) {
                    ChestLinkHolder.attach(
                            chest,
                            services.messages().get(Message.CHESTLINK_TITLE, Messages.text("group", chest.name())),
                            loadedGroup.contents());
                }
            });
            getSLF4JLogger()
                    .info(
                            "Loaded {} group(s) and {} linked block(s)",
                            loaded,
                            services.nodes().size());
        } catch (IOException | SQLException e) {
            getSLF4JLogger().error("Could not open the ChestsPlusPlus database; disabling", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        services.persistence(persistence);
        this.services = services;

        getServer().getPluginManager().registerEvents(this, this);
        services.tickers().every("persistence", 1, persistence::tick);
        int[] seconds = {0};
        services.tickers().every("persistence-flush", 20, () -> {
            if (++seconds[0] >= services.settings().storage().flushIntervalSeconds()) {
                seconds[0] = 0;
                persistence.requestFlush();
            }
        });
    }

    @Override
    public void onDisable() {
        Services current = services;
        services = null;
        if (current == null) return;
        current.tickers().stopAll();
        current.persistence().close();
    }

    /** The live services, or null while the plugin is not enabled. */
    public @Nullable Services services() {
        return services;
    }

    /** Re-reads config.yml and messages.yml and applies them without re-running enable. */
    public void reload() throws IOException {
        Services current = services;
        if (current == null) throw new IllegalStateException("ChestsPlusPlus is not enabled");
        current.reconfigure(loadSettings(), loadMessages());
    }

    @EventHandler
    void onWorldSave(WorldSaveEvent event) {
        Services current = services;
        if (current != null) current.persistence().requestFlush();
    }

    private Settings loadSettings() {
        saveDefaultConfig();
        reloadConfig();
        return Settings.from(getConfig());
    }

    private Messages loadMessages() throws IOException {
        try (InputStream defaults = getResource("messages.yml")) {
            if (defaults == null) throw new IOException("messages.yml missing from the plugin jar");
            return Messages.load(defaults, new File(getDataFolder(), "messages.yml"));
        }
    }
}
