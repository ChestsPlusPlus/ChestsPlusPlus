package com.jamesdpeters.chestsplusplus.access;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Owner-wide trust: a trusted player can use every ChestLink and AutoCrafter of the owner. Main thread only. */
public final class TrustService {

    private final Map<UUID, Set<UUID>> trusted = new HashMap<>();
    private final Map<UUID, Set<UUID>> trustedBy = new HashMap<>();
    private Consumer<UUID> onChange = owner -> {};

    /** Called with the owner whose trust list changed (persistence marks it dirty). */
    public void onChange(Consumer<UUID> listener) {
        this.onChange = listener;
    }

    public boolean trust(UUID owner, UUID player) {
        if (owner.equals(player)) return false;
        if (!trusted.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(player)) return false;
        trustedBy.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(owner);
        onChange.accept(owner);
        return true;
    }

    public boolean untrust(UUID owner, UUID player) {
        Set<UUID> set = trusted.get(owner);
        if (set == null || !set.remove(player)) return false;
        if (set.isEmpty()) trusted.remove(owner);
        Set<UUID> reverse = trustedBy.get(player);
        if (reverse != null) {
            reverse.remove(owner);
            if (reverse.isEmpty()) trustedBy.remove(player);
        }
        onChange.accept(owner);
        return true;
    }

    public boolean isTrusted(UUID owner, UUID player) {
        Set<UUID> set = trusted.get(owner);
        return set != null && set.contains(player);
    }

    /** Players {@code owner} trusts. */
    public Set<UUID> trustedBy(UUID owner) {
        return Collections.unmodifiableSet(trusted.getOrDefault(owner, Set.of()));
    }

    /** Owners who trust {@code player}. */
    public Set<UUID> ownersTrusting(UUID player) {
        return Collections.unmodifiableSet(trustedBy.getOrDefault(player, Set.of()));
    }

    /** Replaces all state (used on load). Does not fire change notifications. */
    public void load(Map<UUID, Set<UUID>> data) {
        trusted.clear();
        trustedBy.clear();
        data.forEach((owner, players) -> players.forEach(player -> {
            trusted.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(player);
            trustedBy.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(owner);
        }));
    }
}
