package com.jamesdpeters.chestsplusplus.model;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** All groups, indexed by id, by type + owner + lower-cased name, by owner and by member. Main thread only. */
public final class GroupRegistry {

    private record NameKey(GroupType type, UUID owner, String name) {}

    private final Map<Long, StorageGroup> byId = new LinkedHashMap<>();
    private final Map<NameKey, StorageGroup> byName = new HashMap<>();
    private final Map<UUID, Set<StorageGroup>> byOwner = new HashMap<>();
    private final Map<UUID, Set<StorageGroup>> byMember = new HashMap<>();
    private long nextId = 1;

    public long nextId() {
        return nextId++;
    }

    public void add(StorageGroup group) {
        NameKey key = key(group.type(), group.owner(), group.name());
        if (byId.containsKey(group.id())) throw new IllegalStateException("Duplicate group id " + group.id());
        if (byName.containsKey(key)) throw new IllegalStateException("Duplicate group name " + key);
        byId.put(group.id(), group);
        byName.put(key, group);
        byOwner.computeIfAbsent(group.owner(), k -> new HashSet<>()).add(group);
        group.members().forEach(member -> byMember.computeIfAbsent(member, k -> new HashSet<>()).add(group));
        nextId = Math.max(nextId, group.id() + 1);
    }

    public void remove(StorageGroup group) {
        if (byId.remove(group.id()) == null) return;
        byName.remove(key(group.type(), group.owner(), group.name()));
        removeFrom(byOwner, group.owner(), group);
        group.members().forEach(member -> removeFrom(byMember, member, group));
    }

    public void rename(StorageGroup group, String newName) {
        StorageGroup existing = find(group.type(), group.owner(), newName);
        if (existing != null && existing != group) throw new IllegalStateException("Name already used: " + newName);
        byName.remove(key(group.type(), group.owner(), group.name()));
        group.setName(newName);
        byName.put(key(group.type(), group.owner(), newName), group);
    }

    public boolean addMember(StorageGroup group, UUID member) {
        if (!group.addMember(member)) return false;
        byMember.computeIfAbsent(member, k -> new HashSet<>()).add(group);
        return true;
    }

    public boolean removeMember(StorageGroup group, UUID member) {
        if (!group.removeMember(member)) return false;
        removeFrom(byMember, member, group);
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
