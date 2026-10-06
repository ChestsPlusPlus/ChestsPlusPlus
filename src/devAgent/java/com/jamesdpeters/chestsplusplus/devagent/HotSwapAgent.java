package com.jamesdpeters.chestsplusplus.devagent;

import static java.util.stream.Collectors.groupingBy;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Dev-server agent ({@code ./gradlew runServer} only): whenever the compiled classes change, redefines them in the running
 * server. On the JetBrains Runtime with {@code -XX:+AllowEnhancedClassRedefinition} that includes added or removed methods
 * and fields, not just method bodies.
 */
public final class HotSwapAgent {
    private HotSwapAgent() {}

    public static void premain(String classesDir, Instrumentation instrumentation) {
        var watcher = new ClassWatcher(Path.of(classesDir));
        Thread.ofPlatform().daemon().name("ChestsPlusPlus-HotSwap").start(() -> watcher.run(classes -> swap(instrumentation, classes)));
    }

    private static void swap(Instrumentation instrumentation, Map<String, byte[]> classes) {
        Map<String, List<Class<?>>> loaded = loadedClasses(instrumentation)
                .filter(type -> classes.containsKey(type.getName()))
                .collect(groupingBy(Class::getName));
        List<String> unloaded = classes.keySet().stream().filter(name -> !loaded.containsKey(name)).toList();
        try {
            ClassDefinition[] definitions = loaded.values()
                    .stream()
                    .flatMap(List::stream)
                    .map(type -> new ClassDefinition(type, NewStatics.prepare(type, classes.get(type.getName()))))
                    .toArray(ClassDefinition[]::new);
            int defined = defineAll(unloaded, classes, instrumentation);
            instrumentation.redefineClasses(definitions);
            Arrays.stream(definitions).map(ClassDefinition::getDefinitionClass).forEach(NewStatics::initialize);
            log("Reloaded %d classes (%d new)", definitions.length, defined);
        } catch (Exception | LinkageError e) {
            log("Reload failed, restart the server: %s", e);
        }
    }

    /**
     * Defines classes the server hasn't loaded yet, so it can't later load the stale copy from the plugin jar. Retries in passes
     * because a new class can't be defined before a new superclass of it.
     */
    private static int defineAll(List<String> names, Map<String, byte[]> classes, Instrumentation instrumentation) {
        List<String> pending = new ArrayList<>(names);
        int before;
        do {
            before = pending.size();
            pending.removeIf(name -> define(name, classes.get(name), instrumentation));
        } while (!pending.isEmpty() && pending.size() < before);
        if (!pending.isEmpty()) log("Couldn't define %s; restart the server if you need them", pending);
        return names.size() - pending.size();
    }

    private static boolean define(String name, byte[] bytes, Instrumentation instrumentation) {
        String pkg = name.substring(0, name.lastIndexOf('.'));
        return loadedClasses(instrumentation)
                .filter(type -> type.getClassLoader() != null && type.getPackageName().equals(pkg) && !type.isHidden())
                .findFirst()
                .map(anchor -> defineNextTo(anchor, bytes))
                .orElse(false);
    }

    private static boolean defineNextTo(Class<?> anchor, byte[] bytes) {
        try {
            MethodHandles.privateLookupIn(anchor, MethodHandles.lookup()).defineClass(bytes);
            return true;
        } catch (IllegalAccessException | LinkageError e) {
            return false;
        }
    }

    private static Stream<Class<?>> loadedClasses(Instrumentation instrumentation) {
        return Arrays.<Class<?>>stream(instrumentation.getAllLoadedClasses());
    }

    static void log(String format, Object... args) {
        // System.out rather than a logger: touching java.util.logging from an agent can pin the log manager before Paper sets it.
        System.out.println(format.formatted(args));
    }
}
