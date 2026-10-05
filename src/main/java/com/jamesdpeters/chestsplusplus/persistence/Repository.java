package com.jamesdpeters.chestsplusplus.persistence;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableList;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.persistence.Records.GroupRecord;
import com.jamesdpeters.chestsplusplus.persistence.Records.GroupSave;
import com.jamesdpeters.chestsplusplus.persistence.Records.LoadedData;
import com.jamesdpeters.chestsplusplus.persistence.Records.NodeRecord;
import com.jamesdpeters.chestsplusplus.persistence.Records.SaveBatch;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * All SQL for the plugin: reads everything at startup and applies {@link SaveBatch}es transactionally. Obtained from
 * {@link Database#repository()}; runs only on the persistence I/O thread (or synchronously during the final flush).
 */
public interface Repository {

    record GroupRow(long id, GroupType type, UUID owner, String name, boolean isPublic, @Nullable String sortMode, long createdAt) {

        GroupRecord toRecord(List<UUID> members, List<NodeRecord> nodes, byte @Nullable [] inventory, @Nullable RecipeRow recipe) {
            return new GroupRecord(id, type, owner, name, isPublic, sortMode, createdAt, members, nodes, inventory,
                    recipe == null ? null : recipe.recipeKey(), recipe == null ? null : recipe.matrix());
        }
    }

    record MemberRow(long groupId, UUID member) {}

    record NodeRow(long groupId, UUID world, int x, int y, int z, String facing) {

        static NodeRow of(long groupId, NodeRecord node) {
            return new NodeRow(groupId, node.world(), node.x(), node.y(), node.z(), node.facing());
        }

        NodeRecord node() {
            return new NodeRecord(world, x, y, z, facing);
        }
    }

    record InventoryRow(long groupId, byte[] items, long updatedAt) {}

    record RecipeRow(long groupId, @Nullable String recipeKey, byte[] matrix) {}

    record TrustRow(UUID owner, UUID trusted) {}

    @SqlQuery("SELECT id, type, owner, name, is_public, sort_mode, created_at FROM groups ORDER BY id")
    @RegisterConstructorMapper(GroupRow.class)
    List<GroupRow> groups();

    @SqlQuery("SELECT group_id, member FROM group_members ORDER BY rowid")
    @RegisterConstructorMapper(MemberRow.class)
    List<MemberRow> members();

    @SqlQuery("SELECT group_id, world, x, y, z, facing FROM nodes ORDER BY rowid")
    @RegisterConstructorMapper(NodeRow.class)
    List<NodeRow> nodes();

    @SqlQuery("SELECT group_id, items, updated_at FROM chest_inventories")
    @RegisterConstructorMapper(InventoryRow.class)
    List<InventoryRow> inventories();

    @SqlQuery("SELECT group_id, recipe_key, matrix FROM autocraft_recipes")
    @RegisterConstructorMapper(RecipeRow.class)
    List<RecipeRow> recipes();

    @SqlQuery("SELECT owner, trusted FROM trust ORDER BY rowid")
    @RegisterConstructorMapper(TrustRow.class)
    List<TrustRow> trust();

    @SqlBatch("DELETE FROM groups WHERE id = :id")
    void deleteGroups(List<Long> id);

    @SqlBatch("""
            INSERT INTO groups (id, type, owner, name, is_public, sort_mode, created_at)
            VALUES (:id, :type, :owner, :name, :isPublic, :sortMode, :createdAt)
            ON CONFLICT (id) DO UPDATE SET name = excluded.name, is_public = excluded.is_public, sort_mode = excluded.sort_mode""")
    void upsertGroups(@BindMethods List<GroupRecord> groups);

    @SqlBatch("DELETE FROM group_members WHERE group_id = :groupId")
    void deleteMembers(List<Long> groupId);

    @SqlBatch("INSERT INTO group_members (group_id, member) VALUES (:groupId, :member)")
    void insertMembers(@BindMethods List<MemberRow> rows);

    @SqlBatch("DELETE FROM nodes WHERE group_id = :groupId")
    void deleteNodes(List<Long> groupId);

    // OR REPLACE: a node may have moved here from a group that hasn't been saved since.
    @SqlBatch("INSERT OR REPLACE INTO nodes (world, x, y, z, group_id, facing) VALUES (:world, :x, :y, :z, :groupId, :facing)")
    void insertNodes(@BindMethods List<NodeRow> rows);

    @SqlBatch("INSERT OR REPLACE INTO chest_inventories (group_id, items, updated_at) VALUES (:groupId, :items, :updatedAt)")
    void upsertInventories(@BindMethods List<InventoryRow> rows);

    @SqlBatch("INSERT OR REPLACE INTO autocraft_recipes (group_id, recipe_key, matrix) VALUES (:groupId, :recipeKey, :matrix)")
    void upsertRecipes(@BindMethods List<RecipeRow> rows);

    @SqlBatch("DELETE FROM trust WHERE owner = :owner")
    void deleteTrust(List<UUID> owner);

    @SqlBatch("INSERT INTO trust (owner, trusted) VALUES (:owner, :trusted)")
    void insertTrust(@BindMethods List<TrustRow> rows);

    default LoadedData loadAll() {
        Map<Long, List<UUID>> members = byGroup(members(), MemberRow::groupId, MemberRow::member);
        Map<Long, List<NodeRecord>> nodes = byGroup(nodes(), NodeRow::groupId, NodeRow::node);
        Map<Long, byte[]> inventories = inventories().stream().collect(toMap(InventoryRow::groupId, InventoryRow::items));
        Map<Long, RecipeRow> recipes = recipes().stream().collect(toMap(RecipeRow::groupId, recipe -> recipe));
        List<GroupRecord> groups = groups().stream()
                .map(g -> g.toRecord(members.getOrDefault(g.id(), List.of()), nodes.getOrDefault(g.id(), List.of()), inventories.get(g.id()),
                        recipes.get(g.id())))
                .toList();
        Map<UUID, Set<UUID>> trust = trust().stream()
                .collect(groupingBy(TrustRow::owner, LinkedHashMap::new, mapping(TrustRow::trusted, toCollection(LinkedHashSet::new))));
        return new LoadedData(groups, trust);
    }

    /**
     * Applies a batch in one transaction: deletes first, then each group's changed parts, then trust replacements. Each statement runs once
     * for the whole batch; clearing every group's nodes before inserting any keeps a node that moved between two groups in this batch with
     * the group listed last.
     */
    @Transaction
    default void write(SaveBatch batch) {
        if (batch.isEmpty()) return;
        deleteGroups(batch.deletedGroups());
        upsertGroups(changed(batch, Change.META));
        replaceMembers(changed(batch, Change.MEMBERS));
        replaceNodes(changed(batch, Change.NODES));
        upsertContents(changed(batch, Change.CONTENTS));
        replaceTrust(batch.trust());
    }

    private void replaceMembers(List<GroupRecord> groups) {
        deleteMembers(ids(groups));
        insertMembers(rows(groups, g -> g.members().stream().map(member -> new MemberRow(g.id(), member))));
    }

    private void replaceNodes(List<GroupRecord> groups) {
        deleteNodes(ids(groups));
        insertNodes(rows(groups, g -> g.nodes().stream().map(node -> NodeRow.of(g.id(), node))));
    }

    private void upsertContents(List<GroupRecord> groups) {
        long now = System.currentTimeMillis();
        upsertInventories(rows(groups, g -> Stream.ofNullable(g.inventory()).map(items -> new InventoryRow(g.id(), items, now))));
        upsertRecipes(rows(groups, g -> Stream.ofNullable(g.matrix()).map(matrix -> new RecipeRow(g.id(), g.recipeKey(), matrix))));
    }

    private void replaceTrust(Map<UUID, Set<UUID>> trust) {
        deleteTrust(List.copyOf(trust.keySet()));
        insertTrust(rows(trust.entrySet(), e -> e.getValue().stream().map(trusted -> new TrustRow(e.getKey(), trusted))));
    }

    private static List<GroupRecord> changed(SaveBatch batch, Change change) {
        return batch.groups().stream().filter(save -> save.changes().contains(change)).map(GroupSave::group).toList();
    }

    private static List<Long> ids(List<GroupRecord> groups) {
        return groups.stream().map(GroupRecord::id).toList();
    }

    private static <T, R> List<R> rows(Collection<T> items, Function<T, Stream<R>> toRows) {
        return items.stream().flatMap(toRows).toList();
    }

    private static <R, V> Map<Long, List<V>> byGroup(List<R> rows, Function<R, Long> groupId, Function<R, V> value) {
        return rows.stream().collect(groupingBy(groupId, mapping(value, toUnmodifiableList())));
    }
}
