package com.jamesdpeters.chestsplusplus.persistence;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.persistence.Records.GroupRecord;
import com.jamesdpeters.chestsplusplus.persistence.Records.NodeRecord;
import java.util.List;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Converts between the live model and persistence records. Main thread only (touches ItemStacks). */
public final class ModelMapper {

    private ModelMapper() {}

    public static GroupRecord toRecord(StorageGroup group, List<Node> nodes) {
        List<NodeRecord> nodeRecords = nodes.stream()
                .map(n -> new NodeRecord(n.pos().world(), n.pos().x(), n.pos().y(), n.pos().z(), n.facing().name())).toList();
        return switch (group) {
            case ChestLinkGroup chest -> new GroupRecord(chest.id(), chest.type(), chest.owner(), chest.name(), chest.isPublic(),
                    chest.sortMode().name(), chest.createdAt(), List.copyOf(chest.members()), nodeRecords,
                    chest.hasInventory() ? serialize(chest.inventory().getContents()) : serialize(new ItemStack[0]), null, null);
            case AutoCraftGroup craft -> new GroupRecord(craft.id(), craft.type(), craft.owner(), craft.name(), craft.isPublic(), null,
                    craft.createdAt(), List.copyOf(craft.members()), nodeRecords, null,
                    craft.recipeKey() == null ? null : craft.recipeKey().asString(), serialize(craft.matrix()));
        };
    }

    /** Builds the group without its inventory (attach it afterwards) and without registering it. */
    public static StorageGroup fromRecord(GroupRecord record) {
        StorageGroup group = switch (record.type()) {
            case CHESTLINK -> {
                ChestLinkGroup chest = new ChestLinkGroup(record.id(), record.owner(), record.name(), record.createdAt());
                chest.setSortMode(parseSortMode(record.sortMode()));
                yield chest;
            }
            case AUTOCRAFT -> {
                AutoCraftGroup craft = new AutoCraftGroup(record.id(), record.owner(), record.name(), record.createdAt());
                @Nullable ItemStack[] matrix = new ItemStack[9];
                if (record.matrix() != null) {
                    @Nullable ItemStack[] stored = deserialize(record.matrix());
                    System.arraycopy(stored, 0, matrix, 0, Math.min(9, stored.length));
                }
                NamespacedKey key = record.recipeKey() == null ? null : NamespacedKey.fromString(record.recipeKey());
                // The result is re-resolved against the live recipe list by the AutoCraft service on load.
                craft.setRecipe(matrix, key, null);
                yield craft;
            }
        };
        group.setPublic(record.isPublic());
        return group;
    }

    public static List<Node> nodes(GroupRecord record) {
        return record.nodes().stream().map(n -> new Node(new BlockPos(n.world(), n.x(), n.y(), n.z()), parseFace(n.facing()), record.id())).toList();
    }

    public static byte[] serialize(@Nullable ItemStack[] items) {
        return ItemStack.serializeItemsAsBytes(items);
    }

    /** Deserialises and normalises empty stacks to {@code null} (spike S2: nulls come back as AIR x0). */
    public static @Nullable ItemStack[] deserialize(byte[] bytes) {
        @Nullable ItemStack[] items = ItemStack.deserializeItemsFromBytes(bytes);
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.isEmpty()) items[i] = null;
        }
        return items;
    }

    private static SortMode parseSortMode(@Nullable String value) {
        if (value == null) return SortMode.OFF;
        try {
            return SortMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SortMode.OFF;
        }
    }

    private static BlockFace parseFace(String value) {
        try {
            return BlockFace.valueOf(value);
        } catch (IllegalArgumentException e) {
            return BlockFace.NORTH;
        }
    }
}
