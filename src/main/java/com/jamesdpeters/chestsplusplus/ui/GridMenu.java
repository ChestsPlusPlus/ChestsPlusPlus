package com.jamesdpeters.chestsplusplus.ui;

import com.jamesdpeters.chestsplusplus.access.AccessibleGroups;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.ui.menu.PaginatedMenu;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** The icon grid: a chest menu of the groups the hub would list. */
@RequiredArgsConstructor
final class GridMenu {

    private final HubScreen hub;
    private final Dialogs dialogs;
    private final Navigator navigator;

    PaginatedMenu open(Player player, GroupType type) {
        List<PaginatedMenu.Entry> entries = new ArrayList<>();
        AccessibleGroups accessible = hub.groups(player, type, "");
        for (StorageGroup group : accessible.groups()) {
            ItemStack icon = dialogs.namedIcon(group, Message.MENU_GRID_ENTRY_LORE,
                    Messages.text("owner", accessible.ownerName(group)),
                    Messages.text("items", dialogs.summary(group)));
            entries.add(new PaginatedMenu.Entry(icon, (p, click) -> dialogs.withGroup(p, group.id(), g -> onClick(p, g, click))));
        }
        PaginatedMenu menu = new PaginatedMenu(6, dialogs.text(Message.MENU_GRID_TITLE), entries, dialogs.text(Message.MENU_GRID_PREVIOUS),
                dialogs.text(Message.MENU_GRID_NEXT));
        menu.open(player);
        return menu;
    }

    /** Right-click opens the group dialog; left-click opens the group itself and comes back to the grid afterwards. */
    private void onClick(Player player, StorageGroup group, ClickType click) {
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            player.closeInventory();
            navigator.openGroup(player, group);
        } else {
            dialogs.openRemote(player, group, () -> open(player, group.type()));
        }
    }
}
