package com.jamesdpeters.chestsplusplus.benchprobe;

import java.util.Map;

/** What a window measured; written as {@code result.json} and read by the {@code benchmark} task. Times are in milliseconds. */
record Result(int ticks, double seconds, double tps, double msptMean, double msptP50, double msptP95, double msptP99, double msptMax,
        double mainThreadCpuMillis, double processCpuMillis, long gcCount, long gcMillis, long heapUsedMb, Map<String, Integer> itemsMoved) {}
