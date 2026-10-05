package com.jamesdpeters.chestsplusplus.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Text files bundled in the plugin jar. */
public final class Resources {

    private Resources() {}

    /** The UTF-8 contents of a jar resource; throws if it is missing. */
    public static String text(String path) {
        try (InputStream in = Resources.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing " + path + " in the plugin jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
