import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.work.DisableCachingByDefault

/** Files and checks shared by the benchmark tasks (docs/benchmarking.md). */
object Benchmark {
    /** Written into each world: the block area to force-load, as `minX minZ maxX maxZ`. */
    const val AREA_FILE = "benchmark-area.txt"
    const val V2_WORLD = "v2"
    const val V3_WORLD = "v3"

    /** How ChestsPlusPlus's log lines and stack frames, v2's or v3's, can be told apart from the server's. */
    val PLUGIN_MARKERS = listOf("ChestsPlusPlus", "com.jamesdpeters")

    /** A fresh folder (a copy of [world], if given) with the fixture's server.properties and RCON behind a new password, which it returns. */
    fun prepare(runDir: File, fixture: File, rconPort: Int, world: File? = null): String {
        runDir.mkdirs()
        Servers.clear(runDir)
        world?.copyRecursively(runDir, overwrite = true)
        val password = Servers.randomPassword()
        Servers.configure(runDir, File(fixture, "server.properties"), mapOf("rcon.port" to rconPort.toString(), "rcon.password" to password))
        // bStats reports and spark (built into Paper) profiles in the background; both would add noise.
        File(runDir, "plugins/bStats").mkdirs()
        File(runDir, "plugins/bStats/config.yml").writeText("enabled: false\n")
        File(runDir, "plugins/spark").mkdirs()
        File(runDir, "plugins/spark/config.json").writeText("{\"backgroundProfiler\": false}\n")
        return password
    }

    fun installPlugins(runDir: File, plugins: Iterable<File>) {
        val pluginsDir = File(runDir, "plugins").apply { mkdirs() }
        pluginsDir.listFiles { file -> file.isFile && file.name.endsWith(".jar") }?.forEach { it.delete() }
        plugins.forEach { it.copyTo(File(pluginsDir, it.name), overwrite = true) }
    }

    /** Keeps [runDir] as a world for the benchmark to copy, without any plugin jars. */
    fun snapshot(runDir: File, worldDir: File) {
        installPlugins(runDir, emptyList())
        Servers.snapshot(runDir, worldDir)
    }

    /** Asks v3 for its migration status until it contains [wanted]. RCON replies keep colour codes, so they are stripped first. */
    fun awaitStatus(rcon: Rcon, wanted: String, logger: Logger) {
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5)
        while (System.nanoTime() < deadline) {
            val status = rcon.command("cpp migrate v2 status").replace(Regex("§."), "")
            if (status.contains(wanted)) return
            logger.info(status)
            Thread.sleep(2000)
        }
        throw GradleException("v3 never reported '$wanted' from /cpp migrate v2 status")
    }
}

/**
 * Builds the two benchmark worlds from one layout: v2 lays the cells out on Paper 1.21.7 through its own classes, then v3 upgrades a copy
 * on Paper 26.3, imports the groups and converts the v2 filters. Both are kept without plugin jars in worldsDirectory.
 */
@DisableCachingByDefault(because = "Runs Minecraft servers")
abstract class BuildBenchmarkWorlds : DefaultTask() {
    @get:Nested abstract val javaLauncher: Property<JavaLauncher>
    @get:InputFile abstract val v2ServerJar: RegularFileProperty
    @get:InputFile abstract val v3ServerJar: RegularFileProperty

    /** v2 and the fixture plugin that lays the cells out. */
    @get:InputFiles abstract val v2Plugins: ConfigurableFileCollection
    @get:InputFile abstract val v3Plugin: RegularFileProperty

    /** server.properties and the v2 config.yml, from src/test/benchmark. */
    @get:InputDirectory abstract val fixtureDirectory: DirectoryProperty
    @get:Input abstract val chestLinks: Property<Int>
    @get:Input abstract val autoCrafters: Property<Int>
    @get:Input abstract val filters: Property<Int>
    @get:Input abstract val acceptEula: Property<Boolean>
    @get:Input abstract val rconPort: Property<Int>
    @get:Internal abstract val workDirectory: DirectoryProperty
    @get:Internal abstract val worldsDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun build() {
        Servers.requireEula(acceptEula.get(), "Building the benchmark worlds")
        val work = workDirectory.get().asFile
        val password = Benchmark.prepare(work, fixtureDirectory.get().asFile, rconPort.get())
        val area = buildV2World(work, password)
        upgradeToV3(work, password)
        logger.lifecycle("Built the benchmark worlds in ${worldsDirectory.get().asFile} (cells in the area $area)")
    }

