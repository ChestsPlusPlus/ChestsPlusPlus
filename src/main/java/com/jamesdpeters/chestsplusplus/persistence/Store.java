package com.jamesdpeters.chestsplusplus.persistence;

import java.util.List;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.Nullable;

/** One kind of persisted thing: how to copy it on the main thread, and how to write, delete and load its rows. */
public interface Store<K, S> {

    /** Main thread, cheap: a copy safe to hand to the I/O thread, or null when the thing no longer exists (it is then deleted). */
    @Nullable
    S snapshot(K key);

    /** I/O thread, inside the flush transaction. */
    void write(Handle handle, List<S> snapshots);

    /** I/O thread, inside the flush transaction, before {@link #write}. */
    void delete(Handle handle, List<K> keys);

    /** Main thread, at startup. */
    void load(Handle handle);
}
