package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.access.AccessService;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.GroupNames;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jspecify.annotations.Nullable;

/**
 * The shared linking lifecycle for both group types (plan §5.2): resolve or create the target group, validate
 * permission, world, limit, access and protection, then link; unlink and remove; rename, public, members.
 */
public final class LinkService {

    /** The outcome of resolving user input to a group. */
    public sealed interface Resolved {
        record Found(StorageGroup group) implements Resolved {}

        /** No group: the caller may create {@code name} for {@code owner} (only when owner is the requester). */
        record Missing(UUID owner, String name) implements Resolved {}

        record Error(Message message, TagResolver... placeholders) implements Resolved {}
    }

    private final Services services;
    private final DisplayService displays;
    private final Map<GroupType, GroupTypeHandler> handlers = new EnumMap<>(GroupType.class);
    private boolean firingSyntheticInteract;

    public LinkService(Services services, DisplayService displays) {
        this.services = services;
        this.displays = displays;
    }

    public void register(GroupTypeHandler handler) {
        handlers.put(handler.type(), handler);
    }

    public @Nullable GroupTypeHandler handler(GroupType type) {
        return handlers.get(type);
    }

    public boolean isFeatureEnabled(GroupType type) {
        var features = services.settings().features();
        return handlers.containsKey(type) && type.pick(features.chestlinks(), features.autocraft());
    }

    /** Is the synthetic protection-check interact event being fired right now? Listeners must ignore it. */
    public boolean isFiringSyntheticInteract() {
        return firingSyntheticInteract;
    }

    /**
     * Resolves {@code input} ({@code name} or {@code owner:name}) to a group {@code requester} can use. Missing groups
     * of the requester come back as {@link Resolved.Missing} so callers can create them.
     */
    public Resolved resolve(UUID requester, boolean bypass, GroupType type, String input) {
        String owned = input;
        UUID owner = requester;
        int colon = input.indexOf(':');
        if (colon >= 0) {
            String ownerName = input.substring(0, colon);
            owned = input.substring(colon + 1);
            OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(ownerName);
            if (player == null) return new Resolved.Error(Message.ERROR_PLAYER_NOT_FOUND, Messages.text("player", ownerName));
            owner = player.getUniqueId();
        }
        if (!GroupNames.isValid(owned)) return new Resolved.Error(Message.ERROR_INVALID_NAME, Messages.text("name", owned));
        StorageGroup group = services.groups().find(type, owner, owned);
        if (group == null) {
            return owner.equals(requester)
                    ? new Resolved.Missing(owner, owned)
                    : new Resolved.Error(Message.ERROR_UNKNOWN_GROUP, Messages.text("group", input));
        }
        if (!services.access().canAccess(requester, bypass, group)) {
            return new Resolved.Error(Message.ERROR_NO_ACCESS, Messages.text("group", input));
        }
        return new Resolved.Found(group);
    }

