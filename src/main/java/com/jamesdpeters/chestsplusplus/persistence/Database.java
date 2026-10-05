package com.jamesdpeters.chestsplusplus.persistence;

import com.jamesdpeters.chestsplusplus.core.Resources;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.JdbiException;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

/**
 * The SQLite handle, PRAGMAs and schema migrations ({@code db/migration/V<n>.sql}, versioned with {@code PRAGMA user_version}). Uses the
 * {@code sqlite-jdbc} driver Paper bundles. A shipped migration file is never edited; schema changes go in a new one.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Database implements AutoCloseable {

    public static final int SCHEMA_VERSION = 1;

    private static final List<String> PRAGMAS = List.of("journal_mode=WAL", "synchronous=NORMAL", "foreign_keys=ON", "busy_timeout=5000");

    /** Not thread-safe: only the persistence I/O thread uses it, or the final flush once that thread has stopped. */
    @Getter private final Handle handle;

    /**
     * Opens (creating if needed) and migrates the database at a JDBC SQLite URL.
     *
     * @throws IllegalStateException if the schema is newer than this plugin or a migration fails
     */
    public static Database open(String jdbcUrl) {
        Jdbi jdbi = Jdbi.create(jdbcUrl).installPlugin(new SqlObjectPlugin());
        UuidBlob.register(jdbi);
        ItemsBlob.register(jdbi);
        // One handle for the plugin's lifetime: PRAGMAs such as foreign_keys only apply to the connection they ran on.
        Handle handle = jdbi.open();
        try {
            PRAGMAS.forEach(pragma -> handle.execute("PRAGMA " + pragma));
            Database database = new Database(handle);
            database.migrate();
            return database;
        } catch (RuntimeException e) {
            handle.close();
            throw e;
        }
    }

    public Repository repository() {
        return handle.attach(Repository.class);
    }

    public int userVersion() {
        return handle.createQuery("PRAGMA user_version").mapTo(int.class).one();
    }

    private void migrate() {
        int current = userVersion();
        if (current > SCHEMA_VERSION) {
            throw new IllegalStateException("Database schema version " + current + " is newer than this plugin supports (" + SCHEMA_VERSION
                    + "). Downgrading ChestsPlusPlus is not supported.");
        }
        for (int version = current + 1; version <= SCHEMA_VERSION; version++) apply(version);
    }

    private void apply(int version) {
        String script = migrationScript(version);
        try {
            handle.useTransaction(h -> {
                h.createScript(script).executeAsSeparateStatements();
                h.execute("PRAGMA user_version = " + version);
            });
        } catch (JdbiException e) {
            throw new IllegalStateException("Migration to schema v" + version + " failed", e);
        }
    }

    static String migrationScript(int version) {
        return Resources.text("db/migration/V" + version + ".sql");
    }

    @Override
    public void close() {
        handle.close();
    }
}
