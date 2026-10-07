package com.jamesdpeters.chestsplusplus.benchprobe;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** One measurement window. */
final class Window {

    /** An hour of ticks; a longer window keeps only the first hour's tick times. */
    private static final int MAX_TICKS = 20 * 60 * 60;

    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final OperatingSystemMXBean OS = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private final double[] tickMillis = new double[MAX_TICKS];
    private int ticks;
    private final long startNanos = System.nanoTime();
    private final long serverThread;
    private final long startThreadCpu;
    private final long startProcessCpu = OS.getProcessCpuTime();
    private final long startGcCount = gcCount();
    private final long startGcMillis = gcMillis();
    private final Map<String, Integer> startItems;

    Window(long serverThread, Map<String, Integer> startItems) {
        this.serverThread = serverThread;
        this.startThreadCpu = THREADS.getThreadCpuTime(serverThread);
        this.startItems = startItems;
    }

    /** Runs every tick, so it only stores the number. */
    void tick(double millis) {
        if (ticks < MAX_TICKS) tickMillis[ticks] = millis;
        ticks++;
    }

    Result close(Map<String, Integer> endItems) {
        double seconds = (System.nanoTime() - startNanos) / 1e9;
        int kept = Math.min(ticks, MAX_TICKS);
        double[] sorted = Arrays.copyOf(tickMillis, kept);
        Arrays.sort(sorted);
        Runtime runtime = Runtime.getRuntime();
        return new Result(ticks, seconds, ticks / seconds, Arrays.stream(sorted).average().orElse(0), percentile(sorted, 50),
                percentile(sorted, 95), percentile(sorted, 99), kept == 0 ? 0 : sorted[kept - 1],
                (THREADS.getThreadCpuTime(serverThread) - startThreadCpu) / 1e6, (OS.getProcessCpuTime() - startProcessCpu) / 1e6,
                gcCount() - startGcCount, gcMillis() - startGcMillis, (runtime.totalMemory() - runtime.freeMemory()) >> 20,
                difference(startItems, endItems));
    }

    private static double percentile(double[] sorted, int percent) {
        if (sorted.length == 0) return 0;
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(percent / 100.0 * sorted.length) - 1)];
    }

    private static Map<String, Integer> difference(Map<String, Integer> before, Map<String, Integer> after) {
        Set<String> materials = new HashSet<>(before.keySet());
        materials.addAll(after.keySet());
        Map<String, Integer> change = new TreeMap<>();
        materials.forEach(material -> change.put(material, after.getOrDefault(material, 0) - before.getOrDefault(material, 0)));
        return change;
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }
}
