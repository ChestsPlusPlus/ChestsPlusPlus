package com.jamesdpeters.chestsplusplus.message;

import com.jamesdpeters.chestsplusplus.model.GroupType;

/** A message every group type sends, with its own wording per type. */
public enum GroupMessage {
    CREATED(Message.CHESTLINK_CREATED, Message.AUTOCRAFT_CREATED),
    LINKED(Message.CHESTLINK_LINKED, Message.AUTOCRAFT_LINKED),
    UNLINKED(Message.CHESTLINK_UNLINKED, Message.AUTOCRAFT_UNLINKED),
    REMOVED(Message.CHESTLINK_REMOVED, Message.AUTOCRAFT_REMOVED),
    RENAMED(Message.CHESTLINK_RENAMED, Message.AUTOCRAFT_RENAMED),
    PUBLIC(Message.CHESTLINK_PUBLIC, Message.AUTOCRAFT_PUBLIC),
    LIST_HEADER(Message.CHESTLINK_LIST_HEADER, Message.AUTOCRAFT_LIST_HEADER),
    LIST_ENTRY(Message.CHESTLINK_LIST_ENTRY, Message.AUTOCRAFT_LIST_ENTRY),
    LIST_EMPTY(Message.CHESTLINK_LIST_EMPTY, Message.AUTOCRAFT_LIST_EMPTY);

    private final Message chestlink;
    private final Message autocraft;

    GroupMessage(Message chestlink, Message autocraft) {
        this.chestlink = chestlink;
        this.autocraft = autocraft;
    }

    public Message of(GroupType type) {
        return type.pick(chestlink, autocraft);
    }
}
