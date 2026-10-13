package com.jamesdpeters.chestsplusplus.ui;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.ui.menu.PaginatedMenu;
import org.bukkit.entity.Player;

/** How the screens open each other, so none of them holds another. */
interface Navigator {

    void openHub(Player player, GroupType type, String search, int page);

    void openGroup(Player player, StorageGroup group);

    void openMembers(Player player, StorageGroup group);

    void openTrust(Player player, GroupType backTo);

    PaginatedMenu openGrid(Player player, GroupType type);
}
