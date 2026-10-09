package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jspecify.annotations.Nullable;

/**
 * The shared linking lifecycle for both group types: resolve or create the target group, validate permission, world, limit, access and
 * protection, then link; unlink and remove; rename, public, members.
 */
@Slf4j(topic = ChestsPlusPlus.NAME)
@RequiredArgsConstructor
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

    /** Closes what players have open of every group whose feature is now off, so {@code /cpp reload} takes effect at once. */
    public void closeDisabledViews() {
        handlers.values().stream().filter(handler -> !isFeatureEnabled(handler.type())).forEach(GroupTypeHandler::closeAllViews);
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
            if (player == null) return new Resolved.Error(Message.ERROR_PLAYER_NOT_FOUND, Messages.player(ownerName));
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
     * @param protectionChecked true only when this comes from a real right-click on {@code block} that no plugin denied, so
     *     protection plugins already approved using it; otherwise a synthetic interact event is fired first
     */
    public @Nullable StorageGroup link(Player player, GroupType type, String input, Block block, BlockFace facing, boolean protectionChecked) {
        Resolved.Error refusal = linkRefusal(player, type, block, facing, protectionChecked);
        if (refusal != null) {
            send(player, refusal);
            return null;
        }
        GroupTypeHandler handler = Objects.requireNonNull(handlers.get(type));
        Resolved target = resolve(player.getUniqueId(), AccessService.hasBypass(player), type, input);
        StorageGroup group = switch (target) {
            case Resolved.Found found -> found.group();
            case Resolved.Missing missing -> createGroup(player, handler, missing);
            case Resolved.Error error -> {
                send(player, error);
                yield null;
            }
        };
        if (group == null) return null;

        Message linked = target instanceof Resolved.Missing
                ? type.pick(Message.CHESTLINK_CREATED, Message.AUTOCRAFT_CREATED)
                : type.pick(Message.CHESTLINK_LINKED, Message.AUTOCRAFT_LINKED);
        join(player, group, block, facing, linked);
        return group;
    }

    /** Adds {@code block} to {@code group}, runs the type's link hook and tells the player. */
    void join(Player player, StorageGroup group, Block block, BlockFace facing, Message message) {
        addNode(group, block, facing);
        int overflow = Objects.requireNonNull(handlers.get(group.type())).onLinked(group, block);
        services.send(player, message, Messages.group(group));
        if (overflow > 0) services.send(player, Message.CHESTLINK_OVERFLOW, Messages.text("count", overflow));
    }

    /** Why {@code player} may not link {@code block}, or null if they may. The protection check runs last as it fires an event. */
    private Resolved.@Nullable Error linkRefusal(Player player, GroupType type, Block block, BlockFace facing, boolean protectionChecked) {
        Message refusal = linkingRefusal(player, type, block.getWorld());
        if (refusal != null) return new Resolved.Error(refusal);
        GroupTypeHandler handler = Objects.requireNonNull(handlers.get(type));
        if (!handler.isValidBlock(block)) return new Resolved.Error(Message.ERROR_INVALID_BLOCK, Messages.text("type", type.displayName()));
        Node existing = services.nodes().at(block);
        if (existing != null) {
            StorageGroup linkedTo = services.groups().byId(existing.groupId());
            return new Resolved.Error(Message.ERROR_ALREADY_LINKED, Messages.text("group", linkedTo == null ? "?" : linkedTo.name()));
        }
        if (!protectionChecked && !passesProtection(player, block, facing)) return new Resolved.Error(Message.ERROR_PROTECTED);
        return null;
    }

    /** The checks every way of linking shares: feature enabled, create permission, world not blacklisted. */
    @Nullable
    Message linkingRefusal(Player player, GroupType type, World world) {
        if (!isFeatureEnabled(type)) return Message.ERROR_FEATURE_DISABLED;
        if (!player.hasPermission(Permissions.create(type))) return Message.ERROR_NO_PERMISSION;
        if (services.settings().isBlacklisted(world.getName())) return Message.ERROR_WORLD_BLACKLISTED;
        return null;
    }

    private @Nullable StorageGroup createGroup(Player player, GroupTypeHandler handler, Resolved.Missing missing) {
        GroupType type = handler.type();
        int limit = limit(player, type);
        if (limit >= 0 && services.groups().ownedBy(player.getUniqueId(), type).size() >= limit) {
            services.send(player, Message.ERROR_LIMIT_REACHED, Messages.text("limit", limit), Messages.text("type", type.displayName()));
            return null;
        }
        StorageGroup group = handler.create(services.groups().nextId(), missing.owner(), missing.name());
        services.groups().add(group);
        return group;
    }

    public void send(Audience audience, Resolved.Error error) {
        services.send(audience, error.message(), error.placeholders());
    }

    /** Adds a node without checks (used after validation, and by silk-touch re-linking). */
    public Node addNode(StorageGroup group, Block block, BlockFace facing) {
        if (block.getBlockData() instanceof Chest) DoubleChests.split(block);
        Node node = new Node(BlockPos.of(block), front(block, facing), group.id());
        services.nodes().put(node);
        services.groupStore().markDirty(group);
        displays.nodeAdded(node);
        displays.requestUpdate(group);
        return node;
    }

    /** Chests and barrels show their link on their front; blocks without a horizontal front use {@code fallback}. */
    public static BlockFace front(Block block, BlockFace fallback) {
        if (block.getBlockData() instanceof Directional directional && directional.getFacing().getModY() == 0) return directional.getFacing();
        return fallback;
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
            services.groupStore().markDirty(group);
        }
        return group;
    }

    /** Unlinks nodes whose block was changed behind our back (WorldEdit, or while the plugin was removed). Their chunks must be loaded. */
    public void unlinkChangedBlocks(List<Node> nodes) {
        for (Node node : nodes) {
            StorageGroup group = services.groups().byId(node.groupId());
            GroupTypeHandler handler = group == null ? null : handlers.get(group.type());
            Block block = node.pos().block();
            if (handler == null || block == null || handler.isValidBlock(block)) continue;
            log.warn("Unlinking {} from {}: block is now {}", node.pos(), group.name(), block.getType());
            unlink(node.pos(), block.getLocation(), true);
        }
    }

    /** Validates the nodes in every chunk that is already loaded; chunks loaded later are validated by {@code NodeListener}. */
    public void unlinkChangedBlocksInLoadedChunks() {
        unlinkChangedBlocks(services.nodes().all().stream().filter(node -> node.pos().isLoaded()).toList());
    }

    /** Deletes a group: its contents are dropped at {@code dropAt}, its nodes unlinked, and its rows deleted. */
    public void removeGroup(StorageGroup group, Location dropAt) {
        GroupTypeHandler handler = handlers.get(group.type());
        if (handler != null) handler.onRemoved(group, dropAt);
        for (Node node : services.nodes().removeGroup(group.id())) displays.nodeRemoved(node);
        services.groups().remove(group);
        services.groupStore().markDirty(group);
    }

    /** Renames a group, messaging {@code audience} on failure. */
    public boolean rename(Audience audience, StorageGroup group, String newName) {
        if (!GroupNames.isValid(newName)) {
            services.send(audience, Message.ERROR_INVALID_NAME, Messages.text("name", newName));
            return false;
        }
        StorageGroup existing = services.groups().find(group.type(), group.owner(), newName);
        if (existing != null && existing != group) {
            services.send(audience, Message.ERROR_GROUP_EXISTS, Messages.text("group", newName));
            return false;
        }
        String old = group.name();
        services.groups().rename(group, newName);
        GroupTypeHandler handler = handlers.get(group.type());
        if (handler != null) handler.onRenamed(group);
        services.groupStore().markDirty(group);
        displays.requestUpdate(group);
        services.send(audience, group.type().pick(Message.CHESTLINK_RENAMED, Message.AUTOCRAFT_RENAMED),
                Messages.text("old", old), Messages.text("new", newName));
        return true;
    }

    public void setPublic(StorageGroup group, boolean isPublic) {
        group.setPublic(isPublic);
        services.groupStore().markDirty(group);
    }

    public boolean addMember(StorageGroup group, UUID member) {
        if (member.equals(group.owner()) || !services.groups().addMember(group, member)) return false;
        services.groupStore().markDirty(group);
        return true;
    }

    public boolean removeMember(StorageGroup group, UUID member) {
        if (!services.groups().removeMember(group, member)) return false;
        services.groupStore().markDirty(group);
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
     * The shared part of right-clicking a linked block of {@code type}: takes the click over from vanilla and checks the player may open the
     * group. Returns the clicked node when the caller should open it. Sneaking with an item still places blocks, as in vanilla.
     */
    public @Nullable Node claimNodeClick(PlayerInteractEvent event, GroupType type) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || firingSyntheticInteract) return null;
        Block block = event.getClickedBlock();
        Node node = block == null ? null : services.nodes().at(block);
        StorageGroup group = node == null ? null : services.groups().byId(node.groupId());
        if (group == null || group.type() != type) return null;
        Player player = event.getPlayer();
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().isEmpty()) return null;
        if (event.useInteractedBlock() == Event.Result.DENY) return null;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) return null;

        Message refusal = openRefusal(player, group, block);
        if (refusal == null) return node;
        services.send(player, refusal, Messages.group(group));
        return null;
    }

    private @Nullable Message openRefusal(Player player, StorageGroup group, Block block) {
        if (!isFeatureEnabled(group.type())) return Message.ERROR_FEATURE_DISABLED;
        if (services.settings().isBlacklisted(block.getWorld().getName())) return Message.ERROR_WORLD_BLACKLISTED;
        if (!player.hasPermission(Permissions.open(group.type()))) return Message.ERROR_NO_PERMISSION;
        if (!services.access().canAccess(player.getUniqueId(), player, group)) return Message.ERROR_NO_ACCESS;
        return null;
    }

    /**
     * Protection check for links that don't come from a real placement: fires a synthetic right-click and
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
}
