package com.jamesdpeters.chestsplusplus.access;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.core.PlayerNames;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.permissions.Permissible;

/** The single access rule for groups. */
public final class AccessService {

    private final TrustService trust;
    private final GroupRegistry groups;

    public AccessService(TrustService trust, GroupRegistry groups) {
        this.trust = trust;
        this.groups = groups;
    }

    /** Use (open, link, remote open): owner, public, member, trusted by the owner, or bypass. */
    public boolean canAccess(UUID player, boolean bypass, StorageGroup group) {
        return bypass || group.owner().equals(player) || group.isPublic() || group.members().contains(player)
                || trust.isTrusted(group.owner(), player);
    }

    public boolean canAccess(UUID player, Permissible permissible, StorageGroup group) {
        return canAccess(player, hasBypass(permissible), group);
    }

    /** Manage (rename, remove, public, members, sort mode, recipe): owner or bypass. */
    public boolean canManage(UUID player, boolean bypass, StorageGroup group) {
        return bypass || group.owner().equals(player);
    }

    public boolean canManage(UUID player, Permissible permissible, StorageGroup group) {
        return canManage(player, hasBypass(permissible), group);
    }

    /**
     * Groups of {@code type} the player can use: their own, member-of, owners who trust them, public ones (and all groups with bypass). Own
     * groups first, then by owner name and group name.
     */
    public List<StorageGroup> accessibleGroups(UUID player, boolean bypass, GroupType type) {
        Set<StorageGroup> found = new LinkedHashSet<>();
        if (bypass) {
            found.addAll(groups.all(type));
        } else {
            found.addAll(groups.ownedBy(player, type));
            for (StorageGroup group : groups.memberOf(player)) if (group.type() == type) found.add(group);
            for (UUID owner : trust.ownersTrusting(player)) found.addAll(groups.ownedBy(owner, type));
            for (StorageGroup group : groups.all(type)) if (group.isPublic()) found.add(group);
        }
        Comparator<StorageGroup> order = Comparator.<StorageGroup, Boolean>comparing(g -> !g.owner().equals(player))
                .thenComparing(g -> PlayerNames.of(g.owner()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StorageGroup::name, String.CASE_INSENSITIVE_ORDER);
        return found.stream().sorted(order).toList();
    }

    public static boolean hasBypass(Permissible permissible) {
        return permissible.hasPermission(Permissions.ADMIN_BYPASS);
    }
}
