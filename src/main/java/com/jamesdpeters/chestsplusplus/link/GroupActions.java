package com.jamesdpeters.chestsplusplus.link;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.jamesdpeters.chestsplusplus.GroupPermission;
import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.access.AccessService;
import com.jamesdpeters.chestsplusplus.access.AccessibleGroups;
import com.jamesdpeters.chestsplusplus.core.PaperApi;
import com.jamesdpeters.chestsplusplus.core.PlayerNames;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.message.GroupMessage;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.message.Refusal;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
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
    private final Handlers handlers;
    private final Checks checks;
    /** {@code ChestLinkService::setSortMode}, passed in because link can't depend on chestlink. */
    private final BiConsumer<ChestLinkGroup, SortMode> setSortMode;

    /** Resolves {@code input} for {@code player}; messages and returns null if it isn't an accessible group. */
    public @Nullable StorageGroup find(Player player, GroupType type, String input) {
        return switch (links.resolve(player.getUniqueId(), AccessService.hasBypass(player), type, input)) {
            case LinkService.Resolved.Found found -> found.group();
            case LinkService.Resolved.Missing missing -> {
                services.send(player, Message.ERROR_UNKNOWN_GROUP, Messages.text("group", input));
                yield null;
            }
            case LinkService.Resolved.Refused refused -> {
                services.send(player, refused.refusal());
                yield null;
            }
        };
    }

    public boolean openRemote(Player player, StorageGroup group) {
        GroupType type = group.type();
        if (checks.refused(player, checks.enabled(type), checks.permission(player, Permissions.of(type, GroupPermission.REMOTE)),
                checks.allowedWorld(player.getWorld()), checks.use(player, group))) {
            return false;
        }
        handlers.of(group).openRemote(player, group);
        return true;
    }

    public boolean remove(Player player, StorageGroup group) {
        GroupType type = group.type();
        if (checks.refused(player, checks.enabled(type), checks.permission(player, Permissions.of(type, GroupPermission.REMOVE)),
                checks.manage(player, group))) {
            return false;
        }
        links.removeGroup(group, PaperApi.location(player));
        services.send(player, GroupMessage.REMOVED.of(type), Messages.group(group));
        return true;
    }

    public boolean rename(Player player, StorageGroup group, String newName) {
        if (checks.refused(player, checks.enabled(group.type()), checks.manage(player, group))) return false;
        return links.rename(player, group, newName);
    }

    public boolean setPublic(Player player, StorageGroup group, boolean isPublic) {
        if (checks.refused(player, checks.enabled(group.type()), checks.manage(player, group))) return false;
        links.setPublic(group, isPublic);
        services.send(player, GroupMessage.PUBLIC.of(group.type()),
                Messages.group(group),
                Messages.component("state", services.messages().get(isPublic ? Message.STATE_PUBLIC : Message.STATE_PRIVATE)));
        return true;
    }

    public boolean sort(Player player, ChestLinkGroup group, SortMode mode) {
        if (checks.refused(player, checks.enabled(group.type()), checks.permission(player, Permissions.CHESTLINK_SORT),
                checks.manage(player, group))) {
            return false;
        }
        setSortMode.accept(group, mode);
        services.send(player, Message.CHESTLINK_SORT_MODE, Messages.group(group),
                Messages.text("mode", mode.key()));
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
        if (checks.refused(player, checks.enabled(group.type()), checks.use(player, group))) return;
        if (group.members().isEmpty()) {
            services.send(player, Message.MEMBERS_NONE, Messages.group(group));
            return;
        }
        services.send(player, Message.MEMBERS_LIST, Messages.group(group),
                Messages.text("players", PlayerNames.join(group.members())));
    }

    public void trust(Player player, String name, Runnable after) {
        if (checks.refused(player, checks.permission(player, Permissions.TRUST))) return;
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
        if (checks.refused(player, checks.permission(player, Permissions.TRUST))) return;
        lookup(player, name, trusted -> {
            links.untrust(player.getUniqueId(), trusted);
            services.send(player, Message.TRUST_REMOVED, Messages.player(name));
            after.run();
        });
    }

    public void listTrust(Player player) {
        if (checks.refused(player, checks.permission(player, Permissions.TRUST))) return;
        List<UUID> trusted = List.copyOf(services.trust().trustedBy(player.getUniqueId()));
        if (trusted.isEmpty()) {
            services.send(player, Message.TRUST_NONE);
        } else {
            services.send(player, Message.TRUST_LIST, Messages.text("players", PlayerNames.join(trusted)));
        }
    }

    public void list(Player player, GroupType type) {
        if (checks.refused(player, checks.enabled(type))) return;
        AccessibleGroups accessible = services.access().accessibleGroups(player.getUniqueId(), AccessService.hasBypass(player), type);
        if (accessible.groups().isEmpty()) {
            services.send(player, GroupMessage.LIST_EMPTY.of(type));
            return;
        }
        services.send(player, GroupMessage.LIST_HEADER.of(type));
        GroupTypeHandler<?> handler = handlers.of(type);
        for (StorageGroup group : accessible.groups()) {
            String ref = StringArgumentType.escapeIfRequired(accessible.reference(group));
            String command = type.pick("/chestlink open ", "/autocraft open ") + ref;
            services.send(player, GroupMessage.LIST_ENTRY.of(type),
                    Messages.group(group), Messages.text("ref", ref), TagResolver.resolver("open", Tag.styling(ClickEvent.runCommand(command))),
                    Messages.text("owner", accessible.ownerName(group)),
                    Messages.text("nodes", services.nodes().count(group.id())),
                    Messages.text("items", handler.summary(group)));
        }
    }

    private void lookupMember(Player player, StorageGroup group, String name, Consumer<UUID> onFound) {
        if (!canEditMembers(player, group)) return;
        lookup(player, name, member -> {
            if (canEditMembers(player, group)) onFound.accept(member);
        });
    }

    /** Also run after an async lookup returns, when the group may have been deleted or the player's rights changed. */
    private boolean canEditMembers(Player player, StorageGroup group) {
        GroupType type = group.type();
        return !checks.refused(player, exists(group), checks.enabled(type), checks.permission(player, Permissions.of(type, GroupPermission.MEMBERS)),
                checks.manage(player, group));
    }

    private @Nullable Refusal exists(StorageGroup group) {
        return services.groups().byId(group.id()) == group ? null : new Refusal(Message.ERROR_UNKNOWN_GROUP, Messages.group(group));
    }

    /**
     * Resolves a player name without blocking the main thread: the cache first, then an async profile
     * lookup whose result is applied back on the main thread.
     */
    @SuppressWarnings("FutureReturnValueIgnored") // The callback handles a failed lookup.
    private void lookup(Player requester, String name, Consumer<UUID> onFound) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            onFound.accept(cached.getUniqueId());
            return;
        }
        services.send(requester, Message.TRUST_LOOKING_UP, Messages.player(name));
        PlayerProfile profile = Bukkit.createProfile(name);
        profile.update().whenComplete((completed, error) -> services.mainThread().nextTick(() -> {
            UUID id = error == null && completed != null && completed.isComplete() ? completed.getId() : null;
            if (id == null) {
                services.send(requester, Message.ERROR_PLAYER_NOT_FOUND, Messages.player(name));
            } else if (requester.isOnline()) {
                onFound.accept(id);
            }
        }));
    }
}
