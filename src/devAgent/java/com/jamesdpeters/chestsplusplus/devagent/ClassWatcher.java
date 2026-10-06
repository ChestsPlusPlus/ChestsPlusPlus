package com.jamesdpeters.chestsplusplus.devagent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Polls a class output directory and reports each settled batch of changed classes, keyed by binary name. */
final class ClassWatcher {
    private static final long POLL_MILLIS = 500;

    private final Path root;
    private Map<Path, FileTime> known;

    ClassWatcher(Path root) {
        this.root = root;
        this.known = scan();
    }

    void run(Consumer<Map<String, byte[]>> onChange) {
        while (true) {
            Map<Path, FileTime> current = settle(poll());
            Map<String, byte[]> changed = current.entrySet()
                    .stream()
                    .filter(entry -> !entry.getValue().equals(known.get(entry.getKey())))
                    .collect(Collectors.toMap(entry -> className(entry.getKey()), entry -> read(entry.getKey())));
            known = current;
            if (!changed.isEmpty()) onChange.accept(changed);
        }
    }

    /** javac writes one file at a time, so wait until two polls in a row agree. */
    private Map<Path, FileTime> settle(Map<Path, FileTime> current) {
        Map<Path, FileTime> next;
        while (!(next = poll()).equals(current)) current = next;
        return current;
    }

    private Map<Path, FileTime> poll() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return scan();
    }

    private Map<Path, FileTime> scan() {
        if (!Files.isDirectory(root)) return Map.of();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(file -> file.toString().endsWith(".class")).collect(Collectors.toMap(file -> file, ClassWatcher::modified));
        } catch (IOException | UncheckedIOException e) {
            // Gradle is mid-way through deleting or rewriting the directory; the next poll will differ and keep settling.
            return Map.of();
        }
    }

    private String className(Path file) {
        String relative = root.relativize(file).toString();
        return relative.substring(0, relative.length() - ".class".length()).replace(file.getFileSystem().getSeparator(), ".");
    }

    private static FileTime modified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
