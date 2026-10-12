package com.jamesdpeters.chestsplusplus.persistence;

import java.util.List;
import org.jdbi.v3.core.Handle;

/** A single table with one row per dirty key; composite keys are records with the table's key components. */
public interface RowStore<K, R extends Record> extends TrackedStore<K, R> {

    RecordTable<R> table();

    @Override
    default void write(Handle handle, List<R> rows) {
        table().upsert(handle, rows);
    }

    @Override
    default void delete(Handle handle, List<K> keys) {
        table().deleteKeys(handle, keys);
    }
}
