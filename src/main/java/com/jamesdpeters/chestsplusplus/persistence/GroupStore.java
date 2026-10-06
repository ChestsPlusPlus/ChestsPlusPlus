package com.jamesdpeters.chestsplusplus.persistence;

import static java.util.stream.Collectors.groupingBy;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import com.jamesdpeters.chestsplusplus.model.SlotMatch;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore.GroupSnapshot;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jspecify.annotations.Nullable;

/** Groups: a {@code groups} row each (with a ChestLink's inventory or an AutoCraft recipe), plus their members and nodes. */
@RequiredArgsConstructor
public final class GroupStore implements Store<Long, GroupSnapshot> {

    /**
     * {@code items} is a ChestLink's inventory or an AutoCraft matrix; {@code matches} is an AutoCraft's comma-separated
     * {@link SlotMatch} per slot, or null when every slot is {@link SlotMatch#RECIPE}. {@code v2Source} names the v2 group it was imported
     * from, if any.
     */
    public record GroupRow(long id, GroupType type, UUID owner, String name, boolean isPublic, @Nullable SortMode sortMode, long createdAt,
            @Nullable ItemStack @Nullable [] items, @Nullable String recipeKey, @Nullable String matches, @Nullable String v2Source) {}

    public record MemberRow(long groupId, UUID member) {}

    public record NodeRow(long groupId, UUID world, int x, int y, int z, BlockFace facing) {}

    public record GroupSnapshot(GroupRow group, List<MemberRow> members, List<NodeRow> nodes) {}

    /** A freshly loaded group and its stored items. */
    public record LoadedGroup(StorageGroup group, @Nullable ItemStack @Nullable [] items) {}

    private static final RecordTable<GroupRow> GROUPS = new RecordTable<>(GroupRow.class, "groups", "id");
    private static final RecordTable<MemberRow> MEMBERS = new RecordTable<>(MemberRow.class, "group_members", "group_id", "member");
    private static final RecordTable<NodeRow> NODES = new RecordTable<>(NodeRow.class, "nodes", "world", "x", "y", "z");

    private final Persistence persistence;
    private final GroupRegistry groups;
    private final NodeIndex nodes;
    /** Called for each loaded group once it is registered and its nodes indexed (e.g. to create ChestLink inventories). */
    private final Consumer<LoadedGroup> attach;

    public void markDirty(StorageGroup group) {
        persistence.markDirty(this, group.id());
    }

    public boolean isDirty(StorageGroup group) {
        return persistence.isDirty(this, group.id());
    }

    @Override
    public @Nullable GroupSnapshot snapshot(Long id) {
        StorageGroup group = groups.byId(id);
        if (group == null) return null;
        List<MemberRow> members = group.members().stream().map(member -> new MemberRow(id, member)).toList();
        List<NodeRow> nodeRows = nodes.nodesOf(id).stream().map(GroupStore::row).toList();
        return new GroupSnapshot(row(group), members, nodeRows);
    }

    @Override
    public void write(Handle handle, List<GroupSnapshot> snapshots) {
        List<Long> ids = snapshots.stream().map(snapshot -> snapshot.group().id()).toList();
        GROUPS.upsert(handle, snapshots.stream().map(GroupSnapshot::group).toList());
        // Every group's old rows go before any new ones are written, so a node that moved between two groups here ends up in its new one.
        MEMBERS.replaceFor(handle, "group_id", ids, children(snapshots, GroupSnapshot::members));
        NODES.replaceFor(handle, "group_id", ids, children(snapshots, GroupSnapshot::nodes));
    }

    /** Members and nodes go with the group (ON DELETE CASCADE). */
    @Override
    public void delete(Handle handle, List<Long> ids) {
        GROUPS.deleteWhere(handle, "id", ids);
    }

    @Override
    public void load(Handle handle) {
        handle.registerColumnMapper(SortMode.class, lenient(SortMode.class, SortMode.OFF));
        handle.registerColumnMapper(BlockFace.class, lenient(BlockFace.class, BlockFace.NORTH));
        Map<Long, List<MemberRow>> members = MEMBERS.all(handle).stream().collect(groupingBy(MemberRow::groupId));
        Map<Long, List<NodeRow>> nodeRows = NODES.all(handle).stream().collect(groupingBy(NodeRow::groupId));
        for (GroupRow row : GROUPS.all(handle)) {
            register(new GroupSnapshot(row, members.getOrDefault(row.id(), List.of()), nodeRows.getOrDefault(row.id(), List.of())));
        }
    }

