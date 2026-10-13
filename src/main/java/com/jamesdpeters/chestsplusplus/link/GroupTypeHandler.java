package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Type-specific hooks used by the shared {@link LinkService} (ChestLink, AutoCraft). The public methods take any group of this type,
 * narrow it to {@code G} once, and call the typed hook; they are final so every handler narrows the same way.
 */
public abstract class GroupTypeHandler<G extends StorageGroup> {

    @Getter private final GroupType type;
    private final Class<G> groupClass;

    protected GroupTypeHandler(GroupType type, Class<G> groupClass) {
        this.type = type;
        this.groupClass = groupClass;
    }

    /** Can a block of this kind be linked to this group type? */
    public abstract boolean isValidBlock(Block block);

    /** Creates (but does not register) a new group. */
    public abstract G create(long id, UUID owner, String name);

    /** Closes every view players have open of this type's groups (inventories, editors). */
    public abstract void closeAllViews();

    /** A block was just linked to {@code group} (e.g. absorb its contents). Returns overflowed stack count. */
    public final int onLinked(StorageGroup group, Block block) {
        return linked(narrow(group), block);
    }

    /** The group is being deleted; drop or discard whatever it holds at {@code dropAt}. */
    public final void onRemoved(StorageGroup group, Location dropAt) {
        removed(narrow(group), dropAt);
    }

    /** The group was renamed or its access changed. */
    public final void onRenamed(StorageGroup group) {
        renamed(narrow(group));
    }

    /** {@code group}'s membership, trust or visibility changed: closes the views of players who may no longer use it. */
    public final void closeRevokedViews(StorageGroup group) {
        revokedViews(narrow(group));
    }

    /** Opens the group for a player away from its blocks (command, menu, dialog); checks are done by the caller. */
    public final void openRemote(Player player, StorageGroup group) {
        openRemotely(player, narrow(group));
    }

    /** Icon for menus and dialogs. */
    public final ItemStack icon(StorageGroup group) {
        return iconOf(narrow(group));
    }

    /** Short contents summary for menus and listings, e.g. "1,234 items" or "Torch". */
    public final String summary(StorageGroup group) {
        return summaryOf(narrow(group));
    }

    protected abstract int linked(G group, Block block);

    protected abstract void removed(G group, Location dropAt);

    protected abstract void renamed(G group);

    protected abstract void revokedViews(G group);

    protected abstract void openRemotely(Player player, G group);

    protected abstract ItemStack iconOf(G group);

    protected abstract String summaryOf(G group);

    private G narrow(StorageGroup group) {
        return groupClass.cast(group);
    }
}
