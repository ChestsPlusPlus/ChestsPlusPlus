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

/**
 * All SQL for the plugin: reads everything at startup and applies {@link SaveBatch}es transactionally. Runs only on
 * the persistence I/O thread (or synchronously during the final flush).
 */
public final class Repository {

    private final Connection connection;

    public Repository(Database database) {
        this.connection = database.connection();
    }

    public LoadedData loadAll() throws SQLException {
        Map<Long, List<UUID>> members = new HashMap<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT group_id, member FROM group_members ORDER BY rowid")) {
            while (rs.next()) {
                members.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(uuid(rs.getBytes(2)));
            }
        }
        Map<Long, List<NodeRecord>> nodes = new HashMap<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT group_id, world, x, y, z, facing FROM nodes ORDER BY rowid")) {
            while (rs.next()) {
                nodes.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                        .add(new NodeRecord(
                                uuid(rs.getBytes(2)), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6)));
            }
        }
        Map<Long, byte[]> inventories = new HashMap<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT group_id, items FROM chest_inventories")) {
            while (rs.next()) inventories.put(rs.getLong(1), rs.getBytes(2));
        }
        Map<Long, String> recipeKeys = new HashMap<>();
        Map<Long, byte[]> matrices = new HashMap<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT group_id, recipe_key, matrix FROM autocraft_recipes")) {
            while (rs.next()) {
                long id = rs.getLong(1);
                String key = rs.getString(2);
                if (key != null) recipeKeys.put(id, key);
                matrices.put(id, rs.getBytes(3));
            }
        }
        List<GroupRecord> groups = new ArrayList<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, type, owner, name, is_public, sort_mode, created_at FROM groups ORDER BY id")) {
            while (rs.next()) {
                long id = rs.getLong(1);
                groups.add(new GroupRecord(
                        id,
                        GroupType.valueOf(rs.getString(2)),
                        uuid(rs.getBytes(3)),
                        rs.getString(4),
                        rs.getInt(5) != 0,
                        rs.getString(6),
                        rs.getLong(7),
                        List.copyOf(members.getOrDefault(id, List.of())),
                        List.copyOf(nodes.getOrDefault(id, List.of())),
                        inventories.get(id),
                        recipeKeys.get(id),
                        matrices.get(id)));
            }
        }
        Map<UUID, Set<UUID>> trust = new LinkedHashMap<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT owner, trusted FROM trust ORDER BY rowid")) {
            while (rs.next()) {
                trust.computeIfAbsent(uuid(rs.getBytes(1)), k -> new LinkedHashSet<>())
                        .add(uuid(rs.getBytes(2)));
            }
        }
        return new LoadedData(groups, trust);
    }

    /** Applies a batch in one transaction: deletes first, then group upserts, then trust replacements. */
    public void write(SaveBatch batch) throws SQLException {
        if (batch.isEmpty()) return;
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM groups WHERE id = ?")) {
                for (long id : batch.deletedGroups()) {
                    delete.setLong(1, id);
                    delete.addBatch();
                }
                delete.executeBatch();
            }
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
        try (PreparedStatement st = connection.prepareStatement("""
                INSERT INTO groups (id, type, owner, name, is_public, sort_mode, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = excluded.name, is_public = excluded.is_public,
                  sort_mode = excluded.sort_mode""")) {
            st.setLong(1, g.id());
            st.setString(2, g.type().name());
            st.setBytes(3, bytes(g.owner()));
            st.setString(4, g.name());
            st.setInt(5, g.isPublic() ? 1 : 0);
            st.setString(6, g.sortMode());
            st.setLong(7, g.createdAt());
            st.executeUpdate();
        }
        try (PreparedStatement clear = connection.prepareStatement("DELETE FROM group_members WHERE group_id = ?")) {
            clear.setLong(1, g.id());
            clear.executeUpdate();
        }
        try (PreparedStatement st =
                connection.prepareStatement("INSERT INTO group_members (group_id, member) VALUES (?, ?)")) {
            for (UUID member : g.members()) {
                st.setLong(1, g.id());
                st.setBytes(2, bytes(member));
                st.addBatch();
            }
            st.executeBatch();
        }
        try (PreparedStatement clear = connection.prepareStatement("DELETE FROM nodes WHERE group_id = ?")) {
            clear.setLong(1, g.id());
            clear.executeUpdate();
        }
        // OR REPLACE: a node may have moved to this group from another one not yet flushed.
        try (PreparedStatement st = connection.prepareStatement(
                "INSERT OR REPLACE INTO nodes (world, x, y, z, group_id, facing) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (NodeRecord node : g.nodes()) {
                st.setBytes(1, bytes(node.world()));
                st.setInt(2, node.x());
                st.setInt(3, node.y());
                st.setInt(4, node.z());
                st.setLong(5, g.id());
                st.setString(6, node.facing());
                st.addBatch();
            }
            st.executeBatch();
        }
        if (g.inventory() != null) {
            try (PreparedStatement st = connection.prepareStatement(
                    "INSERT OR REPLACE INTO chest_inventories (group_id, items, updated_at) VALUES (?, ?, ?)")) {
                st.setLong(1, g.id());
                st.setBytes(2, g.inventory());
                st.setLong(3, System.currentTimeMillis());
                st.executeUpdate();
            }
        }
        if (g.matrix() != null) {
            try (PreparedStatement st = connection.prepareStatement(
                    "INSERT OR REPLACE INTO autocraft_recipes (group_id, recipe_key, matrix) VALUES (?, ?, ?)")) {
                st.setLong(1, g.id());
                st.setString(2, g.recipeKey());
                st.setBytes(3, g.matrix());
                st.executeUpdate();
            }
        }
    }

    private void writeTrust(UUID owner, Set<UUID> trusted) throws SQLException {
        try (PreparedStatement clear = connection.prepareStatement("DELETE FROM trust WHERE owner = ?")) {
            clear.setBytes(1, bytes(owner));
            clear.executeUpdate();
        }
        try (PreparedStatement st = connection.prepareStatement("INSERT INTO trust (owner, trusted) VALUES (?, ?)")) {
            for (UUID player : trusted) {
                st.setBytes(1, bytes(owner));
                st.setBytes(2, bytes(player));
                st.addBatch();
            }
            st.executeBatch();
        }
    }

    static byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
