package com.jamesdpeters.chestsplusplus.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.persistence.Records.GroupRecord;
import com.jamesdpeters.chestsplusplus.persistence.Records.GroupSave;
import com.jamesdpeters.chestsplusplus.persistence.Records.LoadedData;
import com.jamesdpeters.chestsplusplus.persistence.Records.NodeRecord;
import com.jamesdpeters.chestsplusplus.persistence.Records.SaveBatch;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag(Tags.UNIT)
class RepositoryTest {

    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();

    /** The v1 schema as it shipped from Java code; existing databases were created from exactly these statements. */
    private static final List<String> LEGACY_V1 = List.of("""
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
            )""");

    private static GroupRecord chest(long id, String name, NodeRecord... nodes) {
        return new GroupRecord(id, GroupType.CHESTLINK, OWNER, name, true, "NAME", 123L, List.of(MEMBER), List.of(nodes), new byte[]{1, 2, 3}, null,
                null);
    }

    private static List<GroupSave> full(GroupRecord... groups) {
        return Arrays.stream(groups).map(GroupSave::full).toList();
    }

    @Test
    void migratesFreshDatabase() {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            assertThat(db.userVersion()).isEqualTo(Database.SCHEMA_VERSION);
        }
    }

    @Test
    void roundTripsGroupsNodesMembersInventoriesRecipesAndTrust(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("data.db");
        NodeRecord node = new NodeRecord(WORLD, 1, -60, 2, "NORTH");
        GroupRecord craft = new GroupRecord(2, GroupType.AUTOCRAFT, OWNER, "torches", false, null, 5L, List.of(),
                List.of(new NodeRecord(WORLD, 5, 64, 5, "EAST")), null, "minecraft:torch", new byte[]{9});
        try (Database db = Database.open(url)) {
            db.repository().write(new SaveBatch(full(chest(1, "Ores", node), craft), List.of(), Map.of(OWNER, Set.of(MEMBER))));
        }

        LoadedData loaded;
        try (Database db = Database.open(url)) {
            loaded = db.repository().loadAll();
        }

        assertThat(loaded.groups()).hasSize(2);
        GroupRecord chest = loaded.groups().getFirst();
        assertThat(chest.name()).isEqualTo("Ores");
        assertThat(chest.isPublic()).isTrue();
        assertThat(chest.sortMode()).isEqualTo("NAME");
        assertThat(chest.members()).containsExactly(MEMBER);
        assertThat(chest.nodes()).containsExactly(node);
        assertThat(chest.inventory()).containsExactly(1, 2, 3);
        GroupRecord loadedCraft = loaded.groups().get(1);
        assertThat(loadedCraft.recipeKey()).isEqualTo("minecraft:torch");
        assertThat(loadedCraft.matrix()).containsExactly(9);
        assertThat(loaded.trust()).containsEntry(OWNER, Set.of(MEMBER));
    }

    @Test
    void updatesReplaceChildRowsAndDeletesCascade() {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            Repository repo = db.repository();
            NodeRecord a = new NodeRecord(WORLD, 0, 0, 0, "NORTH");
            NodeRecord b = new NodeRecord(WORLD, 1, 0, 0, "NORTH");
            repo.write(new SaveBatch(full(chest(1, "g", a, b)), List.of(), Map.of()));
            repo.write(new SaveBatch(full(chest(1, "renamed", b)), List.of(), Map.of(OWNER, Set.of())));

            GroupRecord updated = repo.loadAll().groups().getFirst();
            assertThat(updated.name()).isEqualTo("renamed");
            assertThat(updated.nodes()).containsExactly(b);

            // A node moving to another group in a later batch is reassigned, not duplicated.
            repo.write(new SaveBatch(full(chest(2, "other", b)), List.of(), Map.of()));
            assertThat(repo.loadAll().groups().getFirst().nodes()).isEmpty();

            repo.write(new SaveBatch(List.of(), List.of(1L, 2L), Map.of()));
            assertThat(repo.loadAll().groups()).isEmpty();
            assertThat(db.handle().createQuery("SELECT count(*) FROM nodes").mapTo(int.class).one()).isZero();
        }
    }

    @Test
    void savesOnlyTheChangedParts() {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            Repository repo = db.repository();
            NodeRecord a = new NodeRecord(WORLD, 0, 0, 0, "NORTH");
            repo.write(new SaveBatch(full(chest(1, "g", a)), List.of(), Map.of()));
            GroupRecord renamedWithNewContents = new GroupRecord(1, GroupType.CHESTLINK, OWNER, "renamed", false, null, 123L, List.of(), List.of(),
                    new byte[]{4}, null, null);

            repo.write(new SaveBatch(List.of(new GroupSave(renamedWithNewContents, Set.of(Change.CONTENTS))), List.of(), Map.of()));

            GroupRecord loaded = repo.loadAll().groups().getFirst();
            assertThat(loaded.inventory()).containsExactly(4);
            assertThat(loaded.name()).isEqualTo("g");
            assertThat(loaded.isPublic()).isTrue();
            assertThat(loaded.members()).containsExactly(MEMBER);
            assertThat(loaded.nodes()).containsExactly(a);
        }
    }

    @Test
    void nodeMovedBetweenGroupsInOneBatchStaysWithItsNewGroup() {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            Repository repo = db.repository();
            NodeRecord a = new NodeRecord(WORLD, 0, 0, 0, "NORTH");
            NodeRecord b = new NodeRecord(WORLD, 1, 0, 0, "NORTH");
            repo.write(new SaveBatch(full(chest(1, "first", a, b)), List.of(), Map.of()));

            repo.write(new SaveBatch(full(chest(2, "second", b), chest(1, "first", a)), List.of(), Map.of()));

            List<GroupRecord> groups = repo.loadAll().groups();
            assertThat(groups.get(0).nodes()).containsExactly(a);
            assertThat(groups.get(1).nodes()).containsExactly(b);
        }
    }

    @Test
    void opensDatabasesCreatedBeforeMigrationsMovedToSqlFiles(@TempDir Path dir) throws SQLException {
        String url = "jdbc:sqlite:" + dir.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection(url); Statement st = connection.createStatement()) {
            for (String sql : LEGACY_V1) st.execute(sql);
            st.execute("PRAGMA user_version = 1");
        }
        NodeRecord node = new NodeRecord(WORLD, 3, 70, 3, "SOUTH");

        try (Database legacy = Database.open(url); Database fresh = Database.open("jdbc:sqlite::memory:")) {
            assertThat(schema(legacy)).isEqualTo(schema(fresh));
            legacy.repository().write(new SaveBatch(full(chest(1, "Ores", node)), List.of(), Map.of(OWNER, Set.of(MEMBER))));
            assertThat(legacy.repository().loadAll().groups().getFirst().nodes()).containsExactly(node);
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

    /** Whitespace-normalised DDL, so the comparison ignores how the statements were indented. */
    private static List<String> schema(Database db) {
        return db.handle()
                .createQuery("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY name")
                .mapTo(String.class)
                .map(sql -> sql.replaceAll("\\s+", " "))
                .list();
    }
}
