package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.migration.V2PendingLocationStore.Snapshot;
import com.jamesdpeters.chestsplusplus.migration.V2PendingLocations.LocationRow;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.persistence.Persistence;
import com.jamesdpeters.chestsplusplus.persistence.RecordTable;
import com.jamesdpeters.chestsplusplus.persistence.Store;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.Nullable;

/** Saved after groups, in the same transaction as node attachments and group deletions. */
@RequiredArgsConstructor
public final class V2PendingLocationStore implements Store<Long, Snapshot> {

    public record Snapshot(long groupId, List<LocationRow> locations) {}

    private static final RecordTable<LocationRow> LOCATIONS = new RecordTable<>(LocationRow.class, "v2_pending_locations", "group_id", "world_name",
            "x", "y", "z");

    private final Persistence persistence;
    private final GroupRegistry groups;
    private final V2PendingLocations pending;

    public void markDirty(long id) {
        persistence.markDirty(this, id);
    }

    @Override
    public @Nullable Snapshot snapshot(Long id) {
        return groups.byId(id) == null ? null : new Snapshot(id, pending.forGroup(id));
    }

    @Override
    public void write(Handle handle, List<Snapshot> snapshots) {
        LOCATIONS.replaceFor(handle, "group_id", snapshots.stream().map(Snapshot::groupId).toList(),
                snapshots.stream().flatMap(snapshot -> snapshot.locations().stream()).toList());
    }

    @Override
    public void delete(Handle handle, List<Long> ids) {
        LOCATIONS.deleteWhere(handle, "group_id", ids);
    }

    @Override
    public void load(Handle handle) {
        pending.load(LOCATIONS.all(handle));
    }
}
