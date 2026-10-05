package com.jamesdpeters.chestsplusplus.persistence;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;

import com.jamesdpeters.chestsplusplus.access.TrustService;
import com.jamesdpeters.chestsplusplus.persistence.TrustStore.TrustSnapshot;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jdbi.v3.core.Handle;

/** Each owner's trusted players. An owner who trusts nobody still writes a snapshot, which clears their rows. */
@RequiredArgsConstructor
public final class TrustStore implements Store<UUID, TrustSnapshot> {

    public record TrustRow(UUID owner, UUID trusted) {}

    public record TrustSnapshot(UUID owner, List<TrustRow> rows) {}

    private static final RecordTable<TrustRow> TRUST = new RecordTable<>(TrustRow.class, "trust", "owner", "trusted");

    private final Persistence persistence;
    private final TrustService trust;

    public void markDirty(UUID owner) {
        persistence.markDirty(this, owner);
    }

    @Override
    public TrustSnapshot snapshot(UUID owner) {
        return new TrustSnapshot(owner, trust.trustedBy(owner).stream().map(trusted -> new TrustRow(owner, trusted)).toList());
    }

    @Override
    public void write(Handle handle, List<TrustSnapshot> snapshots) {
        List<UUID> owners = snapshots.stream().map(TrustSnapshot::owner).toList();
        TRUST.replaceFor(handle, "owner", owners, snapshots.stream().flatMap(snapshot -> snapshot.rows().stream()).toList());
    }

    @Override
    public void delete(Handle handle, List<UUID> owners) {
        TRUST.deleteWhere(handle, "owner", owners);
    }

    @Override
    public void load(Handle handle) {
        trust.load(TRUST.all(handle).stream()
                .collect(groupingBy(TrustRow::owner, LinkedHashMap::new, mapping(TrustRow::trusted, toCollection(LinkedHashSet<UUID>::new)))));
    }
}
