package com.jamesdpeters.chestsplusplus.model;

import com.jamesdpeters.chestsplusplus.core.PlayerNames;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

/** A named group of linked blocks owned by a player. Mutated only on the main thread. */
public abstract sealed class StorageGroup permits ChestLinkGroup, AutoCraftGroup {

    @Getter private final long id;
    @Getter private final UUID owner;
    @Getter private final long createdAt;
    private final Set<UUID> members = new LinkedHashSet<>();
    @Getter private String name;
    @Getter private boolean isPublic;

    protected StorageGroup(long id, UUID owner, String name, long createdAt) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.createdAt = createdAt;
    }

    public abstract GroupType type();

    /** Renames without re-indexing; use {@link GroupRegistry#rename}. */
    void setName(String name) {
        this.name = name;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
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

    /** How {@code requester} refers to this group in commands: {@code name}, or {@code owner:name} if it isn't theirs. */
    public String referenceFor(UUID requester) {
        return owner.equals(requester) ? name : PlayerNames.of(owner) + ":" + name;
    }

    @Override
    public String toString() {
        return type() + "[" + id + " " + name + " owner=" + owner + "]";
    }
}
