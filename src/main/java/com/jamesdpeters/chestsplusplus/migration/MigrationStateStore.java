package com.jamesdpeters.chestsplusplus.migration;

import static java.util.stream.Collectors.toMap;

import com.jamesdpeters.chestsplusplus.migration.MigrationStateStore.StateRow;
import com.jamesdpeters.chestsplusplus.persistence.Persistence;
import com.jamesdpeters.chestsplusplus.persistence.RecordTable;
import com.jamesdpeters.chestsplusplus.persistence.Store;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.Nullable;

/** {@link MigrationState}: one {@code migration_state} row per key. */
@RequiredArgsConstructor
public final class MigrationStateStore implements Store<String, StateRow> {

    public record StateRow(String key, String value) {}

    private static final RecordTable<StateRow> STATE = new RecordTable<>(StateRow.class, "migration_state", "key");

    private final Persistence persistence;
    private final MigrationState state;

    public void markDirty(String key) {
        persistence.markDirty(this, key);
    }

    @Override
    public @Nullable StateRow snapshot(String key) {
        String value = state.get(key);
        return value == null ? null : new StateRow(key, value);
    }

    @Override
    public void write(Handle handle, List<StateRow> rows) {
        STATE.upsert(handle, rows);
    }

    @Override
    public void delete(Handle handle, List<String> keys) {
        STATE.deleteWhere(handle, "key", keys);
    }

    @Override
    public void load(Handle handle) {
        state.load(STATE.all(handle).stream().collect(toMap(StateRow::key, StateRow::value)));
    }
}
