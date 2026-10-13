package com.jamesdpeters.chestsplusplus.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Add-only listeners, told synchronously in the order they were added. Main thread only, like the model they watch. */
public final class Listeners<E> {

    private final List<Consumer<? super E>> listeners = new ArrayList<>();

    public void add(Consumer<? super E> listener) {
        listeners.add(listener);
    }

    /** Indexed rather than iterated, so firing allocates nothing. */
    public void fire(E event) {
        for (int i = 0; i < listeners.size(); i++) listeners.get(i).accept(event);
    }
}
