package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import java.util.Locale;

/** Permission nodes. Defaults are declared in the build script, which generates paper-plugin.yml. */
public final class Permissions {

    public static final String CHESTLINK_CREATE = "chestsplusplus.chestlink.create";
    public static final String CHESTLINK_OPEN = "chestsplusplus.chestlink.open";
    public static final String CHESTLINK_REMOTE = "chestsplusplus.chestlink.remote";
    public static final String CHESTLINK_MENU = "chestsplusplus.chestlink.menu";
    public static final String CHESTLINK_REMOVE = "chestsplusplus.chestlink.remove";
    public static final String CHESTLINK_SORT = "chestsplusplus.chestlink.sort";
    public static final String CHESTLINK_MEMBERS = "chestsplusplus.chestlink.members";

    public static final String AUTOCRAFT_CREATE = "chestsplusplus.autocraft.create";
    public static final String AUTOCRAFT_OPEN = "chestsplusplus.autocraft.open";
    public static final String AUTOCRAFT_REMOTE = "chestsplusplus.autocraft.remote";
    public static final String AUTOCRAFT_MENU = "chestsplusplus.autocraft.menu";
    public static final String AUTOCRAFT_REMOVE = "chestsplusplus.autocraft.remove";
    public static final String AUTOCRAFT_MEMBERS = "chestsplusplus.autocraft.members";

    public static final String FILTER = "chestsplusplus.filter";
    public static final String TRUST = "chestsplusplus.trust";

    public static final String ADMIN_BYPASS = "chestsplusplus.admin.bypass";
    public static final String ADMIN_RELOAD = "chestsplusplus.admin.reload";
    public static final String ADMIN_UPDATE = "chestsplusplus.admin.update";
    public static final String ADMIN_VERSION = "chestsplusplus.admin.version";

    /** Prefix of the numeric limit nodes, e.g. {@code chestsplusplus.limit.chestlink.10}. */
    public static String limitPrefix(GroupType type) {
        return "chestsplusplus.limit." + type.name().toLowerCase(Locale.ROOT) + ".";
    }

    public static String create(GroupType type) {
        return type.pick(CHESTLINK_CREATE, AUTOCRAFT_CREATE);
    }

    public static String open(GroupType type) {
        return type.pick(CHESTLINK_OPEN, AUTOCRAFT_OPEN);
    }

    public static String remote(GroupType type) {
        return type.pick(CHESTLINK_REMOTE, AUTOCRAFT_REMOTE);
    }

    public static String menu(GroupType type) {
        return type.pick(CHESTLINK_MENU, AUTOCRAFT_MENU);
    }

    public static String remove(GroupType type) {
        return type.pick(CHESTLINK_REMOVE, AUTOCRAFT_REMOVE);
    }

    public static String members(GroupType type) {
        return type.pick(CHESTLINK_MEMBERS, AUTOCRAFT_MEMBERS);
    }

    private Permissions() {}
}
