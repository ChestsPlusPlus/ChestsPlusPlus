package com.jamesdpeters.chestsplusplus.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * The SQLite connection, PRAGMAs and schema migrations (versioned with {@code PRAGMA user_version}). Uses the
 * {@code sqlite-jdbc} driver Paper bundles (spike S2). Owned by the persistence I/O thread after opening.
 */
public final class Database implements AutoCloseable {

    /** A schema step; {@link #version} becomes {@code user_version} once its statements commit. */
    record Migration(int version, List<String> statements) {}

    static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, List.of("""
                    CREATE TABLE groups (
                      id          INTEGER PRIMARY KEY,
                      type        TEXT    NOT NULL,
                      owner       BLOB    NOT NULL,
                      name        TEXT    NOT NULL COLLATE NOCASE,
                      is_public   INTEGER NOT NULL DEFAULT 0,
                      sort_mode   TEXT,
                      created_at  INTEGER NOT NULL,
                      UNIQUE (type, owner, name)
                    )""", """
                    CREATE TABLE group_members (
                      group_id INTEGER NOT NULL REFERENCES groups ON DELETE CASCADE,
                      member   BLOB    NOT NULL,
                      PRIMARY KEY (group_id, member)
                    )""", """
                    CREATE TABLE nodes (
                      world    BLOB    NOT NULL,
                      x        INTEGER NOT NULL,
                      y        INTEGER NOT NULL,
                      z        INTEGER NOT NULL,
                      group_id INTEGER NOT NULL REFERENCES groups ON DELETE CASCADE,
                      facing   TEXT    NOT NULL,
                      PRIMARY KEY (world, x, y, z)
                    )""", "CREATE INDEX nodes_group ON nodes(group_id)", """
                    CREATE TABLE chest_inventories (
                      group_id   INTEGER PRIMARY KEY REFERENCES groups ON DELETE CASCADE,
                      items      BLOB    NOT NULL,
                      updated_at INTEGER NOT NULL
                    )""", """
                    CREATE TABLE autocraft_recipes (
                      group_id   INTEGER PRIMARY KEY REFERENCES groups ON DELETE CASCADE,
                      recipe_key TEXT,
                      matrix     BLOB    NOT NULL
                    )""", """
                    CREATE TABLE trust (
                      owner   BLOB NOT NULL,
                      trusted BLOB NOT NULL,
                      PRIMARY KEY (owner, trusted)
                    )""")));

    public static final int SCHEMA_VERSION = MIGRATIONS.getLast().version();

    private final Connection connection;

    private Database(Connection connection) {
        this.connection = connection;
    }

    /** Opens (creating if needed) and migrates the database at a JDBC SQLite URL. */
    public static Database open(String jdbcUrl) throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        Database database = new Database(connection);
        try {
            database.migrate();
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return database;
    }

    public Connection connection() {
        return connection;
    }

    public int userVersion() throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void migrate() throws SQLException {
        int current = userVersion();
        if (current > SCHEMA_VERSION) {
            throw new SQLException("Database schema version " + current + " is newer than this plugin supports ("
                    + SCHEMA_VERSION + "). Downgrading ChestsPlusPlus is not supported.");
        }
        for (Migration migration : MIGRATIONS) {
            if (migration.version() <= current) continue;
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String sql : migration.statements()) statement.execute(sql);
                statement.execute("PRAGMA user_version = " + migration.version());
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new SQLException("Migration to schema v" + migration.version() + " failed", e);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
