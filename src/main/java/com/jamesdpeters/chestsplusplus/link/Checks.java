package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.message.Refusal;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** The guard checks actions share. Each returns why the player is refused, or null, and never sends: {@link #refused} does that. */
@RequiredArgsConstructor
public final class Checks {

    private final Services services;

    /** Disabled features keep their groups, but players can't use or change them until the feature is turned back on. */
    public boolean isFeatureEnabled(GroupType type) {
        var features = services.settings().features();
        return type.pick(features.chestlinks(), features.autocraft());
    }

    public @Nullable Refusal enabled(GroupType type) {
        return isFeatureEnabled(type) ? null : new Refusal(Message.ERROR_FEATURE_DISABLED);
    }

    public @Nullable Refusal permission(Player player, String permission) {
        return player.hasPermission(permission) ? null : new Refusal(Message.ERROR_NO_PERMISSION);
    }

    public @Nullable Refusal allowedWorld(World world) {
        return services.settings().isBlacklisted(world.getName()) ? new Refusal(Message.ERROR_WORLD_BLACKLISTED) : null;
    }

    public @Nullable Refusal use(Player player, StorageGroup group) {
        return services.access().canAccess(player.getUniqueId(), player, group) ? null : new Refusal(Message.ERROR_NO_ACCESS, Messages.group(group));
    }

    public @Nullable Refusal manage(Player player, StorageGroup group) {
        return services.access().canManage(player.getUniqueId(), player, group) ? null : new Refusal(Message.ERROR_NOT_OWNER, Messages.group(group));
    }

    /** Sends the first refusal in {@code checks}, if any, and returns whether it did. */
    public boolean refused(Audience audience, @Nullable Refusal... checks) {
        for (Refusal refusal : checks) {
            if (refusal != null) {
                services.send(audience, refusal);
                return true;
            }
        }
        return false;
    }
}
