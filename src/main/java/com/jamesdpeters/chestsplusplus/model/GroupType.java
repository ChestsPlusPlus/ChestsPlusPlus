package com.jamesdpeters.chestsplusplus.model;

public enum GroupType {
    CHESTLINK("ChestLink"),
    AUTOCRAFT("AutoCraft");

    private final String displayName;

    GroupType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Returns the argument matching this type. */
    public <T> T pick(T chestlink, T autocraft) {
        return this == CHESTLINK ? chestlink : autocraft;
    }
}
