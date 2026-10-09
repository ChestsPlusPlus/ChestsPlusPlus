package com.jamesdpeters.chestsplusplus.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/** A named group of linked blocks owned by a player. Mutated only on the main thread. */
public abstract sealed class StorageGroup permits ChestLinkGroup, AutoCraftGroup {

    @Getter private final long id;
    @Getter private final UUID owner;
    @Getter private final long createdAt;
    private final Set<UUID> members = new LinkedHashSet<>();
    @Getter private String name;
    @Getter private boolean isPublic;
    /** The v2 group this one was imported from ({@code type:owner:name}), so a repeated import recognises it. */
    @Getter private @Nullable String v2Source;

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

    public void setV2Source(@Nullable String v2Source) {
        this.v2Source = v2Source;
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
