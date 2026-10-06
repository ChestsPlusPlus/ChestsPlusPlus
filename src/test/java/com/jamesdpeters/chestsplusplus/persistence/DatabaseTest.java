package com.jamesdpeters.chestsplusplus.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag(Tags.UNIT)
class DatabaseTest {

    @Test
    void migratesFreshDatabase() {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            assertThat(db.userVersion()).isEqualTo(Database.SCHEMA_VERSION);
        }
    }

    @Test
    void missingMigrationFileFailsClearly() {
        assertThatThrownBy(() -> Database.migrationScript(999)).hasMessageContaining("db/migration/V999.sql");
    }

    @Test
    void refusesNewerSchema(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("future.db");
        try (Database db = Database.open(url)) {
            db.handle().execute("PRAGMA user_version = 999");
        }

        assertThatThrownBy(() -> Database.open(url)).hasMessageContaining("newer");
    }

    @Test
    void uuidBytesRoundTrip() {
        UUID uuid = UUID.randomUUID();
        assertThat(UuidBlob.uuid(UuidBlob.bytes(uuid))).isEqualTo(uuid);
    }

    @Test
    void upgradingOldDevelopmentDatabaseRetainsEvidenceAfterGroupsWereDeleted(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("legacy.db");
        try (Database db = Database.open(url)) {
            db.handle().execute("DROP TABLE v2_pending_locations");
            db.handle().execute("PRAGMA user_version = 1");
            db.handle().createUpdate("INSERT INTO v2_blocks VALUES (:world, 0, 64, 0, 0)").bind("world", UUID.randomUUID()).execute();
        }
        try (Database db = Database.open(url)) {
            assertThat(db.userVersion()).isEqualTo(2);
            assertThat(db.handle().createQuery("SELECT value FROM migration_state WHERE key = 'v2_import'").mapTo(String.class).one())
                    .isEqualTo("LEGACY");
            assertThat(db.handle().createQuery("SELECT count(*) FROM v2_blocks").mapTo(int.class).one()).isEqualTo(1);
            assertThat(db.handle().createQuery("SELECT count(*) FROM v2_pending_locations").mapTo(int.class).one()).isZero();
        }
    }
}
