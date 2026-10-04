package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Type-specific hooks used by the shared {@link LinkService} (ChestLink, AutoCraft). */
public interface GroupTypeHandler {

    GroupType type();

    /** Can a block of this kind be linked to this group type? */
    boolean isValidBlock(Block block);

    /** Creates (but does not register) a new group. */
    StorageGroup create(long id, UUID owner, String name);

    /** A block was just linked to {@code group} (e.g. absorb its contents). Returns overflowed stack count. */
    int onLinked(StorageGroup group, Block block);

    /** The group is being deleted; drop or discard whatever it holds at {@code dropAt}. */
    void onRemoved(StorageGroup group, Location dropAt);

    /** The group was renamed or its access changed. */
    void onRenamed(StorageGroup group);

    /** Opens the group for a player away from its blocks (command, menu, dialog); checks are done by the caller. */
    void openRemote(Player player, StorageGroup group);

    /** Icon for menus and dialogs. */
    ItemStack icon(StorageGroup group);

    /** Short contents summary for menus and listings, e.g. "1,234 items" or "Torch". */
    String summary(StorageGroup group);
}
