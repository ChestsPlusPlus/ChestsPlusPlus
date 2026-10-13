package com.jamesdpeters.chestsplusplus.model;

import java.util.function.Consumer;

/** Told when a registered group changes or is removed, synchronously on the main thread. */
@FunctionalInterface
public interface GroupListener {

    void changed(StorageGroup group);

    /** The group has already left the registry. */
    default void removed(StorageGroup group) {
        changed(group);
    }

    static GroupListener removals(Consumer<StorageGroup> removed) {
        return new GroupListener() {
            @Override
            public void changed(StorageGroup group) {}

            @Override
            public void removed(StorageGroup group) {
                removed.accept(group);
            }
        };
    }
}
