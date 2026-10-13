package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.model.GroupType;

/** An action every group type has its own permission node for. {@link Permissions#of} gives the node. */
public enum GroupPermission {
    CREATE,
    OPEN,
    REMOTE,
    MENU,
    REMOVE,
    MEMBERS;

    final String chestlink = Permissions.node(GroupType.CHESTLINK, this);
    final String autocraft = Permissions.node(GroupType.AUTOCRAFT, this);
}
