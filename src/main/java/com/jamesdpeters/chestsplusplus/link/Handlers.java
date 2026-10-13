package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.EnumMap;
import java.util.Map;

/** The {@link GroupTypeHandler} of each group type. Startup registers one per type and then calls {@link #requireAll()}, so lookups never miss. */
public final class Handlers {

    private final Map<GroupType, GroupTypeHandler> handlers = new EnumMap<>(GroupType.class);

    public void register(GroupTypeHandler handler) {
        handlers.put(handler.type(), handler);
    }

    /** Throws if any group type has no handler. */
    public void requireAll() {
        for (GroupType type : GroupType.values()) of(type);
    }

    public GroupTypeHandler of(GroupType type) {
        GroupTypeHandler handler = handlers.get(type);
        if (handler == null) throw new IllegalStateException(type + " has no handler");
        return handler;
    }

    public GroupTypeHandler of(StorageGroup group) {
        return of(group.type());
    }
}
