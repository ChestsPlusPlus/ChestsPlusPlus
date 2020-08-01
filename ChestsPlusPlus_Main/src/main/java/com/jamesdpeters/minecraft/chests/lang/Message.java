package com.jamesdpeters.minecraft.chests.lang;

import java.text.MessageFormat;

public enum Message {
    //Messages.java
    CHEST_HAD_OVERFLOW("Chest item's wouldn't all fit into ChestLink!"),
    MUST_HOLD_SIGN("You must be holding a sign to do that!"),
    NO_PERMISSION("You don't have permission to do that!"),
    SORT("Sort method for {0} has been set to {1}", Tag.STORAGE_IDENTIFIER, Tag.SORT_METHOD),
    CANNOT_RENAME_GROUP_ALREADY_EXISTS("Error renaming group! {0} already exists!", Tag.STORAGE_IDENTIFIER),
    CANNOT_RENAME_GROUP_DOESNT_EXIST("Error renaming group! {0} doesn't exist!", Tag.STORAGE_IDENTIFIER),
    OWNER_HAS_TOO_MANY_CHESTS("Owner: {0} has reached the limit of groups allowed!", Tag.PLAYER_NAME),
    ALREADY_PART_OF_GROUP("This {0} is already a part of a group!", Tag.STORAGE_TYPE),

    //StorageMessages.java
    STORAGE_ADDED("Successfully added {0} to group: {1} for {2}", Tag.STORAGE_TYPE, Tag.STORAGE_GROUP, Tag.PLAYER_NAME),
    STORAGE_REMOVED("Successfully removed {0} from group: {1} for {2}", Tag.STORAGE_TYPE, Tag.STORAGE_GROUP, Tag.PLAYER_NAME),
    REMOVED_GROUP("Successfully removed group {0} from your {1}'s!", Tag.STORAGE_GROUP, Tag.STORAGE_TYPE),
    GROUP_DOESNT_EXIST("{0} isn't a valid {1} group to remove!", Tag.STORAGE_GROUP, Tag.STORAGE_TYPE),
    FOUND_UNLINKED_STORAGE("This {0} wasn't linked to your system! It has been added under the {1} group!", Tag.STORAGE_TYPE, Tag.STORAGE_IDENTIFIER),
    ADDED_MEMBER("Successfully added {0} to {1} group {2}", Tag.PLAYER_NAME, Tag.STORAGE_TYPE, Tag.STORAGE_IDENTIFIER),
    REMOVED_MEMBER("Successfully removed {0} from {1} group {2}", Tag.PLAYER_NAME, Tag.STORAGE_TYPE, Tag.STORAGE_IDENTIFIER),
    CURRENT_MEMBERS("Current Members: {0}", Tag.PLAYER_LIST),
    ADDED_MEMBER_TO_ALL("Successfully added {0} to all {1} groups", Tag.PLAYER_NAME, Tag.STORAGE_TYPE),
    REMOVE_MEMBER_FROM_ALL("Successfully removed {0} from all {1} groups", Tag.PLAYER_NAME, Tag.STORAGE_TYPE),
    UNABLE_TO_ADD_MEMBER_TO_ALL("Unable to add player {0} to {1}!", Tag.PLAYER_NAME, Tag.STORAGE_TYPE),
    UNABLE_TO_REMOVE_MEMBER("Unable to remove player {0} from {1}! Were they already removed?", Tag.PLAYER_NAME, Tag.STORAGE_TYPE),
    LIST_MEMBERS_OF_GROUP("Members of {0} group {1}: {2}", Tag.STORAGE_TYPE, Tag.STORAGE_IDENTIFIER, Tag.PLAYER_LIST),
    NO_ADDITIONAL_MEMBERS("There are no additional members in the group: {0}", Tag.STORAGE_IDENTIFIER),
    SET_PUBLICITY("There are no additional members in the group: {0}", Tag.STORAGE_IDENTIFIER),
    INVALID_ID("Invalid {0} ID! Must not contain a colon ':' unless you are referencing another players group that you are a member of", Tag.STORAGE_TYPE),

    //ChestLinkMessages
    LIST_OF_CHESTLINK("List of your ChestLinks:"),
    MUST_LOOK_AT_CHEST("You must be looking at the chest you want to ChestLink!"),
    INVALID_CHESTLINK("Invalid ChestLink - You must place a sign on the front of a chest / you should ensure there is space for a sign on front of the chest!"),

    //AutoCraftMessages
    LIST_OF_AUTOCRAFTERS("List of your AutoCraft Stations:"),
    MUST_LOOK_AT_CRAFTING_TABLE("You must be looking at the Crafting Table you want to AutoCraft with!"),
    INVALID_AUTOCRAFTER("Invalid AutoCrafter - You must place a sign on any side of a Crafting Table, and it must not already by apart of a group!"),

    //Commands
    COMMAND_MEMBER("Add, remove or list members of a group"),
    COMMAND_HELP("List of commands and their uses!"),

    //AutoCraft
    COMMAND_AUTOCRAFT_ADD("Create/add a Crafting Table to an AutoCraft group"),
    COMMAND_AUTOCRAFT_LIST("Lists all AutoCraft groups that you own!"),
    COMMAND_AUTOCRAFT_OPEN("Open the workbench of an AutoCraft group"),
    COMMAND_AUTOCRAFT_REMOVE("Delete an AutoCraft group and drop all the Crafting Tables!"),
    COMMAND_AUTOCRAFT_RENAME("Rename an AutoCraft group."),
    COMMAND_AUTOCRAFT_SETPUBLIC("Set an AutoCraft group to be accessible by anyone."),

    //ChestLink
    COMMAND_CHESTLINK_MENU("Open the ChestLink menu to display all groups!"),
    COMMAND_CHESTLINK_SORT("Set the sorting option for the given ChestLink."),
    COMMAND_CHESTLINK_ADD("Create/add a chest to a ChestLink group"),
    COMMAND_CHESTLINK_LIST("Lists all ChestLinks that you own!"),
    COMMAND_CHESTLINK_OPEN("Open the inventory of a ChestLink group"),
    COMMAND_CHESTLINK_REMOVE("Delete a ChestLink and drop its inventory at your feet!"),
    COMMAND_CHESTLINK_RENAME("Rename a ChestLink."),
    COMMAND_CHESTLINK_SETPUBLIC("Set a ChestLink to be accessible by anyone.");

    String message;

    Message(String defaultMessage) {
        this(defaultMessage, new Tag[]{});
    }

    Tag[] tags;

    Message(String defaultMessage, Tag... tags) {
        message = defaultMessage;
        this.tags = tags;
    }

    /**
     * This can be used to set different messages for different languages.
     *
     * @param message - the template string for the message.
     */
    public void setMessage(String message) {
        this.message = detagMessage(message, tags);
    }

    public String getTaggedMessage() {
        return tagMessage(message, tags);
    }

    private static String detagMessage(String string, Tag[] tags) {
        for (int i = 0; i < tags.length; i++) {
            string = string.replaceAll("\\{" + tags[i] + "}", "{" + i + "}");
        }
        return string;
    }

    private static String tagMessage(String string, Tag[] tags) {
        for (int i = 0; i < tags.length; i++) {
            String replace = "\\{" + i + "}";
            string = string.replaceAll(replace, "{" + tags[i] + "}");
        }
        return string;
    }

    public String getString(Object... args) {
        return MessageFormat.format(message, args);
    }

}