    /** Lays the cells out with v2 and keeps the world. Returns the area they cover. */
    private fun buildV2World(work: File, password: String): String {
        Benchmark.installPlugins(work, v2Plugins.files)
        File(fixtureDirectory.get().asFile, "config.yml").copyTo(File(work, "plugins/ChestsPlusPlus/config.yml"), overwrite = true)
        val v2 = server(v2ServerJar, work, password, "v2 server")
        val area = v2.use(File(work, "build-v2.log")) { rcon ->
            val reply = rcon.command("v2fixture benchmark ${chestLinks.get()} ${autoCrafters.get()} ${filters.get()}")
            if (!reply.contains("built in ")) throw GradleException("v2fixture benchmark failed: $reply")
            rcon.command("save-all flush")
            reply.substringAfter("built in ").trim()
        }
        // The second start checks v2 reads its file back, and leaves v2's armour stands in the world as a real v2 server would.
        val reload = File(work, "reload-v2.log")
        v2.use(reload) { rcon -> rcon.command("save-all flush") }
        V2Upgrade.checkReadItsData(reload)
        File(work, Benchmark.AREA_FILE).writeText(area)
        Benchmark.snapshot(work, worldsDirectory.dir(Benchmark.V2_WORLD).get().asFile)
        return area
    }

    /** Starts v3 on the v2 world, which imports the groups, then converts the filters and keeps the result. */
    private fun upgradeToV3(work: File, password: String) {
        Benchmark.installPlugins(work, listOf(v3Plugin.get().asFile))
        server(v3ServerJar, work, password, "v3 server").use(File(work, "upgrade-v3.log")) { rcon ->
            Benchmark.awaitStatus(rcon, "v2 import: COMPLETE", logger)
            rcon.command("cpp migrate v2 filters convert-all")
            Benchmark.awaitStatus(rcon, "v2 hopper filters: done", logger)
            rcon.command("save-all flush")
        }
        Benchmark.snapshot(work, worldsDirectory.dir(Benchmark.V3_WORLD).get().asFile)
    }

    private fun server(jar: RegularFileProperty, runDir: File, password: String, name: String) =
        ForegroundServer(javaLauncher.get().executablePath.asFile, listOf("-Xmx2G"), jar.get().asFile, runDir, rconPort.get(), password, name, logger)
}

/**
 * Runs each setup on a fresh copy of its world: v2 and v3, each with and without the plugin, so each plugin's cost is measured against its
 * own Minecraft version. Writes report.md and results.json to reportDirectory.
 */
@DisableCachingByDefault(because = "Measures Minecraft servers")
abstract class RunBenchmark : DefaultTask() {
    @get:Nested abstract val javaLauncher: Property<JavaLauncher>
    @get:InputFile abstract val v2ServerJar: RegularFileProperty
    @get:InputFile abstract val v3ServerJar: RegularFileProperty
    @get:InputFiles abstract val v2Plugin: ConfigurableFileCollection
    @get:InputFile abstract val v3Plugin: RegularFileProperty
    @get:InputFile abstract val probePlugin: RegularFileProperty
    @get:InputDirectory abstract val fixtureDirectory: DirectoryProperty
    @get:Input abstract val warmupSeconds: Property<Int>
    @get:Input abstract val measureSeconds: Property<Int>
    @get:Input abstract val rounds: Property<Int>

    /** Setups to run, by name; all of them when empty. */
    @get:Input abstract val only: ListProperty<String>
    @get:Input abstract val jvmArgs: ListProperty<String>

    /** Also records a JFR profile of each measured window, for JDK Mission Control. */
    @get:Input abstract val flightRecording: Property<Boolean>
    @get:Input abstract val acceptEula: Property<Boolean>
    @get:Input abstract val rconPort: Property<Int>
    @get:Internal abstract val worldsDirectory: DirectoryProperty
    @get:Internal abstract val runDirectory: DirectoryProperty
    @get:OutputDirectory abstract val reportDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    private class Setup(val name: String, val world: String, val serverJar: File, val plugin: File?)

