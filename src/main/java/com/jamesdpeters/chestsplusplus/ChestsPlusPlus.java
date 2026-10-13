package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.autocraft.AutoCraft;
import com.jamesdpeters.chestsplusplus.autocraft.AutoCraftListener;
import com.jamesdpeters.chestsplusplus.autocraft.Recipes;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkListener;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkService;
import com.jamesdpeters.chestsplusplus.chestlink.GolemBridge;
import com.jamesdpeters.chestsplusplus.chestlink.HopperBridge;
import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.CppInventoryHolder;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.core.Shutdown;
import com.jamesdpeters.chestsplusplus.core.Viewers;
import com.jamesdpeters.chestsplusplus.core.scheduler.Tickers;
import com.jamesdpeters.chestsplusplus.filter.FilterListener;
import com.jamesdpeters.chestsplusplus.filter.FilterService;
import com.jamesdpeters.chestsplusplus.integration.MetricsService;
import com.jamesdpeters.chestsplusplus.integration.UpdateChecker;
import com.jamesdpeters.chestsplusplus.link.Checks;
import com.jamesdpeters.chestsplusplus.link.Handlers;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.link.NameTagLinkListener;
import com.jamesdpeters.chestsplusplus.link.NodeListener;
import com.jamesdpeters.chestsplusplus.link.SignLinkListener;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.migration.V2ConfigMigrator;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.persistence.Database;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore.LoadedGroup;
import com.jamesdpeters.chestsplusplus.persistence.Persistence;
import com.jamesdpeters.chestsplusplus.persistence.TrustStore;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.entity.Player;
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

    private @Nullable Shutdown shutdown;
    private @Nullable Components components;

    @Override
    public void onEnable() {
        try {
            V2ConfigMigrator.migrate(getDataFolder());
            Services services = new Services(this, loadSettings(), loadMessages());
            // Set before anything can fail, so onDisable undoes whatever was started.
            this.shutdown = services.shutdown();
            Features features = Features.create(services);
            Migration.Models models = Migration.Models.create(services.groups());
            Storage storage = openDatabase(services, features, models);
            Migration migration = Migration.start(services, features, storage, models);
            Components components = new Components(services, features, storage, migration);
            this.components = components;
            registerListeners(components);
            startTickers(components);
            startIntegrations(services);
            refreshLoadedChunks(components);
        } catch (IOException | RuntimeException e) {
            log.error("Could not start ChestsPlusPlus; disabling", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /** Opens and loads the database. The v2 models' stores register here too, so they load with everything else. */
    private Storage openDatabase(Services services, Features features, Migration.Models models) throws IOException {
        File dataFolder = getDataFolder();
        if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) throw new IOException("Cannot create " + dataFolder);
        Database database = Database.open("jdbc:sqlite:" + new File(dataFolder, DATABASE_FILE).getAbsolutePath());
        Persistence persistence = new Persistence(database, services.mainThread()::nextTick);
        services.shutdown().add("persistence", persistence::close);
        GroupStore groupStore = persistence.register(new GroupStore(persistence, services.groups(), services.nodes(), attachLoaded(features)));
        services.groups().listen(groupStore::markDirty);
        TrustStore trustStore = persistence.register(new TrustStore(persistence, services.trust()));
        services.trust().listen(trustStore::markDirty);
        models.registerStores(persistence, services.groups());
        persistence.load();
        log.info("Loaded {} group(s) and {} linked block(s)", services.groups().size(), services.nodes().size());
        return new Storage(persistence, groupStore);
    }

    private static Consumer<LoadedGroup> attachLoaded(Features features) {
        ChestLinkService chestLinks = features.chestLinks();
        Recipes recipes = features.autoCraft().recipes();
        return loaded -> {
            switch (loaded.group()) {
                case ChestLinkGroup chest -> chestLinks.attachLoaded(chest, loaded.items());
                case AutoCraftGroup craft -> recipes.resolveLoaded(craft);
            }
        };
    }

    private void registerListeners(Components components) {
        Services services = components.services();
        Features features = components.features();
        LinkService links = features.links();
        Handlers handlers = features.handlers();
        Checks checks = features.checks();
        ChestLinkService chestLinks = features.chestLinks();
        AutoCraft autoCraft = features.autoCraft();
        List<Listener> listeners = new ArrayList<>(List.of(
                this,
                new NodeListener(services, links, checks, handlers, features.displays(), features.linkItem()),
                new SignLinkListener(services.mainThread(), services::settings, links),
                new NameTagLinkListener(services, links, features.protection(), handlers, checks),
                new ChestLinkListener(services, links, chestLinks),
                new HopperBridge(services.mainThread(), services, components.storage().groupStore(), chestLinks),
                new GolemBridge(services, chestLinks),
                features.menus(),
                new AutoCraftListener(services, links, autoCraft.editor(), autoCraft.recipes(), autoCraft.crafter()),
                new FilterListener(services, features.filters(), features.protection(), chestLinks, autoCraft.crafter()::inventoryChanged)));
        listeners.addAll(components.migration().listeners());
        listeners.forEach(listener -> getServer().getPluginManager().registerEvents(listener, this));
        services.shutdown().add("open inventories", this::closePluginInventories);
    }

    /**
     * Ghost items and menu icons are only kept in place by our listeners, which Paper removes on disable. Close every view while they
     * still run, and before persistence closes, so the close handlers can still save.
     */
    private void closePluginInventories() {
        Viewers.withHolder(CppInventoryHolder.class).forEach(Player::closeInventory);
    }

    /** A few central tickers rather than one task per group. */
    private static void startTickers(Components components) {
        Services services = components.services();
        Tickers tickers = services.tickers();
        Persistence persistence = components.storage().persistence();
        services.shutdown().add("tickers", tickers::stopAll);
        tickers.everyInterval("persistence-flush", () -> services.settings().storage().flushIntervalSeconds() * 20, ticks -> persistence.flush());
        tickers.every("displays", 1, components.features().displays()::tick);
        tickers.every("autocraft", 1, components.features().autoCraft().crafter()::tick);
        tickers.every("v2-filter-conversion", 1, components.migration()::tick);
    }

    private void startIntegrations(Services services) {
        MetricsService metrics = new MetricsService();
        services.shutdown().add("metrics", metrics::stop);
        metrics.start(this, services);
        UpdateChecker updates = new UpdateChecker(services, getPluginMeta().getVersion());
        services.shutdown().add("update checker", updates::stop);
        getServer().getPluginManager().registerEvents(updates, this);
        updates.start();
    }

    private void refreshLoadedChunks(Components components) {
        Features features = components.features();
        // Before the displays, so nodes whose block is gone never get one.
        features.links().unlinkChangedBlocksInLoadedChunks();
        features.displays().refreshAll();
        features.filters().scanLoadedChunks();
        if (components.services().settings().features().hopperFilters()) warnIfMoveEventDisabled(components.services());
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
        Shutdown current = shutdown;
        shutdown = null;
        components = null;
        if (current != null) current.run();
    }

    /** Everything enable built, or null while the plugin is not enabled. */
    public @Nullable Components components() {
        return components;
    }

    /** Re-reads config.yml and messages.yml without re-running enable. */
    public void reload() throws IOException {
        Components current = components;
        if (current == null) throw new IllegalStateException("ChestsPlusPlus is not enabled");
        current.services().reconfigure(loadSettings(), loadMessages());
        Features features = current.features();
        features.links().closeDisabledViews();
        for (var group : current.services().groups().all(GroupType.CHESTLINK)) features.chestLinks().retitle((ChestLinkGroup) group);
        features.displays().refreshAll();
        features.filters().reload();
    }

    @EventHandler
    void onWorldSave(WorldSaveEvent event) {
        Components current = components;
        if (current != null) current.storage().persistence().flush();
    }

    private Settings loadSettings() throws IOException {
        return Settings.update(getDataFolder().toPath().resolve("config.yml"));
    }

    private Messages loadMessages() throws IOException {
        try (InputStream defaults = getResource("messages.yml")) {
            if (defaults == null) throw new IOException("messages.yml missing from the plugin jar");
            return Messages.load(defaults, new File(getDataFolder(), "messages.yml"));
        }
    }
}