    /**
     * Links {@code block} to the group named by {@code input}, creating it when it is the player's own and missing.
     * All checks happen here; failures are messaged to the player. Returns the group on success.
     *
     * @param protectionChecked true when the block was just placed or edited by the player (e.g. a sign), so
     *     protection plugins already approved it; otherwise a synthetic interact event is fired first
     */
    public @Nullable StorageGroup link(Player player, GroupType type, String input, Block block, BlockFace facing, boolean protectionChecked) {
        var messages = services.messages();
        GroupTypeHandler handler = handlers.get(type);
        if (handler == null || !isFeatureEnabled(type)) {
            messages.send(player, Message.ERROR_FEATURE_DISABLED);
            return null;
        }
        if (!player.hasPermission(Permissions.create(type))) {
            messages.send(player, Message.ERROR_NO_PERMISSION);
            return null;
        }
        if (services.settings().isBlacklisted(block.getWorld().getName())) {
            messages.send(player, Message.ERROR_WORLD_BLACKLISTED);
            return null;
        }
        if (!handler.isValidBlock(block)) {
            messages.send(player, Message.ERROR_INVALID_BLOCK, Messages.text("type", type.displayName()));
            return null;
        }
        Node existing = services.nodes().get(BlockPos.of(block));
        if (existing != null) {
            StorageGroup linkedTo = services.groups().byId(existing.groupId());
            messages.send(player, Message.ERROR_ALREADY_LINKED, Messages.text("group", linkedTo == null ? "?" : linkedTo.name()));
            return null;
        }
        if (!protectionChecked && !passesProtection(player, block, facing)) {
            messages.send(player, Message.ERROR_PROTECTED);
            return null;
        }

        boolean bypass = AccessService.hasBypass(player);
        StorageGroup group;
        boolean created = false;
        switch (resolve(player.getUniqueId(), bypass, type, input)) {
            case Resolved.Found found -> group = found.group();
            case Resolved.Error error -> {
                messages.send(player, error.message(), error.placeholders());
                return null;
            }
            case Resolved.Missing missing -> {
                int limit = limit(player, type);
                if (limit >= 0 && services.groups().ownedBy(player.getUniqueId(), type).size() >= limit) {
                    messages.send(player, Message.ERROR_LIMIT_REACHED, Messages.text("limit", Integer.toString(limit)),
                            Messages.text("type", type.displayName()));
                    return null;
                }
                group = handler.create(services.groups().nextId(), missing.owner(), missing.name());
                services.groups().add(group);
                created = true;
            }
        }
        addNode(group, block, facing);
        int overflow = handler.onLinked(group, block);
        messages.send(player,
                created
                        ? type.pick(Message.CHESTLINK_CREATED, Message.AUTOCRAFT_CREATED)
                        : type.pick(Message.CHESTLINK_LINKED, Message.AUTOCRAFT_LINKED),
                Messages.text("group", group.name()));
        if (overflow > 0) messages.send(player, Message.CHESTLINK_OVERFLOW, Messages.text("count", Integer.toString(overflow)));
        return group;
    }

    /** Adds a node without checks (used after validation, and by silk-touch re-linking). */
    public Node addNode(StorageGroup group, Block block, BlockFace facing) {
        if (block.getBlockData() instanceof Chest) splitDoubleChest(block);
        Node node = new Node(BlockPos.of(block), facing, group.id());
        services.nodes().put(node);
        services.persistence().markDirty(group);
        displays.nodeAdded(node);
        displays.requestUpdate(group);
        return node;
    }

    /**
     * Removes the node at {@code pos}. When it was the group's last node and {@code keepGroup} is false, the group is
     * removed and its contents dropped at {@code dropAt}. Returns the affected group (null if there was no node).
     */
    public @Nullable StorageGroup unlink(BlockPos pos, Location dropAt, boolean keepGroup) {
        Node node = services.nodes().remove(pos);
        if (node == null) return null;
        displays.nodeRemoved(node);
        StorageGroup group = services.groups().byId(node.groupId());
        if (group == null) return null;
        if (!keepGroup && services.nodes().count(group.id()) == 0) {
            removeGroup(group, dropAt);
        } else {
            services.persistence().markDirty(group);
        }
        return group;
    }

    /** Deletes a group: its contents are dropped at {@code dropAt}, its nodes unlinked, and its rows deleted. */
    public void removeGroup(StorageGroup group, Location dropAt) {
        GroupTypeHandler handler = handlers.get(group.type());
        if (handler != null) handler.onRemoved(group, dropAt);
        for (Node node : services.nodes().removeGroup(group.id())) displays.nodeRemoved(node);
        services.groups().remove(group);
        services.persistence().markDeleted(group);
    }

    /** Renames a group, messaging {@code audience} on failure. */
    public boolean rename(Audience audience, StorageGroup group, String newName) {
        var messages = services.messages();
        if (!GroupNames.isValid(newName)) {
            messages.send(audience, Message.ERROR_INVALID_NAME, Messages.text("name", newName));
            return false;
        }
        StorageGroup existing = services.groups().find(group.type(), group.owner(), newName);
        if (existing != null && existing != group) {
            messages.send(audience, Message.ERROR_GROUP_EXISTS, Messages.text("group", newName));
            return false;
        }
        String old = group.name();
        services.groups().rename(group, newName);
        GroupTypeHandler handler = handlers.get(group.type());
        if (handler != null) handler.onRenamed(group);
        services.persistence().markDirty(group);
        displays.requestUpdate(group);
        messages.send(audience, group.type().pick(Message.CHESTLINK_RENAMED, Message.AUTOCRAFT_RENAMED),
                Messages.text("old", old), Messages.text("new", newName));
        return true;
    }

