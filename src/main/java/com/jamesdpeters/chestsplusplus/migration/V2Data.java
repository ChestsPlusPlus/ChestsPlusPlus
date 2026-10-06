package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Everything a v2 {@code storage.yml} holds, read into plain records. {@code problems} lists entries that could not be read. */
public record V2Data(List<Group> groups, List<Party> parties, List<String> problems) {

    /** A v2 block position. Worlds are stored by name in v2. */
    public record Location(String world, int x, int y, int z) {}

    /** A v2 AutoCraft recipe: its key, plus the 3x3 items v2 kept only for recipes that are neither shaped nor shapeless. */
    public record Recipe(NamespacedKey key, @Nullable ItemStack @Nullable [] items) {}

    /** {@code items} is a ChestLink's inventory (null for AutoCraft); {@code recipe} is an AutoCraft's recipe (null for ChestLinks). */
    public record Group(GroupType type, UUID owner, String name, boolean isPublic, List<UUID> members, SortMode sortMode,
            @Nullable ItemStack @Nullable [] items, @Nullable Recipe recipe, List<Location> locations) {

        /** The {@code v2_source} value that identifies this group across repeated imports. */
        public String source() {
            return type.name().toLowerCase(Locale.ROOT) + ":" + owner + ":" + name;
        }
    }

    /** A v2 party: every member could use every group of {@code owner}. */
    public record Party(UUID owner, String name, List<UUID> members) {}
}