    @TaskAction
    fun run() {
        Servers.requireEula(acceptEula.get(), "The benchmark")
        val missing = listOf(Benchmark.V2_WORLD, Benchmark.V3_WORLD).map { worldsDirectory.dir(it).get().asFile }.firstOrNull { !it.isDirectory }
        if (missing != null) throw GradleException("No benchmark world in $missing; run ./gradlew benchmarkWorlds first")
        val setups = listOf(
            Setup("v2-baseline", Benchmark.V2_WORLD, v2ServerJar.get().asFile, null),
            Setup("v2", Benchmark.V2_WORLD, v2ServerJar.get().asFile, v2Plugin.singleFile),
            Setup("v3-baseline", Benchmark.V3_WORLD, v3ServerJar.get().asFile, null),
            Setup("v3", Benchmark.V3_WORLD, v3ServerJar.get().asFile, v3Plugin.get().asFile),
        ).filter { only.get().isEmpty() || it.name in only.get() }
        val reports = reportDirectory.get().asFile.apply { deleteRecursively(); mkdirs() }

        // Rounds interleave the setups, so a machine that slows down part-way hurts them all alike.
        val results = setups.associate { it.name to mutableListOf<Map<String, Any>>() }
        for (round in 1..rounds.get()) setups.forEach { results.getValue(it.name).add(measure(it, round, reports)) }

        val report = BenchmarkReport(results, describeRun()).markdown()
        File(reports, "report.md").writeText(report)
        File(reports, "results.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(results)))
        logger.lifecycle("\n$report\nWritten to ${File(reports, "report.md")}")
    }

    private fun measure(setup: Setup, round: Int, reports: File): Map<String, Any> {
        val runDir = runDirectory.dir(setup.name).get().asFile
        val password = Benchmark.prepare(runDir, fixtureDirectory.get().asFile, rconPort.get(), worldsDirectory.dir(setup.world).get().asFile)
        Benchmark.installPlugins(runDir, listOfNotNull(probePlugin.get().asFile, setup.plugin))
        val area = File(runDir, Benchmark.AREA_FILE).readText().trim()
        val log = File(runDir, "benchmark.log")
        val server = ForegroundServer(javaLauncher.get().executablePath.asFile, jvmArgs.get() + flightRecorder(setup, round, reports),
            setup.serverJar, runDir, rconPort.get(), password, "${setup.name} server", logger)
        server.use(log) { rcon ->
            rcon.command("forceload add $area")
            logger.lifecycle("${setup.name} (round $round): warming up for ${warmupSeconds.get()}s, then measuring for ${measureSeconds.get()}s")
            Thread.sleep(warmupSeconds.get() * 1000L)
            rcon.command("benchprobe start")
            Thread.sleep(measureSeconds.get() * 1000L)
            val reply = rcon.command("benchprobe stop")
            if (!reply.contains("wrote")) throw GradleException("benchprobe failed: $reply (see $log)")
        }
        @Suppress("UNCHECKED_CAST")
        val result = JsonSlurper().parse(File(runDir, "plugins/BenchProbe/result.json")) as Map<String, Any>
        return result + ("errors" to Servers.errorsFrom(log, Benchmark.PLUGIN_MARKERS).size)
    }

    private fun flightRecorder(setup: Setup, round: Int, reports: File): List<String> {
        if (!flightRecording.get()) return emptyList()
        val file = File(reports, "${setup.name}-round$round.jfr")
        return listOf("-XX:StartFlightRecording=delay=${warmupSeconds.get()}s,duration=${measureSeconds.get()}s,settings=profile,filename=$file")
    }

    private fun describeRun(): String {
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val java = javaLauncher.get().metadata
        val machine = "${System.getProperty("os.name")}, ${Runtime.getRuntime().availableProcessors()} threads"
        return "$time on $machine. Java ${java.languageVersion} (${java.vendor}), JVM flags `${jvmArgs.get().joinToString(" ")}`. " +
            "${rounds.get()} round(s): ${warmupSeconds.get()}s warm-up, then ${measureSeconds.get()}s measured."
    }
}
