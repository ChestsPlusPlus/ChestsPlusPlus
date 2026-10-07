import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.work.DisableCachingByDefault

/** Files shared by the tasks that start and stop the E2E server, and by the Plugwright config. */
object E2eServer {
    const val PID_FILE = ".e2e-server.pid"
    const val PASSWORD_FILE = ".rcon-password"
    const val LOG_FILE = "e2e-server.log"

    fun readPassword(runDir: File): String = File(runDir, PASSWORD_FILE).readText().trim()
}

/**
 * Prepares the E2E run directory (fresh world, fixture server.properties, plugins) and starts Paper in the
 * background. Returns once the log shows `Done (`, so the next task can connect.
 */
@DisableCachingByDefault(because = "Starts an external process")
abstract class StartE2eServer : DefaultTask() {
    @get:Nested abstract val javaLauncher: Property<JavaLauncher>
    @get:InputFile abstract val serverJar: RegularFileProperty
    @get:InputFiles abstract val plugins: ConfigurableFileCollection
    @get:InputFile abstract val serverProperties: RegularFileProperty
    @get:Internal abstract val runDirectory: DirectoryProperty
    @get:Input abstract val jvmArgs: ListProperty<String>
    @get:Input abstract val startupTimeoutSeconds: Property<Long>

    /** Must be explicitly true: running a server requires accepting the Minecraft EULA. */
    @get:Input abstract val acceptEula: Property<Boolean>

    /** RCON password; a random one is generated when absent (local runs). */
    @get:Input @get:Optional abstract val rconPassword: Property<String>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun start() {
        Servers.requireEula(acceptEula.get(), "E2E")
        val runDir = runDirectory.get().asFile
        runDir.mkdirs()
        stopLeftoverServer(runDir)

        // Fresh state on every run: worlds and plugins go; the server jar's caches and libraries stay.
        listOf("world", "world_nether", "world_the_end", "plugins").forEach { File(runDir, it).deleteRecursively() }

        val password = rconPassword.orNull?.takeIf { it.isNotBlank() } ?: Servers.randomPassword()
        File(runDir, E2eServer.PASSWORD_FILE).writeText(password)
        Servers.configure(runDir, serverProperties.get().asFile, mapOf("rcon.password" to password))

        val pluginsDir = File(runDir, "plugins").apply { mkdirs() }
        plugins.files.forEach { it.copyTo(File(pluginsDir, it.name), overwrite = true) }

        val log = File(runDir, E2eServer.LOG_FILE)
        log.delete()
        val command = buildList {
            add(javaLauncher.get().executablePath.asFile.absolutePath)
            addAll(jvmArgs.get())
            add("-jar")
            add(serverJar.get().asFile.absolutePath)
            add("--nogui")
        }
        logger.lifecycle("Starting E2E server in $runDir")
        val process = ProcessBuilder(command)
            .directory(runDir)
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()
        process.outputStream.close()
        File(runDir, E2eServer.PID_FILE).writeText(process.pid().toString())

        Servers.waitForStart(process, log, startupTimeoutSeconds.get(), "E2E server")
        logger.lifecycle("E2E server is up (pid ${process.pid()}), log: $log")
    }

    private fun stopLeftoverServer(runDir: File) {
        val pidFile = File(runDir, E2eServer.PID_FILE)
        if (!pidFile.isFile) return
        val handle = pidFile.readText().trim().toLongOrNull()?.let { ProcessHandle.of(it).orElse(null) }
        if (handle != null && handle.isAlive) {
            logger.warn("Killing leftover E2E server (pid ${handle.pid()})")
            handle.destroyForcibly()
            handle.onExit().get(30, TimeUnit.SECONDS)
        }
        pidFile.delete()
    }
}

/** Stops the E2E server via RCON `stop`, falling back to killing the process. Safe to run when nothing is up. */
@DisableCachingByDefault(because = "Stops an external process")
abstract class StopE2eServer : DefaultTask() {
    @get:Internal abstract val runDirectory: DirectoryProperty
    @get:Input abstract val rconPort: Property<Int>
    @get:Input abstract val stopTimeoutSeconds: Property<Long>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun stop() {
        val runDir = runDirectory.get().asFile
        val pidFile = File(runDir, E2eServer.PID_FILE)
        if (!pidFile.isFile) {
            logger.lifecycle("No E2E server is running")
            return
        }
        val handle = pidFile.readText().trim().toLongOrNull()?.let { ProcessHandle.of(it).orElse(null) }
        if (handle == null || !handle.isAlive) {
            pidFile.delete()
            logger.lifecycle("E2E server already stopped")
            return
        }
        try {
            Rcon("127.0.0.1", rconPort.get(), E2eServer.readPassword(runDir)).use { it.fire("stop") }
            logger.lifecycle("Sent 'stop' over RCON")
        } catch (e: Exception) {
            logger.warn("RCON stop failed (${e.message}); killing the server")
            handle.destroyForcibly()
        }
        try {
            handle.onExit().get(stopTimeoutSeconds.get(), TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("E2E server did not stop within ${stopTimeoutSeconds.get()}s; killing it")
            handle.descendants().forEach { it.destroyForcibly() }
            handle.destroyForcibly()
        }
        pidFile.delete()
        logger.lifecycle("E2E server stopped")
    }
}
