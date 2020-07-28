package com.jamesdpeters.minecraft.chests.lang;

public enum Tag {
    STORAGE_IDENTIFIER,
    SORT_METHOD,
    PLAYER_NAME,
    STORAGE_TYPE,
    STORAGE_GROUP,
    PLAYER_LIST,
    INVALID;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