    /** Registers a group built outside the store (an import) exactly as if it had been loaded, and saves it. */
    public StorageGroup adopt(GroupSnapshot snapshot) {
        StorageGroup group = register(snapshot);
        markDirty(group);
        return group;
    }

    private StorageGroup register(GroupSnapshot snapshot) {
        GroupRow row = snapshot.group();
        StorageGroup group = group(row);
        groups.add(group);
        snapshot.members().forEach(member -> groups.addMember(group, member.member()));
        snapshot.nodes().forEach(node -> nodes.put(node(node)));
        attach.accept(new LoadedGroup(group, row.items()));
        return group;
    }

    private static GroupRow row(StorageGroup group) {
        return switch (group) {
            case ChestLinkGroup chest -> new GroupRow(chest.id(), chest.type(), chest.owner(), chest.name(), chest.isPublic(), chest.sortMode(),
                    chest.createdAt(), contents(chest), null, null, chest.v2Source());
            case AutoCraftGroup craft -> new GroupRow(craft.id(), craft.type(), craft.owner(), craft.name(), craft.isPublic(), null,
                    craft.createdAt(), craft.matrix(), craft.recipeKey() == null ? null : craft.recipeKey().asString(), matches(craft),
                    craft.v2Source());
        };
    }

    private static @Nullable String matches(AutoCraftGroup craft) {
        SlotMatch[] matches = craft.matches();
        if (Arrays.stream(matches).allMatch(match -> match == SlotMatch.RECIPE)) return null;
        return Arrays.stream(matches).map(SlotMatch::name).collect(Collectors.joining(","));
    }

    /** Unknown or missing entries fall back to {@link SlotMatch#RECIPE}. */
    private static void applyMatches(AutoCraftGroup craft, @Nullable String stored) {
        if (stored == null) return;
        String[] names = stored.split(",");
        for (int i = 0; i < 9 && i < names.length; i++) {
            try {
                craft.setMatch(i, SlotMatch.valueOf(names[i].trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                craft.setMatch(i, SlotMatch.RECIPE);
            }
        }
    }

    /** Clones every slot: {@code getContents()} returns live mirrors, and the items are serialised later on the I/O thread. */
    private static @Nullable ItemStack @Nullable [] contents(ChestLinkGroup chest) {
        if (!chest.hasInventory()) return null;
        @Nullable ItemStack[] items = chest.inventory().getContents();
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item != null) items[i] = item.clone();
        }
        return items;
    }

    private static NodeRow row(Node node) {
        return row(node.groupId(), node.pos(), node.facing());
    }

    public static NodeRow row(long groupId, BlockPos pos, BlockFace facing) {
        return new NodeRow(groupId, pos.world(), pos.x(), pos.y(), pos.z(), facing);
    }

    private static Node node(NodeRow row) {
        return new Node(new BlockPos(row.world(), row.x(), row.y(), row.z()), row.facing(), row.groupId());
    }

    /** Builds the group without registering it; the AutoCraft result is re-resolved against the live recipes by the attach callback. */
    private static StorageGroup group(GroupRow row) {
        StorageGroup group = switch (row.type()) {
            case CHESTLINK -> {
                ChestLinkGroup chest = new ChestLinkGroup(row.id(), row.owner(), row.name(), row.createdAt());
                chest.setSortMode(row.sortMode() == null ? SortMode.OFF : row.sortMode());
                yield chest;
            }
            case AUTOCRAFT -> {
                AutoCraftGroup craft = new AutoCraftGroup(row.id(), row.owner(), row.name(), row.createdAt());
                craft.setRecipe(matrix(row.items()), row.recipeKey() == null ? null : NamespacedKey.fromString(row.recipeKey()), null);
                applyMatches(craft, row.matches());
                yield craft;
            }
        };
        group.setPublic(row.isPublic());
        group.setV2Source(row.v2Source());
        return group;
    }

    private static @Nullable ItemStack[] matrix(@Nullable ItemStack @Nullable [] stored) {
        @Nullable ItemStack[] matrix = new ItemStack[9];
        if (stored != null) System.arraycopy(stored, 0, matrix, 0, Math.min(9, stored.length));
        return matrix;
    }

    private static <T, C> List<C> children(List<T> parents, Function<T, List<C>> children) {
        return parents.stream().flatMap(parent -> children.apply(parent).stream()).toList();
    }

    /** Reads an enum by name, falling back instead of failing on an unknown or missing value. */
    private static <E extends Enum<E>> ColumnMapper<E> lenient(Class<E> type, E fallback) {
        return (row, column, context) -> {
            String value = row.getString(column);
            if (value == null) return fallback;
            try {
                return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        };
    }
}