    public void setPublic(StorageGroup group, boolean isPublic) {
        group.setPublic(isPublic);
        services.persistence().markDirty(group);
    }

    public boolean addMember(StorageGroup group, UUID member) {
        if (member.equals(group.owner()) || !services.groups().addMember(group, member)) return false;
        services.persistence().markDirty(group);
        return true;
    }

    public boolean removeMember(StorageGroup group, UUID member) {
        if (!services.groups().removeMember(group, member)) return false;
        services.persistence().markDirty(group);
        return true;
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
        var limits = services.settings().limits();
        return type.pick(limits.chestlinkDefault(), limits.autocraftDefault());
    }

    /**
     * Protection check for links that don't come from a real placement (plan §5.2): fires a synthetic right-click and
     * requires that no plugin denied using the block.
     */
    public boolean passesProtection(Player player, Block block, BlockFace face) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(), block, face,
                EquipmentSlot.HAND);
        firingSyntheticInteract = true;
        try {
            Bukkit.getPluginManager().callEvent(event);
        } finally {
            firingSyntheticInteract = false;
        }
        return event.useInteractedBlock() != Event.Result.DENY;
    }

    /**
     * Groups of {@code type} the player can use: their own, member-of, owners who trust them, public ones (and all
     * groups with bypass). Own groups first, then by owner name and group name.
     */
    public java.util.List<StorageGroup> accessibleGroups(UUID player, boolean bypass, GroupType type) {
        java.util.Set<StorageGroup> found = new java.util.LinkedHashSet<>();
        if (bypass) {
            found.addAll(services.groups().all(type));
        } else {
            found.addAll(services.groups().ownedBy(player, type));
            for (StorageGroup group : services.groups().memberOf(player)) if (group.type() == type) found.add(group);
            for (UUID owner : services.trust().ownersTrusting(player)) found.addAll(services.groups().ownedBy(owner, type));
            for (StorageGroup group : services.groups().all(type)) if (group.isPublic()) found.add(group);
        }
        java.util.Comparator<StorageGroup> order = java.util.Comparator.<StorageGroup, Boolean>comparing(g -> !g.owner().equals(player))
                .thenComparing(g -> ownerName(g.owner()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StorageGroup::name, String.CASE_INSENSITIVE_ORDER);
        return found.stream().sorted(order).toList();
    }

    /** How {@code requester} refers to a group in commands: {@code name}, or {@code owner:name} if not theirs. */
    public static String reference(UUID requester, StorageGroup group) {
        return group.owner().equals(requester) ? group.name() : ownerName(group.owner()) + ":" + group.name();
    }

    /** The owner's last known name (never a blocking lookup). */
    public static String ownerName(UUID owner) {
        String name = Bukkit.getOfflinePlayer(owner).getName();
        return name == null ? owner.toString().substring(0, 8) : name;
    }

    /** Linked chests are always single (plan §5.2): split this chest and its partner. */
    public static void splitDoubleChest(Block block) {
        if (!(block.getBlockData() instanceof Chest data) || data.getType() == Chest.Type.SINGLE) return;
        BlockFace towardsPartner = partnerDirection(data);
        Block partner = block.getRelative(towardsPartner);
        data.setType(Chest.Type.SINGLE);
        block.setBlockData(data, false);
        if (partner.getBlockData() instanceof Chest partnerData && partnerData.getType() != Chest.Type.SINGLE) {
            partnerData.setType(Chest.Type.SINGLE);
            partner.setBlockData(partnerData, false);
        }
    }

    /** Vanilla: a LEFT half's partner is clockwise of its facing, a RIGHT half's counter-clockwise. */
    public static BlockFace partnerDirection(Chest data) {
        BlockFace facing = data.getFacing();
        boolean clockwise = data.getType() == Chest.Type.LEFT;
        return switch (facing) {
            case NORTH -> clockwise ? BlockFace.EAST : BlockFace.WEST;
            case EAST -> clockwise ? BlockFace.SOUTH : BlockFace.NORTH;
            case SOUTH -> clockwise ? BlockFace.WEST : BlockFace.EAST;
            case WEST -> clockwise ? BlockFace.NORTH : BlockFace.SOUTH;
            default -> BlockFace.SELF;
        };
    }
}
