package com.jamesdpeters.chestsplusplus.persistence;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Immutable rows exchanged with the persistence I/O thread. Nothing in here references Bukkit objects, so batches can
 * cross threads safely (plan §3.3).
 */
public final class Records {

    private Records() {}

    public record NodeRecord(UUID world, int x, int y, int z, String facing) {}

    /** A full snapshot of one group; the I/O thread replaces everything stored for {@code id}. */
    public record GroupRecord(
            long id,
            GroupType type,
            UUID owner,
            String name,
            boolean isPublic,
            @Nullable String sortMode,
            long createdAt,
            List<UUID> members,
            List<NodeRecord> nodes,
            byte @Nullable [] inventory,
            @Nullable String recipeKey,
            byte @Nullable [] matrix) {}

    /**
     * One write-behind transaction: groups to upsert, group ids to delete, and owners whose trust list is replaced
     * (an empty set deletes it).
     */
    public record SaveBatch(List<GroupRecord> groups, List<Long> deletedGroups, Map<UUID, Set<UUID>> trust) {

        public boolean isEmpty() {
            return groups.isEmpty() && deletedGroups.isEmpty() && trust.isEmpty();
        }
    }

    /** Everything read at startup. */
    public record LoadedData(List<GroupRecord> groups, Map<UUID, Set<UUID>> trust) {}
}
