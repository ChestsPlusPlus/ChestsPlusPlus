package com.jamesdpeters.chestsplusplus.ui;

import com.jamesdpeters.chestsplusplus.access.AccessibleGroups;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.Checks;
import com.jamesdpeters.chestsplusplus.link.GroupActions;
import com.jamesdpeters.chestsplusplus.link.Handlers;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.ui.menu.MenuListener;
import com.jamesdpeters.chestsplusplus.ui.menu.PaginatedMenu;
import org.bukkit.entity.Player;

/**
 * Menus: the Dialog hub, the group, members and trust dialogs, and the icon grid. It builds the screens and is how they open each other.
 * Every action goes through {@link GroupActions}, which re-checks permissions.
 */
public final class UiService implements Navigator {

    private final HubScreen hubScreen;
    private final GroupScreen groupScreen;
    private final MembersScreen membersScreen;
    private final TrustScreen trustScreen;
    private final GridMenu gridMenu;

    public UiService(Services services, Handlers handlers, Checks checks, GroupActions actions, MenuListener menus) {
        Dialogs dialogs = new Dialogs(services, handlers, actions, menus);
        this.hubScreen = new HubScreen(services.access(), checks, dialogs, this);
        this.groupScreen = new GroupScreen(services, checks, actions, dialogs, this);
        this.membersScreen = new MembersScreen(checks, actions, dialogs, this);
        this.trustScreen = new TrustScreen(services.trust(), checks, actions, dialogs, this);
        this.gridMenu = new GridMenu(hubScreen, dialogs, this);
    }

    /** Groups shown in the hub for {@code search} (case-insensitive substring of name or owner). */
    public AccessibleGroups hubGroups(Player player, GroupType type, String search) {
        return hubScreen.groups(player, type, search);
    }

    @Override
    public void openHub(Player player, GroupType type, String search, int page) {
        hubScreen.open(player, type, search, page);
    }

    @Override
    public void openGroup(Player player, StorageGroup group) {
        groupScreen.open(player, group);
    }

    @Override
    public void openMembers(Player player, StorageGroup group) {
        membersScreen.open(player, group);
    }

    @Override
    public void openTrust(Player player, GroupType backTo) {
        trustScreen.open(player, backTo);
    }

    @Override
    public PaginatedMenu openGrid(Player player, GroupType type) {
        return gridMenu.open(player, type);
    }
}
