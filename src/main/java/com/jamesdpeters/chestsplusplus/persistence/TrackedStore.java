package com.jamesdpeters.chestsplusplus.persistence;

/** A store that exposes dirty tracking to its model's callers. */
public interface TrackedStore<K, S> extends Store<K, S> {

    Persistence persistence();

    default void markDirty(K key) {
        persistence().markDirty(this, key);
    }

    default boolean isDirty(K key) {
        return persistence().isDirty(this, key);
    }
}
