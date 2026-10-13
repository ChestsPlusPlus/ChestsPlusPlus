package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.access.AccessService;
import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.message.Refusal;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jspecify.annotations.Nullable;

/** How many groups of each type a player may own. */
@RequiredArgsConstructor
public final class GroupLimits {

    private final GroupRegistry groups;
    private final Supplier<Settings> settings;

    /** Why {@code player} may not create another group of {@code type}, or null if they may. */
    public @Nullable Refusal reached(Player player, GroupType type) {
        int limit = limit(player, type);
        if (limit < 0 || groups.ownedBy(player.getUniqueId(), type).size() < limit) return null;
        return new Refusal(Message.ERROR_LIMIT_REACHED, Messages.text("limit", limit), Messages.text("type", type.displayName()));
    }

    /**
     * The player's group limit for a type: the highest {@code chestsplusplus.limit.<type>.<n>} they hold, else the
     * configured default; -1 means unlimited.
     */
    public int limit(Player player, GroupType type) {
        if (AccessService.hasBypass(player)) return -1;
        String prefix = Permissions.limitPrefix(type);
        int best = Integer.MIN_VALUE;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue() || !info.getPermission().startsWith(prefix)) continue;
            String suffix = info.getPermission().substring(prefix.length());
            if (suffix.equals("unlimited") || suffix.equals("*")) return -1;
            try {
                best = Math.max(best, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
                // not a numeric limit node
            }
        }
        if (best != Integer.MIN_VALUE) return best;
        var limits = settings.get().limits();
        return type.pick(limits.chestlinkDefault(), limits.autocraftDefault());
    }
}
