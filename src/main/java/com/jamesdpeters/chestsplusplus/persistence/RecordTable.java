package com.jamesdpeters.chestsplusplus.persistence;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.Getter;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.core.statement.PreparedBatch;

/**
 * The routine SQL for a table whose columns are a record's components in snake_case. Table and column names come from code, never from
 * user input. Anything beyond these four operations belongs in the store that needs it, as a plain JDBI call.
 */
public final class RecordTable<R extends Record> {

    private final Class<R> type;
    private final String table;
    @Getter private final List<String> columns;
    @Getter private final String upsertSql;
    private final String allSql;

    /** {@code key} is the primary key (or other unique columns) the upsert conflicts on. */
    public RecordTable(Class<R> type, String table, String... key) {
        this.type = type;
        this.table = table;
        List<String> names = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
        this.columns = names.stream().map(RecordTable::snakeCase).toList();
        List<String> keys = List.of(key);
        if (keys.isEmpty() || !columns.containsAll(keys)) throw new IllegalArgumentException("Bad key " + keys + " for " + table);
        this.upsertSql = upsertSql(names, keys);
        this.allSql = "SELECT " + String.join(", ", columns) + " FROM " + table + " ORDER BY rowid";
    }

    public void upsert(Handle handle, Collection<R> rows) {
        batch(handle, upsertSql, rows, PreparedBatch::bindMethods);
    }

    /** Deletes every row whose {@code column} equals one of {@code values}. */
    public void deleteWhere(Handle handle, String column, Collection<?> values) {
        if (!columns.contains(column)) throw new IllegalArgumentException(table + " has no column " + column);
        batch(handle, "DELETE FROM " + table + " WHERE " + column + " = :value", values, (batch, value) -> batch.bind("value", value));
    }

    /** Replaces a parent's child rows: deletes every row for {@code values}, then upserts {@code rows}. */
    public void replaceFor(Handle handle, String column, Collection<?> values, Collection<R> rows) {
        deleteWhere(handle, column, values);
        upsert(handle, rows);
    }

    /** Every row, in insertion order. */
    public List<R> all(Handle handle) {
        return handle.createQuery(allSql).map(ConstructorMapper.of(type)).list();
    }

    private String upsertSql(List<String> names, List<String> keys) {
        String values = names.stream().map(name -> ":" + name).collect(Collectors.joining(", "));
        List<String> others = columns.stream().filter(column -> !keys.contains(column)).toList();
        String onConflict = others.isEmpty()
                ? "DO NOTHING"
                : "DO UPDATE SET " + others.stream().map(column -> column + " = excluded." + column).collect(Collectors.joining(", "));
        return "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + values + ") ON CONFLICT (" + String.join(", ", keys)
                + ") " + onConflict;
    }

    private static <T> void batch(Handle handle, String sql, Collection<T> items, BiConsumer<PreparedBatch, T> bind) {
        if (items.isEmpty()) return;
        PreparedBatch batch = handle.prepareBatch(sql);
        for (T item : items) {
            bind.accept(batch, item);
            batch.add();
        }
        batch.execute();
    }

    static String snakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
