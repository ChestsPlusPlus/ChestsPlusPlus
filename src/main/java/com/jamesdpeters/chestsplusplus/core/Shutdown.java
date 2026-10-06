package com.jamesdpeters.chestsplusplus.core;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import java.util.ArrayDeque;
import java.util.Deque;
import lombok.extern.slf4j.Slf4j;

/**
 * What disable has to undo, added as enable starts each part. Runs in reverse, and a step that throws is logged so the steps after it
 * still run: a failed enable only undoes what it got to, and a failed step can't stop the database closing.
 */
@Slf4j(topic = ChestsPlusPlus.NAME)
public final class Shutdown {

    private record Step(String name, Runnable action) {}

    private final Deque<Step> steps = new ArrayDeque<>();

    public void add(String name, Runnable action) {
        steps.push(new Step(name, action));
    }

    public void run() {
        while (!steps.isEmpty()) {
            Step step = steps.pop();
            try {
                step.action().run();
            } catch (RuntimeException e) {
                log.error("Stopping {} failed", step.name(), e);
            }
        }
    }
}
