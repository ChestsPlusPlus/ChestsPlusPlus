package com.jamesdpeters.chestsplusplus.model;

import java.util.UUID;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.jspecify.annotations.Nullable;

/** A ChestLink: every node shares one 54-slot inventory. */
public final class ChestLinkGroup extends StorageGroup {

    public static final int SIZE = 54;

    @Getter private SortMode sortMode = SortMode.OFF;
    private @Nullable Inventory inventory;

    public ChestLinkGroup(long id, UUID owner, String name, long createdAt) {
        super(id, owner, name, createdAt);
    }

    @Override
    public GroupType type() {
        return GroupType.CHESTLINK;
    }

    public void setSortMode(SortMode sortMode) {
        this.sortMode = sortMode;
    }

    /** The shared inventory, attached by the ChestLink service once the group is created or loaded. */
    public Inventory inventory() {
        if (inventory == null) throw new IllegalStateException(this + " has no inventory attached");
        return inventory;
    }

    public boolean hasInventory() {
        return inventory != null;
    }

    public void attachInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
