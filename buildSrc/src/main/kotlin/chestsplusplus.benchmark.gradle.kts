// v2 vs v3 performance (docs/benchmarking.md). benchmarkWorlds lays one world out with v2 and upgrades a copy to v3; benchmark runs
// each with and without its plugin and reports the plugin's cost on its own Minecraft version. The benchProbe source set is the plugin
// that measures; it is built against Paper 1.21.7 so the same jar runs on both servers.

plugins {
    java
    id("com.gradleup.shadow")
    id("chestsplusplus.server-downloads")
    id("chestsplusplus.v2-upgrade")
}

val benchmarkDir = layout.projectDirectory.dir("run/benchmark")
val benchmarkFixture = layout.projectDirectory.dir("src/test/benchmark")
val benchmarkRconPort = 25577

val benchProbe: SourceSet = sourceSets.create("benchProbe")

dependencies {
    "benchProbeCompileOnly"(libs.paper.api.v2)
    "benchProbeCompileOnly"(libs.jspecify)
}

val benchProbeJar = tasks.register<Jar>("benchProbeJar") {
    group = "benchmark"
    description = "Builds the plugin that measures benchmark runs (benchmark servers only, never shipped)."
    archiveBaseName = "ChestsPlusPlus-BenchProbe"
    from(benchProbe.output)
}

fun intProperty(name: String, default: Int) = providers.gradleProperty("chestsplusplus.benchmark.$name").map { it.toInt() }.orElse(default)

tasks.register<BuildBenchmarkWorlds>("benchmarkWorlds") {
    group = "benchmark"
    description = "Builds the v2 and v3 benchmark worlds in run/benchmark/worlds. Sizes: -Pchestsplusplus.benchmark.chestlinks/autocrafters/filters."
    javaLauncher = serverLauncher
    v2ServerJar = downloaded("downloadV2Paper")
    v3ServerJar = downloaded("downloadE2ePaper")
    v2Plugins.from(v2Jar(), tasks.named<Jar>("v2FixtureJar").flatMap { it.archiveFile })
    v3Plugin = releaseJar
    fixtureDirectory = benchmarkFixture
    chestLinks = intProperty("chestlinks", 100)
    autoCrafters = intProperty("autocrafters", 25)
    filters = intProperty("filters", 25)
    acceptEula = eulaAccepted
    rconPort = benchmarkRconPort
    workDirectory = benchmarkDir.dir("build")
    worldsDirectory = benchmarkDir.dir("worlds")
}

tasks.register<RunBenchmark>("benchmark") {
    group = "benchmark"
    description = "Measures v2 and v3, each with and without the plugin, and writes build/reports/benchmark/report.md. Run benchmarkWorlds first."
    javaLauncher = serverLauncher
    v2ServerJar = downloaded("downloadV2Paper")
    v3ServerJar = downloaded("downloadE2ePaper")
    v2Plugin.from(v2Jar())
    v3Plugin = releaseJar
    probePlugin = benchProbeJar.flatMap { it.archiveFile }
    fixtureDirectory = benchmarkFixture
    warmupSeconds = intProperty("warmup", 120)
    measureSeconds = intProperty("duration", 300)
    rounds = intProperty("rounds", 1)
    only = providers.gradleProperty("chestsplusplus.benchmark.only").map { it.split(',').map(String::trim) }.orElse(emptyList())
    // A fixed heap, touched up front, so the runs differ only in what the plugin allocates.
    jvmArgs = listOf("-Xms4G", "-Xmx4G", "-XX:+UseG1GC", "-XX:+AlwaysPreTouch")
    flightRecording = providers.gradleProperty("chestsplusplus.benchmark.jfr").map { it.toBoolean() }.orElse(false)
    acceptEula = eulaAccepted
    rconPort = benchmarkRconPort
    worldsDirectory = benchmarkDir.dir("worlds")
    runDirectory = benchmarkDir.dir("runs")
    reportDirectory = layout.buildDirectory.dir("reports/benchmark")
}
