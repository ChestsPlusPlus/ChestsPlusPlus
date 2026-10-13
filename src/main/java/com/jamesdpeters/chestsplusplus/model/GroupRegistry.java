package com.jamesdpeters.chestsplusplus.model;

import com.jamesdpeters.chestsplusplus.core.Listeners;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * All groups, indexed by id, by type + owner + lower-cased name, by owner and by member. Main thread only. Its mutators tell the
 * listeners (which save and redisplay groups); anything else that changes a group calls {@link #changed}. Listeners only hear about
 * added groups: {@link #add} is silent, and so is anything done to a group before it, which keeps loading from dirtying every group.
 */
public final class GroupRegistry {

    private record NameKey(GroupType type, UUID owner, String name) {}

    private final Long2ObjectMap<StorageGroup> byId = new Long2ObjectLinkedOpenHashMap<>();
    private final Map<NameKey, StorageGroup> byName = new HashMap<>();
    private final Map<UUID, Set<StorageGroup>> byOwner = new HashMap<>();
    private final Map<UUID, Set<StorageGroup>> byMember = new HashMap<>();
    private final Listeners<StorageGroup> changeListeners = new Listeners<>();
    private final Listeners<StorageGroup> removeListeners = new Listeners<>();
    private long nextId = 1;

    public void listen(GroupListener listener) {
        changeListeners.add(listener::changed);
        removeListeners.add(listener::removed);
    }

    /** Tells the listeners about a change the registry can't see, such as contents, a recipe or nodes. */
    public void changed(StorageGroup group) {
        if (added(group)) changeListeners.fire(group);
    }

    public long nextId() {
        return nextId++;
    }

    /** Ids up to and including {@code id} are never handed out again, even when no group has them any more. */
    public void reserveThrough(long id) {
        nextId = Math.max(nextId, id + 1);
    }

    /** Silent: a new group is announced by its first change, and a loaded one isn't announced at all. */
    public void add(StorageGroup group) {
        NameKey key = key(group.type(), group.owner(), group.name());
        if (byId.containsKey(group.id())) throw new IllegalStateException("Duplicate group id " + group.id());
        if (byName.containsKey(key)) throw new IllegalStateException("Duplicate group name " + key);
        byId.put(group.id(), group);
        byName.put(key, group);
        byOwner.computeIfAbsent(group.owner(), k -> new HashSet<>()).add(group);
        group.members().forEach(member -> byMember.computeIfAbsent(member, k -> new HashSet<>()).add(group));
        reserveThrough(group.id());
    }

    public void remove(StorageGroup group) {
        if (byId.remove(group.id()) == null) return;
        byName.remove(key(group.type(), group.owner(), group.name()));
        removeFrom(byOwner, group.owner(), group);
        group.members().forEach(member -> removeFrom(byMember, member, group));
        removeListeners.fire(group);
    }

    public void rename(StorageGroup group, String newName) {
        StorageGroup existing = find(group.type(), group.owner(), newName);
        if (existing != null && existing != group) throw new IllegalStateException("Name already used: " + newName);
        byName.remove(key(group.type(), group.owner(), group.name()));
        group.setName(newName);
        byName.put(key(group.type(), group.owner(), newName), group);
        changed(group);
    }

    public boolean addMember(StorageGroup group, UUID member) {
        if (!group.addMember(member)) return false;
        if (added(group)) byMember.computeIfAbsent(member, k -> new HashSet<>()).add(group);
        changed(group);
        return true;
    }

    public boolean removeMember(StorageGroup group, UUID member) {
        if (!group.removeMember(member)) return false;
        removeFrom(byMember, member, group);
        changed(group);
        return true;
    }

    public @Nullable StorageGroup byId(long id) {
        return byId.get(id);
    }

    public @Nullable StorageGroup find(GroupType type, UUID owner, String name) {
        return byName.get(key(type, owner, name));
    }

    public List<StorageGroup> ownedBy(UUID owner, GroupType type) {
        return byOwner.getOrDefault(owner, Set.of()).stream().filter(g -> g.type() == type)
                .sorted(Comparator.comparing(StorageGroup::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Set<StorageGroup> memberOf(UUID player) {
        return Collections.unmodifiableSet(byMember.getOrDefault(player, Set.of()));
    }

    public List<StorageGroup> all(GroupType type) {
        return byId.values().stream().filter(group -> group.type() == type).toList();
    }

    public Collection<StorageGroup> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() {
        return byId.size();
    }

    private boolean added(StorageGroup group) {
        return byId.get(group.id()) == group;
    }

    private static NameKey key(GroupType type, UUID owner, String name) {
        return new NameKey(type, owner, GroupNames.normalise(name));
    }

    private static void removeFrom(Map<UUID, Set<StorageGroup>> index, UUID key, StorageGroup group) {
        Set<StorageGroup> set = index.get(key);
        if (set == null) return;
        set.remove(group);
        if (set.isEmpty()) index.remove(key);
    }
}
