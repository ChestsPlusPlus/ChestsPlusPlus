package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.autocraft.AutoCraftListener;
import com.jamesdpeters.chestsplusplus.autocraft.AutoCraftService;
import com.jamesdpeters.chestsplusplus.autocraft.CraftingBackend;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkListener;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkService;
import com.jamesdpeters.chestsplusplus.chestlink.HopperBridge;
import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.core.scheduler.Tickers;
import com.jamesdpeters.chestsplusplus.display.DisplayLayout.Surface;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.filter.FilterCodec;
import com.jamesdpeters.chestsplusplus.filter.FilterListener;
import com.jamesdpeters.chestsplusplus.filter.FilterService;
import com.jamesdpeters.chestsplusplus.filter.ItemGrouping;
import com.jamesdpeters.chestsplusplus.integration.MetricsService;
import com.jamesdpeters.chestsplusplus.integration.UpdateChecker;
import com.jamesdpeters.chestsplusplus.link.GroupActions;
import com.jamesdpeters.chestsplusplus.link.LinkItem;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.link.NameTagLinkListener;
import com.jamesdpeters.chestsplusplus.link.NodeListener;
import com.jamesdpeters.chestsplusplus.link.SignLinkListener;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
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
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/** Plugin entry point. {@code /cpp reload} only swaps settings and messages; it never re-runs enable. */
@Slf4j(topic = ChestsPlusPlus.NAME)
// Not final: MockBukkit loads plugins through a generated subclass.
public class ChestsPlusPlus extends JavaPlugin implements Listener {

    /** The plugin name, which is also the logger topic Paper uses for its plugin logger. */
    public static final String NAME = "ChestsPlusPlus";
    public static final String DATABASE_FILE = "data.db";

    private @Nullable Services services;

