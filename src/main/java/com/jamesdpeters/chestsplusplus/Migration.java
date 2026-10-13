package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.migration.MigrationState;
import com.jamesdpeters.chestsplusplus.migration.MigrationStateStore;
import com.jamesdpeters.chestsplusplus.migration.V2Cleanup;
import com.jamesdpeters.chestsplusplus.migration.V2CleanupStore;
import com.jamesdpeters.chestsplusplus.migration.V2FilterConversion;
import com.jamesdpeters.chestsplusplus.migration.V2FilterMigration;
import com.jamesdpeters.chestsplusplus.migration.V2Importer;
import com.jamesdpeters.chestsplusplus.migration.V2LocationRecovery;
import com.jamesdpeters.chestsplusplus.migration.V2Migration;
import com.jamesdpeters.chestsplusplus.migration.V2PendingLocationStore;
import com.jamesdpeters.chestsplusplus.migration.V2PendingLocations;
import com.jamesdpeters.chestsplusplus.migration.V2WorldCleanup;
import com.jamesdpeters.chestsplusplus.migration.WorldUids;
import com.jamesdpeters.chestsplusplus.model.GroupListener;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.persistence.Persistence;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** Everything the v2 upgrade needs, so removing it later means removing this record and the migration package. */
public record Migration(Models models, V2WorldCleanup worldCleanup, V2FilterMigration filters, V2FilterConversion conversion,
        V2LocationRecovery recovery, V2Migration v2) {

    /** The v2 state the stores load into. Built before the database opens, so it can register their stores. */
    public record Models(V2Cleanup cleanup, MigrationState state, V2PendingLocations pending) {

        public static Models create(GroupRegistry groups) {
            V2PendingLocations pending = new V2PendingLocations();
            groups.listen(GroupListener.removals(group -> pending.removeGroup(group.id())));
            return new Models(new V2Cleanup(), new MigrationState(), pending);
        }

        void registerStores(Persistence persistence, GroupRegistry groups) {
            V2CleanupStore cleanupStore = persistence.register(new V2CleanupStore(persistence, cleanup));
            MigrationStateStore stateStore = persistence.register(new MigrationStateStore(persistence, state));
            V2PendingLocationStore pendingStore = persistence.register(new V2PendingLocationStore(persistence, groups, pending));
            pending.listen(pendingStore::markDirty);
            cleanup.listen(cleanupStore::markDirty);
            state.listen(stateStore::markDirty);
        }
    }

    /** Builds the v2 migration and runs the one-time import if a v2 install left its data behind. */
    public static Migration start(Services services, Features features, Storage storage, Models models) {
        JavaPlugin plugin = services.plugin();
        Server server = plugin.getServer();
        DisplayService displays = features.displays();
        V2Cleanup cleanup = models.cleanup();
        MigrationState state = models.state();
        V2PendingLocations pending = models.pending();
        V2WorldCleanup worldCleanup = new V2WorldCleanup(cleanup, services.nodes(), services.groups(), displays,
                new NamespacedKey(plugin, "chestsplusplus"));
        V2FilterMigration filters = new V2FilterMigration(state, features.filters(), new NamespacedKey(plugin, "v2_filters_scanned"));
        V2FilterConversion conversion = new V2FilterConversion(plugin, services, state, filters, cleanup, worldCleanup, pending);
        V2Importer importer = new V2Importer(services.groups(), services.nodes(), services.trust(), storage.groupStore(), storage.persistence(),
                cleanup, new WorldUids(server, server::getWorldContainer), state, pending, services::settings, displays::nodeAdded);
        V2LocationRecovery recovery = new V2LocationRecovery(server, pending, services.groups(), services.nodes(), storage.persistence(),
                features.handlers(), displays, cleanup, worldCleanup);
        V2Migration v2 = new V2Migration(plugin, services, storage.persistence(), importer, cleanup, worldCleanup, state, filters, conversion,
                recovery, pending);
        v2.importOnStartup();
        return new Migration(models, worldCleanup, filters, conversion, recovery, v2);
    }

    public List<Listener> listeners() {
        return List.of(recovery, worldCleanup, filters, v2);
    }

    public void tick() {
        conversion.tick();
    }
}
