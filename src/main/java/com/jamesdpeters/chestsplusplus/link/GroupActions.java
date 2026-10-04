package com.jamesdpeters.chestsplusplus.link;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.access.AccessService;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkService;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Every user-facing group action with its server-side checks and messages. Commands and dialogs both go through here, so a dialog button
 * can never do more than the equivalent command.
 */
public final class GroupActions {

    private final Services services;
    private final LinkService links;

    public GroupActions(Services services, LinkService links) {
        this.services = services;
        this.links = links;
    }

    /** Resolves {@code input} for {@code player}; messages and returns null if it isn't an accessible group. */
    public @Nullable StorageGroup find(Player player, GroupType type, String input) {
        return switch (links.resolve(player.getUniqueId(), AccessService.hasBypass(player), type, input)) {
            case LinkService.Resolved.Found found -> found.group();
            case LinkService.Resolved.Missing missing -> {
                services.messages().send(player, Message.ERROR_UNKNOWN_GROUP, Messages.text("group", input));
                yield null;
            }
            case LinkService.Resolved.Error error -> {
                links.send(player, error);
                yield null;
            }
        };
    }

    public boolean openRemote(Player player, StorageGroup group) {
        if (!require(player, Permissions.remote(group.type())) || blacklisted(player) || !canUse(player, group)) {
            return false;
        }
        GroupTypeHandler handler = links.handler(group.type());
        if (handler == null) return false;
        handler.openRemote(player, group);
        return true;
    }

    public boolean remove(Player player, StorageGroup group) {
        if (!require(player, Permissions.remove(group.type())) || !canManage(player, group)) return false;
        links.removeGroup(group, player.getLocation());
        services.messages().send(player, group.type().pick(Message.CHESTLINK_REMOVED, Message.AUTOCRAFT_REMOVED),
                Messages.text("group", group.name()));
        return true;
    }

    public boolean rename(Player player, StorageGroup group, String newName) {
        if (!canManage(player, group)) return false;
        return links.rename(player, group, newName);
    }

    public boolean setPublic(Player player, StorageGroup group, boolean isPublic) {
        if (!canManage(player, group)) return false;
        links.setPublic(group, isPublic);
        services.messages().send(player, group.type().pick(Message.CHESTLINK_PUBLIC, Message.AUTOCRAFT_PUBLIC),
                Messages.text("group", group.name()),
                Messages.component("state", services.messages().get(isPublic ? Message.STATE_PUBLIC : Message.STATE_PRIVATE)));
        return true;
    }

    public boolean sort(Player player, ChestLinkGroup group, SortMode mode) {
        if (!require(player, Permissions.CHESTLINK_SORT) || !canManage(player, group)) return false;
        group.setSortMode(mode);
        ChestLinkService chestLinks = services.get(ChestLinkService.class);
        chestLinks.sort(group);
        chestLinks.changed(group);
        services.messages().send(player, Message.CHESTLINK_SORT_MODE, Messages.text("group", group.name()),
                Messages.text("mode", mode.name().toLowerCase(Locale.ROOT)));
        return true;
    }

    public void addMember(Player player, StorageGroup group, String name, @Nullable Runnable after) {
        if (!require(player, Permissions.members(group.type())) || !canManage(player, group)) return;
        lookup(player, name, member -> {
            if (member.equals(group.owner())) {
                services.messages().send(player, Message.ERROR_SELF);
                return;
            }
            links.addMember(group, member);
            services.messages().send(player, Message.MEMBERS_ADDED, Messages.text("player", name), Messages.text("group", group.name()));
            if (after != null) after.run();
        });
    }

    public void removeMember(Player player, StorageGroup group, String name, @Nullable Runnable after) {
        if (!require(player, Permissions.members(group.type())) || !canManage(player, group)) return;
        lookup(player, name, member -> {
            links.removeMember(group, member);
            services.messages().send(player, Message.MEMBERS_REMOVED, Messages.text("player", name), Messages.text("group", group.name()));
            if (after != null) after.run();
        });
    }

    public void listMembers(Player player, StorageGroup group) {
        if (!canUse(player, group)) return;
        if (group.members().isEmpty()) {
            services.messages().send(player, Message.MEMBERS_NONE, Messages.text("group", group.name()));
            return;
        }
        services.messages().send(player, Message.MEMBERS_LIST, Messages.text("group", group.name()),
                Messages.text("players", names(List.copyOf(group.members()))));
    }

