package com.jamesdpeters.chestsplusplus.model;

import lombok.Getter;

public enum GroupType {
    CHESTLINK("ChestLink"),
    AUTOCRAFT("AutoCraft");

    @Getter private final String displayName;

    GroupType(String displayName) {
        this.displayName = displayName;
    }

    /** Returns the argument matching this type. */
    public <T> T pick(T chestlink, T autocraft) {
        return this == CHESTLINK ? chestlink : autocraft;
    }
}
