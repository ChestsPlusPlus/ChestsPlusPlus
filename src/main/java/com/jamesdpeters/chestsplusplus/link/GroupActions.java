package com.jamesdpeters.chestsplusplus.link;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.access.AccessService;
import com.jamesdpeters.chestsplusplus.access.AccessibleGroups;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkService;
import com.jamesdpeters.chestsplusplus.core.PlayerNames;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Every user-facing group action with its server-side checks and messages. Commands and dialogs both go through here, so a dialog button
 * can never do more than the equivalent command.
 */
@RequiredArgsConstructor
public final class GroupActions {

    private final Services services;
    private final LinkService links;
    private final ChestLinkService chestLinks;

    /** Resolves {@code input} for {@code player}; messages and returns null if it isn't an accessible group. */
    public @Nullable StorageGroup find(Player player, GroupType type, String input) {
        return switch (links.resolve(player.getUniqueId(), AccessService.hasBypass(player), type, input)) {
            case LinkService.Resolved.Found found -> found.group();
            case LinkService.Resolved.Missing missing -> {
                services.send(player, Message.ERROR_UNKNOWN_GROUP, Messages.text("group", input));
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
        services.send(player, group.type().pick(Message.CHESTLINK_REMOVED, Message.AUTOCRAFT_REMOVED),
                Messages.group(group));
        return true;
    }

    public boolean rename(Player player, StorageGroup group, String newName) {
        if (!canManage(player, group)) return false;
        return links.rename(player, group, newName);
    }

    public boolean setPublic(Player player, StorageGroup group, boolean isPublic) {
        if (!canManage(player, group)) return false;
        links.setPublic(group, isPublic);
        services.send(player, group.type().pick(Message.CHESTLINK_PUBLIC, Message.AUTOCRAFT_PUBLIC),
                Messages.group(group),
                Messages.component("state", services.messages().get(isPublic ? Message.STATE_PUBLIC : Message.STATE_PRIVATE)));
        return true;
    }

    public boolean sort(Player player, ChestLinkGroup group, SortMode mode) {
        if (!require(player, Permissions.CHESTLINK_SORT) || !canManage(player, group)) return false;
        chestLinks.setSortMode(group, mode);
        services.send(player, Message.CHESTLINK_SORT_MODE, Messages.group(group),
                Messages.text("mode", mode.name().toLowerCase(Locale.ROOT)));
        return true;
    }

    public void addMember(Player player, StorageGroup group, String name, Runnable after) {
        lookupMember(player, group, name, member -> {
            if (member.equals(group.owner())) {
                services.send(player, Message.ERROR_SELF);
                return;
            }
            if (!links.addMember(group, member)) {
                services.send(player, Message.ERROR_ALREADY_MEMBER, Messages.player(name), Messages.group(group));
                return;
            }
            services.send(player, Message.MEMBERS_ADDED, Messages.player(name), Messages.group(group));
            after.run();
        });
    }

    public void removeMember(Player player, StorageGroup group, String name, Runnable after) {
        lookupMember(player, group, name, member -> {
            if (!links.removeMember(group, member)) {
                services.send(player, Message.ERROR_NOT_MEMBER, Messages.player(name), Messages.group(group));
                return;
            }
            services.send(player, Message.MEMBERS_REMOVED, Messages.player(name), Messages.group(group));
            after.run();
        });
    }

    public void listMembers(Player player, StorageGroup group) {
        if (!canUse(player, group)) return;
        if (group.members().isEmpty()) {
            services.send(player, Message.MEMBERS_NONE, Messages.group(group));
            return;
        }
        services.send(player, Message.MEMBERS_LIST, Messages.group(group),
                Messages.text("players", PlayerNames.join(group.members())));
    }

    public void trust(Player player, String name, Runnable after) {
        if (!require(player, Permissions.TRUST)) return;
        lookup(player, name, trusted -> {
            if (trusted.equals(player.getUniqueId())) {
                services.send(player, Message.ERROR_SELF);
                return;
            }
            services.trust().trust(player.getUniqueId(), trusted);
            services.send(player, Message.TRUST_ADDED, Messages.player(name));
            after.run();
        });
    }

    public void untrust(Player player, String name, Runnable after) {
        if (!require(player, Permissions.TRUST)) return;
        lookup(player, name, trusted -> {
            services.trust().untrust(player.getUniqueId(), trusted);
            services.send(player, Message.TRUST_REMOVED, Messages.player(name));
            after.run();
        });
    }

    public void listTrust(Player player) {
        if (!require(player, Permissions.TRUST)) return;
        List<UUID> trusted = List.copyOf(services.trust().trustedBy(player.getUniqueId()));
        if (trusted.isEmpty()) {
            services.send(player, Message.TRUST_NONE);
        } else {
            services.send(player, Message.TRUST_LIST, Messages.text("players", PlayerNames.join(trusted)));
        }
    }

    public void list(Player player, GroupType type) {
        AccessibleGroups accessible = services.access().accessibleGroups(player.getUniqueId(), AccessService.hasBypass(player), type);
        if (accessible.groups().isEmpty()) {
            services.send(player, type.pick(Message.CHESTLINK_LIST_EMPTY, Message.AUTOCRAFT_LIST_EMPTY));
            return;
        }
        services.send(player, type.pick(Message.CHESTLINK_LIST_HEADER, Message.AUTOCRAFT_LIST_HEADER));
        GroupTypeHandler handler = links.handler(type);
        for (StorageGroup group : accessible.groups()) {
            String ref = StringArgumentType.escapeIfRequired(accessible.reference(group));
            String command = type.pick("/chestlink open ", "/autocraft open ") + ref;
            services.send(player, type.pick(Message.CHESTLINK_LIST_ENTRY, Message.AUTOCRAFT_LIST_ENTRY),
                    Messages.group(group), Messages.text("ref", ref), TagResolver.resolver("open", Tag.styling(ClickEvent.runCommand(command))),
                    Messages.text("owner", accessible.ownerName(group)),
                    Messages.text("nodes", services.nodes().count(group.id())),
                    Messages.text("items", handler == null ? "" : handler.summary(group)));
        }
    }

    public boolean canUse(Player player, StorageGroup group) {
        if (services.access().canAccess(player.getUniqueId(), player, group)) return true;
        services.send(player, Message.ERROR_NO_ACCESS, Messages.group(group));
        return false;
    }

    public boolean canManage(Player player, StorageGroup group) {
        if (services.access().canManage(player.getUniqueId(), player, group)) return true;
        services.send(player, Message.ERROR_NOT_OWNER, Messages.group(group));
        return false;
    }

    private boolean require(Player player, String permission) {
        if (player.hasPermission(permission)) return true;
        services.send(player, Message.ERROR_NO_PERMISSION);
        return false;
    }

    private boolean blacklisted(Player player) {
        if (!services.settings().isBlacklisted(player.getWorld().getName())) return false;
        services.send(player, Message.ERROR_WORLD_BLACKLISTED);
        return true;
    }

    private void lookupMember(Player player, StorageGroup group, String name, Consumer<UUID> onFound) {
        if (!canEditMembers(player, group)) return;
        lookup(player, name, member -> {
            if (canEditMembers(player, group)) onFound.accept(member);
        });
    }

    /** Also run after an async lookup returns, when the group may have been deleted or the player's rights changed. */
    private boolean canEditMembers(Player player, StorageGroup group) {
        if (services.groups().byId(group.id()) != group) {
            services.send(player, Message.ERROR_UNKNOWN_GROUP, Messages.group(group));
            return false;
        }
        return require(player, Permissions.members(group.type())) && canManage(player, group);
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
        services.send(requester, Message.TRUST_LOOKING_UP, Messages.player(name));
        PlayerProfile profile = Bukkit.createProfile(name);
        profile.update().whenComplete((completed, error) -> Bukkit.getScheduler().runTask(services.plugin(), () -> {
            UUID id = error == null && completed != null && completed.isComplete() ? completed.getId() : null;
            if (id == null) {
                services.send(requester, Message.ERROR_PLAYER_NOT_FOUND, Messages.player(name));
            } else if (requester.isOnline()) {
                onFound.accept(id);
            }
        }));
    }
}