    @Override
    public void onEnable() {
        Services services;
        try {
            services = new Services(this, loadSettings(), loadMessages());
            registerFeatures(services);
            openDatabase(services);
        } catch (IOException | SQLException e) {
            log.error("Could not start ChestsPlusPlus; disabling", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.services = services;
        registerListeners(services);
        startTickers(services);
        startIntegrations(services);
        refreshLoadedChunks(services);
    }

    private void registerFeatures(Services services) {
        DisplayService displays = services.add(DisplayService.class,
                new DisplayService(this, services.groups(), services.nodes(), services::settings));
        displays.surfaces(block -> block.getState(false) instanceof Chest ? Surface.CHEST : Surface.FULL_BLOCK);
        LinkService links = services.add(LinkService.class, new LinkService(services, displays));
        services.add(LinkItem.class, new LinkItem(this));

        ChestLinkService chestLinks = services.add(ChestLinkService.class, new ChestLinkService(services, displays));
        links.register(chestLinks);
        displays.register(GroupType.CHESTLINK, chestLinks);

        AutoCraftService autoCraft = services.add(AutoCraftService.class, new AutoCraftService(services, displays, CraftingBackend.bukkit()));
        links.register(autoCraft);
        displays.register(GroupType.AUTOCRAFT, autoCraft);

        GroupActions actions = services.add(GroupActions.class, new GroupActions(services, links));
        MenuListener menus = services.add(MenuListener.class, new MenuListener(this));
        services.add(UiService.class, new UiService(services, links, actions, menus));
        services.add(FilterService.class, new FilterService(this, new FilterCodec(this), ItemGrouping.fromServerTags(), services::settings));
    }

    private void openDatabase(Services services) throws IOException, SQLException {
        File dataFolder = getDataFolder();
        if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) throw new IOException("Cannot create " + dataFolder);
        Database database = Database.open("jdbc:sqlite:" + new File(dataFolder, DATABASE_FILE).getAbsolutePath());
        PersistenceService persistence = new PersistenceService(database, services.groups(), services.nodes(), services.trust(),
                this::runOnMainThread, () -> services.settings().storage().maxSerialisationsPerTick());
        services.persistence(persistence);

        ChestLinkService chestLinks = services.get(ChestLinkService.class);
        AutoCraftService autoCraft = services.get(AutoCraftService.class);
        int loaded = persistence.load(loadedGroup -> {
            switch (loadedGroup.group()) {
                case ChestLinkGroup chest -> chestLinks.attachLoaded(chest, loadedGroup.contents());
                case AutoCraftGroup craft -> autoCraft.resolveLoaded(craft);
            }
        });
        log.info("Loaded {} group(s) and {} linked block(s)", loaded, services.nodes().size());
    }

    private void runOnMainThread(Runnable task) {
        if (isEnabled()) getServer().getScheduler().runTask(this, task);
    }

    private void registerListeners(Services services) {
        LinkService links = services.get(LinkService.class);
        List<Listener> listeners = List.of(
                this,
                new NodeListener(services, links, services.get(DisplayService.class), services.get(LinkItem.class)),
                new SignLinkListener(this, links),
                new NameTagLinkListener(services, links),
                new ChestLinkListener(services, links, services.get(ChestLinkService.class)),
                new HopperBridge(services),
                services.get(MenuListener.class),
                new AutoCraftListener(services, links, services.get(AutoCraftService.class)),
                new FilterListener(services, services.get(FilterService.class), links));
        listeners.forEach(listener -> getServer().getPluginManager().registerEvents(listener, this));
    }

    /** A few central tickers rather than one task per group. */
    private void startTickers(Services services) {
        Tickers tickers = services.tickers();
        PersistenceService persistence = services.persistence();
        tickers.every("persistence", 1, persistence::tick);
        tickers.everyInterval("persistence-flush", () -> services.settings().storage().flushIntervalSeconds() * 20,
                ticks -> persistence.requestFlush());
        tickers.every("displays", 1, services.get(DisplayService.class)::tick);
        tickers.everyInterval("autocraft", () -> services.settings().autocraft().tickInterval(), services.get(AutoCraftService.class)::tick);
    }

    private void startIntegrations(Services services) {
        services.add(MetricsService.class, new MetricsService()).start(this, services);
        UpdateChecker updates = services.add(UpdateChecker.class, new UpdateChecker(services, getPluginMeta().getVersion()));
        getServer().getPluginManager().registerEvents(updates, this);
        updates.start();
    }

    private void refreshLoadedChunks(Services services) {
        services.get(DisplayService.class).refreshAll();
        services.get(FilterService.class).scanLoadedChunks();
        if (services.settings().features().hopperFilters()) warnIfMoveEventDisabled(services);
    }

    /** Diagnostics only: never let reading Paper's config files break enable. */
    private void warnIfMoveEventDisabled(Services services) {
        try {
            for (String world : FilterService.worldsWithMoveEventDisabled(getServer().getWorldContainer(), getServer().getWorlds())) {
                log.warn(services.messages().plain(Message.FILTER_MOVE_EVENT_DISABLED, Messages.text("world", world)));
            }
        } catch (RuntimeException e) {
            log.debug("Could not check hopper.disable-move-event", e);
        }
    }

    @Override
    public void onDisable() {
        Services current = services;
        services = null;
        if (current == null) return;
        current.tickers().stopAll();
        current.get(UpdateChecker.class).stop();
        current.get(MetricsService.class).stop();
        current.get(DisplayService.class).despawnAll();
        current.get(FilterService.class).despawnAll();
        current.persistence().close();
    }

    /** The live services, or null while the plugin is not enabled. */
    public @Nullable Services services() {
        return services;
    }

    /** Re-reads config.yml and messages.yml without re-running enable. */
    public void reload() throws IOException {
        Services current = services;
        if (current == null) throw new IllegalStateException("ChestsPlusPlus is not enabled");
        current.reconfigure(loadSettings(), loadMessages());
        ChestLinkService chestLinks = current.get(ChestLinkService.class);
        for (var group : current.groups().all(GroupType.CHESTLINK)) chestLinks.retitle((ChestLinkGroup) group);
        current.get(DisplayService.class).refreshAll();
        current.get(FilterService.class).refreshDisplays();
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
