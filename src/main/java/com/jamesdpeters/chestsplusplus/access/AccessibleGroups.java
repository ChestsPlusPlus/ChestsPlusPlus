package com.jamesdpeters.chestsplusplus.access;

import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The groups {@code viewer} can use, in display order, with each owner's name resolved once. Name lookups can read player data from
 * disk, so callers use {@link #ownerName} rather than {@code PlayerNames.of(group.owner())} per group.
 */
public record AccessibleGroups(UUID viewer, List<StorageGroup> groups, Map<UUID, String> ownerNames) {

    public String ownerName(StorageGroup group) {
        return ownerNames.get(group.owner());
    }

    /** How the viewer refers to the group in commands: {@code name}, or {@code owner:name} if it isn't theirs. */
    public String reference(StorageGroup group) {
        return group.owner().equals(viewer) ? group.name() : ownerName(group) + ":" + group.name();
    }

    public AccessibleGroups filter(Predicate<StorageGroup> keep) {
        return new AccessibleGroups(viewer, groups.stream().filter(keep).toList(), ownerNames);
    }
}