    public void trust(Player player, String name, @Nullable Runnable after) {
        if (!require(player, Permissions.TRUST)) return;
        lookup(player, name, trusted -> {
            if (trusted.equals(player.getUniqueId())) {
                services.messages().send(player, Message.ERROR_SELF);
                return;
            }
            services.trust().trust(player.getUniqueId(), trusted);
            services.messages().send(player, Message.TRUST_ADDED, Messages.text("player", name));
            if (after != null) after.run();
        });
    }

    public void untrust(Player player, String name, @Nullable Runnable after) {
        if (!require(player, Permissions.TRUST)) return;
        lookup(player, name, trusted -> {
            services.trust().untrust(player.getUniqueId(), trusted);
            services.messages().send(player, Message.TRUST_REMOVED, Messages.text("player", name));
            if (after != null) after.run();
        });
    }

    public void listTrust(Player player) {
        if (!require(player, Permissions.TRUST)) return;
        List<UUID> trusted = List.copyOf(services.trust().trustedBy(player.getUniqueId()));
        if (trusted.isEmpty()) {
            services.messages().send(player, Message.TRUST_NONE);
        } else {
            services.messages().send(player, Message.TRUST_LIST, Messages.text("players", names(trusted)));
        }
    }

    public void list(Player player, GroupType type) {
        List<StorageGroup> groups = links.accessibleGroups(player.getUniqueId(), AccessService.hasBypass(player), type);
        if (groups.isEmpty()) {
            services.messages().send(player, type.pick(Message.CHESTLINK_LIST_EMPTY, Message.AUTOCRAFT_LIST_EMPTY));
            return;
        }
        services.messages().send(player, type.pick(Message.CHESTLINK_LIST_HEADER, Message.AUTOCRAFT_LIST_HEADER));
        GroupTypeHandler handler = links.handler(type);
        for (StorageGroup group : groups) {
            services.messages().send(player, type.pick(Message.CHESTLINK_LIST_ENTRY, Message.AUTOCRAFT_LIST_ENTRY),
                    Messages.text("group", group.name()), Messages.text("ref", LinkService.reference(player.getUniqueId(), group)),
                    Messages.text("owner", LinkService.ownerName(group.owner())),
                    Messages.text("nodes", services.nodes().count(group.id())),
                    Messages.text("items", handler == null ? "" : handler.summary(group)));
        }
    }

    public boolean canUse(Player player, StorageGroup group) {
        if (services.access().canAccess(player.getUniqueId(), player, group)) return true;
        services.messages().send(player, Message.ERROR_NO_ACCESS, Messages.text("group", group.name()));
        return false;
    }

    public boolean canManage(Player player, StorageGroup group) {
        if (services.access().canManage(player.getUniqueId(), player, group)) return true;
        services.messages().send(player, Message.ERROR_NOT_OWNER, Messages.text("group", group.name()));
        return false;
    }

    private boolean require(Player player, String permission) {
        if (player.hasPermission(permission)) return true;
        services.messages().send(player, Message.ERROR_NO_PERMISSION);
        return false;
    }

    private boolean blacklisted(Player player) {
        if (!services.settings().isBlacklisted(player.getWorld().getName())) return false;
        services.messages().send(player, Message.ERROR_WORLD_BLACKLISTED);
        return true;
    }

    private static String names(List<UUID> players) {
        return players.stream().map(LinkService::ownerName).collect(Collectors.joining(", "));
    }

    /**
     * Resolves a player name without blocking the main thread: the cache first, then an async profile
     * lookup whose result is applied back on the main thread.
     */
    private void lookup(Player requester, String name, Consumer<UUID> onFound) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            onFound.accept(cached.getUniqueId());
            return;
        }
        services.messages().send(requester, Message.TRUST_LOOKING_UP, Messages.text("player", name));
        PlayerProfile profile = Bukkit.createProfile(name);
        profile.update().whenComplete((completed, error) -> Bukkit.getScheduler().runTask(services.plugin(), () -> {
            UUID id = error == null && completed != null && completed.isComplete() ? completed.getId() : null;
            if (id == null) {
                services.messages().send(requester, Message.ERROR_PLAYER_NOT_FOUND, Messages.text("player", name));
            } else if (requester.isOnline()) {
                onFound.accept(id);
            }
        }));
    }
}
