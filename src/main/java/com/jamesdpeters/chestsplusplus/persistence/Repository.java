package com.jamesdpeters.chestsplusplus.persistence;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.persistence.Records.GroupRecord;
import com.jamesdpeters.chestsplusplus.persistence.Records.LoadedData;
import com.jamesdpeters.chestsplusplus.persistence.Records.NodeRecord;
import com.jamesdpeters.chestsplusplus.persistence.Records.SaveBatch;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * All SQL for the plugin: reads everything at startup and applies {@link SaveBatch}es transactionally. Runs only on the persistence I/O
 * thread (or synchronously during the final flush).
 */
public final class Repository {

    private interface RowReader {
        void read(ResultSet row) throws SQLException;
    }

    private final Connection connection;

    public Repository(Database database) {
        this.connection = database.connection();
    }

    public LoadedData loadAll() throws SQLException {
        Map<Long, List<UUID>> members = new HashMap<>();
        forEachRow("SELECT group_id, member FROM group_members ORDER BY rowid",
                row -> members.computeIfAbsent(row.getLong(1), k -> new ArrayList<>()).add(uuid(row.getBytes(2))));

        Map<Long, List<NodeRecord>> nodes = new HashMap<>();
        forEachRow("SELECT group_id, world, x, y, z, facing FROM nodes ORDER BY rowid",
                row -> nodes.computeIfAbsent(row.getLong(1), k -> new ArrayList<>())
                        .add(new NodeRecord(uuid(row.getBytes(2)), row.getInt(3), row.getInt(4), row.getInt(5), row.getString(6))));

        Map<Long, byte[]> inventories = new HashMap<>();
        forEachRow("SELECT group_id, items FROM chest_inventories", row -> inventories.put(row.getLong(1), row.getBytes(2)));

        Map<Long, String> recipeKeys = new HashMap<>();
        Map<Long, byte[]> matrices = new HashMap<>();
        forEachRow("SELECT group_id, recipe_key, matrix FROM autocraft_recipes", row -> {
            long id = row.getLong(1);
            String key = row.getString(2);
            if (key != null) recipeKeys.put(id, key);
            matrices.put(id, row.getBytes(3));
        });

        List<GroupRecord> groups = new ArrayList<>();
        forEachRow("SELECT id, type, owner, name, is_public, sort_mode, created_at FROM groups ORDER BY id", row -> {
            long id = row.getLong(1);
            groups.add(new GroupRecord(id, GroupType.valueOf(row.getString(2)), uuid(row.getBytes(3)), row.getString(4), row.getInt(5) != 0,
                    row.getString(6), row.getLong(7), List.copyOf(members.getOrDefault(id, List.of())),
                    List.copyOf(nodes.getOrDefault(id, List.of())), inventories.get(id), recipeKeys.get(id), matrices.get(id)));
        });

        Map<UUID, Set<UUID>> trust = new LinkedHashMap<>();
        forEachRow("SELECT owner, trusted FROM trust ORDER BY rowid",
                row -> trust.computeIfAbsent(uuid(row.getBytes(1)), k -> new LinkedHashSet<>()).add(uuid(row.getBytes(2))));
        return new LoadedData(groups, trust);
    }

    /** Applies a batch in one transaction: deletes first, then group upserts, then trust replacements. */
    public void write(SaveBatch batch) throws SQLException {
        if (batch.isEmpty()) return;
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            updateEach("DELETE FROM groups WHERE id = ?", batch.deletedGroups(), id -> new Object[]{id});
            for (GroupRecord group : batch.groups()) writeGroup(group);
            for (var entry : batch.trust().entrySet()) writeTrust(entry.getKey(), entry.getValue());
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void writeGroup(GroupRecord g) throws SQLException {
        update("""
                INSERT INTO groups (id, type, owner, name, is_public, sort_mode, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = excluded.name, is_public = excluded.is_public,
                  sort_mode = excluded.sort_mode""", g.id(), g.type().name(), bytes(g.owner()), g.name(), g.isPublic() ? 1 : 0, g.sortMode(),
                g.createdAt());

        update("DELETE FROM group_members WHERE group_id = ?", g.id());
        updateEach("INSERT INTO group_members (group_id, member) VALUES (?, ?)", g.members(), member -> new Object[]{g.id(), bytes(member)});

        update("DELETE FROM nodes WHERE group_id = ?", g.id());
        // OR REPLACE: a node may have moved to this group from another one not yet flushed.
        updateEach("INSERT OR REPLACE INTO nodes (world, x, y, z, group_id, facing) VALUES (?, ?, ?, ?, ?, ?)", g.nodes(),
                node -> new Object[]{bytes(node.world()), node.x(), node.y(), node.z(), g.id(), node.facing()});

        if (g.inventory() != null) {
            update("INSERT OR REPLACE INTO chest_inventories (group_id, items, updated_at) VALUES (?, ?, ?)", g.id(), g.inventory(),
                    System.currentTimeMillis());
        }
        if (g.matrix() != null) {
            update("INSERT OR REPLACE INTO autocraft_recipes (group_id, recipe_key, matrix) VALUES (?, ?, ?)", g.id(), g.recipeKey(), g.matrix());
        }
    }

    private void writeTrust(UUID owner, Set<UUID> trusted) throws SQLException {
        update("DELETE FROM trust WHERE owner = ?", bytes(owner));
        updateEach("INSERT INTO trust (owner, trusted) VALUES (?, ?)", trusted, player -> new Object[]{bytes(owner), bytes(player)});
    }

    private void forEachRow(String sql, RowReader reader) throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) reader.read(rs);
        }
    }

    private void update(String sql, @Nullable Object... params) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            bind(st, params);
            st.executeUpdate();
        }
    }

    private <T> void updateEach(String sql, Iterable<T> items, Function<T, @Nullable Object[]> params) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            for (T item : items) {
                bind(st, params.apply(item));
                st.addBatch();
            }
            st.executeBatch();
        }
    }

    private static void bind(PreparedStatement st, @Nullable Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) st.setObject(i + 1, params[i]);
    }

    static byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }

    static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
