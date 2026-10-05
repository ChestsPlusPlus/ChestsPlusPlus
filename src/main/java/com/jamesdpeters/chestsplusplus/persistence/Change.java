package com.jamesdpeters.chestsplusplus.persistence;

/** Which part of a group changed, so a save only rewrites the rows for that part. */
public enum Change {
    /** Name, public flag or sort mode: the {@code groups} row. */
    META,
    MEMBERS,
    NODES,
    /** A ChestLink's inventory or an AutoCraft recipe. */
    CONTENTS
}
