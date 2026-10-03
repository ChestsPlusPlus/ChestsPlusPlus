package com.jamesdpeters.chestsplusplus.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A named group of linked blocks owned by a player. Mutated only on the main thread. */
public abstract sealed class StorageGroup permits ChestLinkGroup, AutoCraftGroup {

    private final long id;
    private final UUID owner;
    private final long createdAt;
    private final Set<UUID> members = new LinkedHashSet<>();
    private String name;
    private boolean isPublic;

    protected StorageGroup(long id, UUID owner, String name, long createdAt) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.createdAt = createdAt;
    }

    public abstract GroupType type();

    public long id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    /** Renames without re-indexing; use {@link GroupRegistry#rename}. */
    void setName(String name) {
        this.name = name;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public long createdAt() {
        return createdAt;
    }

    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    /** Membership changes go through {@link GroupRegistry} so its reverse index stays correct. */
    boolean addMember(UUID member) {
        return members.add(member);
    }

    boolean removeMember(UUID member) {
        return members.remove(member);
    }

    @Override
    public String toString() {
        return type() + "[" + id + " " + name + " owner=" + owner + "]";
    }
}
