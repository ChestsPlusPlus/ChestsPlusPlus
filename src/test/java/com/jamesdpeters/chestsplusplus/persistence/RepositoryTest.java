package com.jamesdpeters.chestsplusplus.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.persistence.Records.GroupRecord;
import com.jamesdpeters.chestsplusplus.persistence.Records.LoadedData;
import com.jamesdpeters.chestsplusplus.persistence.Records.NodeRecord;
import com.jamesdpeters.chestsplusplus.persistence.Records.SaveBatch;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
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

    private static GroupRecord chest(long id, String name, NodeRecord... nodes) {
        return new GroupRecord(
                id,
                GroupType.CHESTLINK,
                OWNER,
                name,
                true,
                "NAME",
                123L,
                List.of(MEMBER),
                List.of(nodes),
                new byte[] {1, 2, 3},
                null,
                null);
    }

    @Test
    void migratesFreshDatabase() throws SQLException {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            assertThat(db.userVersion()).isEqualTo(Database.SCHEMA_VERSION);
        }
    }

    @Test
    void roundTripsGroupsNodesMembersInventoriesRecipesAndTrust(@TempDir Path dir) throws SQLException {
        String url = "jdbc:sqlite:" + dir.resolve("data.db");
        NodeRecord node = new NodeRecord(WORLD, 1, -60, 2, "NORTH");
        GroupRecord craft = new GroupRecord(
                2,
                GroupType.AUTOCRAFT,
                OWNER,
                "torches",
                false,
                null,
                5L,
                List.of(),
                List.of(new NodeRecord(WORLD, 5, 64, 5, "EAST")),
                null,
                "minecraft:torch",
                new byte[] {9});
        try (Database db = Database.open(url)) {
            new Repository(db)
                    .write(new SaveBatch(
                            List.of(chest(1, "Ores", node), craft), List.of(), Map.of(OWNER, Set.of(MEMBER))));
        }

        LoadedData loaded;
        try (Database db = Database.open(url)) {
            loaded = new Repository(db).loadAll();
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
    void updatesReplaceChildRowsAndDeletesCascade() throws SQLException {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            Repository repo = new Repository(db);
            NodeRecord a = new NodeRecord(WORLD, 0, 0, 0, "NORTH");
            NodeRecord b = new NodeRecord(WORLD, 1, 0, 0, "NORTH");
            repo.write(new SaveBatch(List.of(chest(1, "g", a, b)), List.of(), Map.of()));
            repo.write(new SaveBatch(List.of(chest(1, "renamed", b)), List.of(), Map.of(OWNER, Set.of())));

            GroupRecord updated = repo.loadAll().groups().getFirst();
            assertThat(updated.name()).isEqualTo("renamed");
            assertThat(updated.nodes()).containsExactly(b);

            // A node moving to another group in a later batch is reassigned, not duplicated.
            repo.write(new SaveBatch(List.of(chest(2, "other", b)), List.of(), Map.of()));
            assertThat(repo.loadAll().groups().getFirst().nodes()).isEmpty();

            repo.write(new SaveBatch(List.of(), List.of(1L, 2L), Map.of()));
            assertThat(repo.loadAll().groups()).isEmpty();
            try (Statement st = db.connection().createStatement();
                    var rs = st.executeQuery("SELECT count(*) FROM nodes")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    void refusesNewerSchema(@TempDir Path dir) throws SQLException {
        String url = "jdbc:sqlite:" + dir.resolve("future.db");
        try (Database db = Database.open(url);
                Statement st = db.connection().createStatement()) {
            st.execute("PRAGMA user_version = 999");
        }

        assertThatThrownBy(() -> Database.open(url)).hasMessageContaining("newer");
    }

    @Test
    void uuidBytesRoundTrip() {
        UUID uuid = UUID.randomUUID();
        assertThat(Repository.uuid(Repository.bytes(uuid))).isEqualTo(uuid);
    }
}
