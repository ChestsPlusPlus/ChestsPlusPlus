package com.jamesdpeters.chestsplusplus.migration;

import static java.util.stream.Collectors.toMap;

import com.jamesdpeters.chestsplusplus.migration.MigrationStateStore.StateRow;
import com.jamesdpeters.chestsplusplus.persistence.Persistence;
import com.jamesdpeters.chestsplusplus.persistence.RecordTable;
import com.jamesdpeters.chestsplusplus.persistence.RowStore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.Nullable;

/** {@link MigrationState}: one {@code migration_state} row per key. */
@RequiredArgsConstructor
public final class MigrationStateStore implements RowStore<String, StateRow> {

    public record StateRow(String key, String value) {}

    @Getter private final RecordTable<StateRow> table = new RecordTable<>(StateRow.class, "migration_state", "key");

    @Getter private final Persistence persistence;
    private final MigrationState state;

    @Override
    public @Nullable StateRow snapshot(String key) {
        String value = state.get(key);
        return value == null ? null : new StateRow(key, value);
    }

    @Override
    public void load(Handle handle) {
        state.load(table.all(handle).stream().collect(toMap(StateRow::key, StateRow::value)));
    }
}
