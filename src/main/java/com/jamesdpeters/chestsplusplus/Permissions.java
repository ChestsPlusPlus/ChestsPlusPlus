package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import java.util.Locale;

/** Permission nodes. Defaults are declared in the build script, which generates paper-plugin.yml. */
public final class Permissions {

    /** Not a {@link GroupPermission}: AutoCraft has nothing to sort. */
    public static final String CHESTLINK_SORT = "chestsplusplus.chestlink.sort";

    public static final String FILTER = "chestsplusplus.filter";
    public static final String TRUST = "chestsplusplus.trust";

    public static final String ADMIN_BYPASS = "chestsplusplus.admin.bypass";
    public static final String ADMIN_RELOAD = "chestsplusplus.admin.reload";
    public static final String ADMIN_UPDATE = "chestsplusplus.admin.update";
    public static final String ADMIN_VERSION = "chestsplusplus.admin.version";
    public static final String ADMIN_MIGRATE = "chestsplusplus.admin.migrate";

    /** The node for {@code action} on {@code type}, e.g. {@code chestsplusplus.chestlink.open}. */
    public static String of(GroupType type, GroupPermission action) {
        return type.pick(action.chestlink, action.autocraft);
    }

    /** Prefix of the numeric limit nodes, e.g. {@code chestsplusplus.limit.chestlink.10}. */
    public static String limitPrefix(GroupType type) {
        return "chestsplusplus.limit." + key(type) + ".";
    }

    static String node(GroupType type, GroupPermission action) {
        return "chestsplusplus." + key(type) + "." + action.name().toLowerCase(Locale.ROOT);
    }

    private static String key(GroupType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private Permissions() {}
}
