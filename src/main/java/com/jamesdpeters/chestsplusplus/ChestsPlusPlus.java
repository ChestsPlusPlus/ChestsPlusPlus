package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.autocraft.AutoCraftListener;
import com.jamesdpeters.chestsplusplus.autocraft.AutoCraftService;
import com.jamesdpeters.chestsplusplus.autocraft.CraftingBackend;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkListener;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkService;
import com.jamesdpeters.chestsplusplus.chestlink.GolemBridge;
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
import com.jamesdpeters.chestsplusplus.migration.MigrationState;
import com.jamesdpeters.chestsplusplus.migration.MigrationStateStore;
import com.jamesdpeters.chestsplusplus.migration.V2Cleanup;
import com.jamesdpeters.chestsplusplus.migration.V2CleanupStore;
import com.jamesdpeters.chestsplusplus.migration.V2ConfigMigrator;
import com.jamesdpeters.chestsplusplus.migration.V2FilterConversion;
import com.jamesdpeters.chestsplusplus.migration.V2FilterMigration;
import com.jamesdpeters.chestsplusplus.migration.V2Importer;
import com.jamesdpeters.chestsplusplus.migration.V2LocationRecovery;
import com.jamesdpeters.chestsplusplus.migration.V2Migration;
import com.jamesdpeters.chestsplusplus.migration.V2PendingLocationStore;
import com.jamesdpeters.chestsplusplus.migration.V2PendingLocations;
import com.jamesdpeters.chestsplusplus.migration.V2WorldCleanup;
import com.jamesdpeters.chestsplusplus.migration.WorldUids;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.persistence.Database;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore.LoadedGroup;
import com.jamesdpeters.chestsplusplus.persistence.Persistence;
import com.jamesdpeters.chestsplusplus.persistence.TrustStore;
import com.jamesdpeters.chestsplusplus.ui.UiService;
import com.jamesdpeters.chestsplusplus.ui.menu.MenuListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jdbi.v3.core.JdbiException;
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
            V2ConfigMigrator.migrate(this);
            services = new Services(this, loadSettings(), loadMessages());
            registerFeatures(services);
            openDatabase(services);
            startMigration(services);
        } catch (IOException | JdbiException | IllegalStateException e) {
            log.error("Could not start ChestsPlusPlus; disabling", e);
            getSLF4JLogger();
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

        GroupActions actions = services.add(GroupActions.class, new GroupActions(services, links, chestLinks));
        MenuListener menus = services.add(MenuListener.class, new MenuListener(this));
        services.add(UiService.class, new UiService(services, links, actions, menus));
        services.add(FilterService.class, new FilterService(this, new FilterCodec(this), ItemGrouping.fromServerTags(), services::settings));
        services.add(V2Cleanup.class, new V2Cleanup());
        services.add(MigrationState.class, new MigrationState());
        V2PendingLocations pending = services.add(V2PendingLocations.class, new V2PendingLocations());
        services.groups().onRemove(pending::removeGroup);
    }

    private void openDatabase(Services services) throws IOException {
        File dataFolder = getDataFolder();
        if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) throw new IOException("Cannot create " + dataFolder);
        Database database = Database.open("jdbc:sqlite:" + new File(dataFolder, DATABASE_FILE).getAbsolutePath());
        Persistence persistence = new Persistence(database, this::runOnMainThread);
        GroupStore groupStore = new GroupStore(persistence, services.groups(), services.nodes(), attachLoaded(services));
        TrustStore trustStore = new TrustStore(persistence, services.trust());
        persistence.register(groupStore);
        persistence.register(trustStore);
        services.trust().onChange(trustStore::markDirty);
        registerMigrationStores(services, persistence);
        services.persistence(persistence, groupStore, trustStore);
        persistence.load();
        log.info("Loaded {} group(s) and {} linked block(s)", services.groups().size(), services.nodes().size());
    }

    private static void registerMigrationStores(Services services, Persistence persistence) {
        V2Cleanup cleanup = services.get(V2Cleanup.class);
        MigrationState state = services.get(MigrationState.class);
        V2CleanupStore cleanupStore = new V2CleanupStore(persistence, cleanup);
        MigrationStateStore stateStore = new MigrationStateStore(persistence, state);
        persistence.register(cleanupStore);
        persistence.register(stateStore);
        V2PendingLocationStore pendingStore = new V2PendingLocationStore(persistence, services.groups(), services.get(V2PendingLocations.class));
        persistence.register(pendingStore);
        services.get(V2PendingLocations.class).onChange(pendingStore::markDirty);
        cleanup.onChange(cleanupStore::markDirty);
        state.onChange(stateStore::markDirty);
    }

    /** Builds the v2 migration and runs the one-time import if a v2 install left its data behind. */
    private void startMigration(Services services) {
        V2Cleanup cleanup = services.get(V2Cleanup.class);
        MigrationState state = services.get(MigrationState.class);
        DisplayService displays = services.get(DisplayService.class);
        V2WorldCleanup worldCleanup = services.add(V2WorldCleanup.class, new V2WorldCleanup(cleanup, services.nodes(), services.groups(),
                services.groupStore(), displays, new NamespacedKey(this, "chestsplusplus")));
        V2FilterMigration filters = services.add(V2FilterMigration.class,
                new V2FilterMigration(state, services.get(FilterService.class), new NamespacedKey(this, "v2_filters_scanned")));
        V2FilterConversion conversion = services.add(V2FilterConversion.class,
                new V2FilterConversion(this, services, state, filters, cleanup, worldCleanup, services.get(V2PendingLocations.class)));
        V2Importer importer = new V2Importer(services.groups(), services.nodes(), services.trust(), services.groupStore(), cleanup,
                new WorldUids(getServer(), getServer()::getWorldContainer), state, services.get(V2PendingLocations.class), services::settings,
                displays::nodeAdded);
        V2LocationRecovery recovery = services.add(V2LocationRecovery.class,
                new V2LocationRecovery(getServer(), services.get(V2PendingLocations.class),
                        services.groups(), services.nodes(), services.groupStore(), services.get(LinkService.class), displays, cleanup,
                        worldCleanup));
        services.add(V2Migration.class,
                new V2Migration(this, services, importer, cleanup, worldCleanup, state, filters, conversion, recovery,
                        services.get(V2PendingLocations.class)))
                .importOnStartup();
    }

    private static Consumer<LoadedGroup> attachLoaded(Services services) {
        ChestLinkService chestLinks = services.get(ChestLinkService.class);
        AutoCraftService autoCraft = services.get(AutoCraftService.class);
        return loaded -> {
            switch (loaded.group()) {
                case ChestLinkGroup chest -> chestLinks.attachLoaded(chest, loaded.items());
                case AutoCraftGroup craft -> autoCraft.resolveLoaded(craft);
            }
        };
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
                new GolemBridge(services, services.get(ChestLinkService.class)),
                services.get(MenuListener.class),
                new AutoCraftListener(services, links, services.get(AutoCraftService.class)),
                new FilterListener(services, services.get(FilterService.class), links, services.get(ChestLinkService.class)),
                services.get(V2LocationRecovery.class),
                services.get(V2WorldCleanup.class),
                services.get(V2FilterMigration.class),
                services.get(V2Migration.class));
        listeners.forEach(listener -> getServer().getPluginManager().registerEvents(listener, this));
    }

    /** A few central tickers rather than one task per group. */
    private void startTickers(Services services) {
        Tickers tickers = services.tickers();
        Persistence persistence = services.persistence();
        tickers.everyInterval("persistence-flush", () -> services.settings().storage().flushIntervalSeconds() * 20, ticks -> persistence.flush());
        tickers.every("displays", 1, services.get(DisplayService.class)::tick);
        tickers.every("autocraft", 1, services.get(AutoCraftService.class)::tick);
        tickers.every("v2-filter-conversion", 1, services.get(V2FilterConversion.class)::tick);
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
        if (current != null) current.persistence().flush();
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
