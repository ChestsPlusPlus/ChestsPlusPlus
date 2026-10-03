package com.jamesdpeters.chestsplusplus.access;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.UUID;
import org.bukkit.permissions.Permissible;

/** The single access rule for groups (plan §5.8). */
public final class AccessService {

    private final TrustService trust;

    public AccessService(TrustService trust) {
        this.trust = trust;
    }

    /** Use (open, link, remote open): owner, public, member, trusted by the owner, or bypass. */
    public boolean canAccess(UUID player, boolean bypass, StorageGroup group) {
        return bypass
                || group.owner().equals(player)
                || group.isPublic()
                || group.members().contains(player)
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

    public static boolean hasBypass(Permissible permissible) {
        return permissible.hasPermission(Permissions.ADMIN_BYPASS);
    }
}
