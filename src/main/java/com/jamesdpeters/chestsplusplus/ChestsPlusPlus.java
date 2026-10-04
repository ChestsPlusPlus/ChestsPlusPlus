package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkListener;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkService;
import com.jamesdpeters.chestsplusplus.chestlink.HopperBridge;
import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.display.DisplayLayout;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.link.GroupActions;
import com.jamesdpeters.chestsplusplus.link.LinkItem;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.link.NodeListener;
import com.jamesdpeters.chestsplusplus.link.SignLinkListener;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.persistence.Database;
import com.jamesdpeters.chestsplusplus.persistence.PersistenceService;
import com.jamesdpeters.chestsplusplus.ui.UiService;
import com.jamesdpeters.chestsplusplus.ui.menu.MenuListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import org.bukkit.block.Chest;
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
        // 1. Settings and messages.
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

        // 2. Feature services.
        DisplayService displays = services.add(
                DisplayService.class,
                new DisplayService(this, services.groups(), services.nodes(), services::settings));
        displays.surfaces(block -> block.getState(false) instanceof Chest
                ? DisplayLayout.Surface.CHEST
                : DisplayLayout.Surface.FULL_BLOCK);
        LinkService links = services.add(LinkService.class, new LinkService(services, displays));
        LinkItem linkItems = services.add(LinkItem.class, new LinkItem(this));
        ChestLinkService chestLinks = services.add(ChestLinkService.class, new ChestLinkService(services, displays));
        links.register(chestLinks);
        displays.register(GroupType.CHESTLINK, chestLinks);
        GroupActions actions = services.add(GroupActions.class, new GroupActions(services, links));
        MenuListener menus = services.add(MenuListener.class, new MenuListener(this));
        services.add(UiService.class, new UiService(services, links, actions, menus));

        // 3. Database and model.
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
            services.persistence(persistence);
            int loaded = persistence.load(loadedGroup -> {
                if (loadedGroup.group() instanceof ChestLinkGroup chest) {
                    chestLinks.attachLoaded(chest, loadedGroup.contents());
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
        this.services = services;

        // 4. Listeners.
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(this, this);
        pluginManager.registerEvents(new NodeListener(services, links, displays, linkItems), this);
        pluginManager.registerEvents(new SignLinkListener(this, links), this);
        pluginManager.registerEvents(new ChestLinkListener(services, links, chestLinks), this);
        pluginManager.registerEvents(new HopperBridge(services), this);
        pluginManager.registerEvents(menus, this);

        // 5. Central tickers (plan §9: no per-group tasks).
        services.tickers().every("persistence", 1, persistence::tick);
        int[] seconds = {0};
        services.tickers().every("persistence-flush", 20, () -> {
            if (++seconds[0] >= services.settings().storage().flushIntervalSeconds()) {
                seconds[0] = 0;
                persistence.requestFlush();
            }
        });
        services.tickers().every("displays", 1, displays::tick);

        // 6. Displays for chunks that are already loaded.
        displays.refreshAll();
    }

    @Override
    public void onDisable() {
        Services current = services;
        services = null;
        if (current == null) return;
        current.tickers().stopAll();
        current.get(DisplayService.class).despawnAll();
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
        ChestLinkService chestLinks = current.get(ChestLinkService.class);
        for (var group : current.groups().all(GroupType.CHESTLINK)) chestLinks.retitle((ChestLinkGroup) group);
        current.get(DisplayService.class).refreshAll();
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
