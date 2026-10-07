import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.work.DisableCachingByDefault

/**
 * Builds the v2 plugin jar from a git ref (v2 lives on `master`, as a Maven build). v2 pins Lombok 1.18.32, which can't run on the
 * JDK 25 toolchain, so the copy is patched to a newer Lombok and annotation processing is forced on (JDK 23+ no longer runs it implicitly).
 */
@DisableCachingByDefault(because = "Runs Maven")
abstract class BuildV2Jar : DefaultTask() {
    /** The commit the ref points at, so a new v2 commit rebuilds the jar. */
    @get:Input abstract val commit: Property<String>
    @get:Input abstract val mavenExecutable: Property<String>
    @get:Nested abstract val javaLauncher: Property<JavaLauncher>
    @get:Internal abstract val repository: DirectoryProperty
    @get:Internal abstract val workDirectory: DirectoryProperty
    @get:OutputFile abstract val output: RegularFileProperty

    @TaskAction
    fun build() {
        val work = workDirectory.get().asFile.apply { deleteRecursively(); mkdirs() }
        val zip = File(work, "v2.zip")
        run(listOf("git", "archive", "--format=zip", "--output=${zip.absolutePath}", commit.get()), repository.get().asFile)
        val source = File(work, "src")
        unzip(zip, source)
        // The copy sits under this repo, whose lombok.config (fluent accessors) would otherwise apply to v2 too.
        File(source, "lombok.config").writeText("config.stopBubbling = true\n")
        val pom = File(source, "ChestsPlusPlus_Main/pom.xml")
        pom.writeText(pom.readText().replace(Regex("(<artifactId>lombok</artifactId>\\s*<version>)[^<]+(</version>)"), "$11.18.42$2"))
        val javaHome = javaLauncher.get().metadata.installationPath.asFile.absolutePath
        run(listOf(mavenExecutable.get(), "-q", "-B", "package", "-DskipTests", "-Dmaven.compiler.proc=full"), source, mapOf("JAVA_HOME" to javaHome))
        // v2's shade plugin writes the plugin jar (with its relocated libraries) to Server/plugins, not to target/.
        val plugins = File(source, "Server/plugins")
        val jar = plugins.listFiles { file -> file.name.startsWith("ChestsPlusPlus") && file.name.endsWith(".jar") }?.firstOrNull()
            ?: throw GradleException("Maven built no ChestsPlusPlus jar in $plugins")
        jar.copyTo(output.get().asFile, overwrite = true)
        logger.lifecycle("Built v2 from ${commit.get().take(10)}: ${output.get().asFile}")
    }

    private fun run(command: List<String>, directory: File, environment: Map<String, String> = emptyMap()) {
        val log = File(workDirectory.get().asFile, "build.log")
        val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
            .apply { environment().putAll(environment) }
            .start()
        if (process.waitFor() != 0) throw GradleException("'${command.joinToString(" ")}' failed; see $log")
    }

    private fun unzip(zip: File, into: File) {
        ZipInputStream(zip.inputStream()).use { input ->
            generateSequence { input.nextEntry }.forEach { entry ->
                val file = File(into, entry.name)
                if (entry.isDirectory) file.mkdirs() else file.apply { parentFile.mkdirs() }.outputStream().use { input.copyTo(it) }
            }
        }
    }
}

/**
 * Builds a flat world with ChestsPlusPlus v2 on Paper 1.21.7 for manual upgrade testing (docs/v2-migration-plan.md §8.1):
 * 1. starts v2 with the `v2-fixture` helper and runs `v2fixture build`, which sets every case up through v2's own classes;
 * 2. starts v2 again and fails if v2 can't read its own file back (this run also leaves v2's armour stands in the world);
 * 3. removes the v2 jars and the fixture datapack, so the folder looks like a server about to upgrade, and snapshots it.
 */
@DisableCachingByDefault(because = "Runs a Minecraft server")
abstract class BuildV2UpgradeFixture : DefaultTask() {
    @get:Nested abstract val javaLauncher: Property<JavaLauncher>
    @get:InputFile abstract val serverJar: RegularFileProperty
    @get:InputFiles abstract val plugins: ConfigurableFileCollection

    /** server.properties, the v2 config.yml and the datapack, from src/test/v2-upgrade. */
    @get:InputDirectory abstract val fixtureDirectory: DirectoryProperty
    @get:Input abstract val testPlayer: Property<String>
    @get:Input abstract val otherPlayer: Property<String>
    @get:Input abstract val acceptEula: Property<Boolean>
    @get:Input abstract val rconPort: Property<Int>
    @get:Internal abstract val runDirectory: DirectoryProperty
    @get:Internal abstract val snapshotDirectory: DirectoryProperty

    /** Where the v2-written storage.yml is copied, with the players' UUIDs replaced by fixed ones, for the parser tests. */
    @get:Internal abstract val storageFixture: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun build() {
        Servers.requireEula(acceptEula.get(), "Building the v2 world")
        val runDir = runDirectory.get().asFile
        checkNoServerRunning()
        val password = prepare(runDir)
        val server = ForegroundServer(
            javaLauncher.get().executablePath.asFile, listOf("-Xmx2G"), serverJar.get().asFile, runDir, rconPort.get(), password, "v2 server", logger,
        )

        val log = File(runDir, "fixture-build.log")
        server.use(log) { rcon ->
            val reply = rcon.command("v2fixture build ${testPlayer.get()} ${otherPlayer.get()}")
            if (!reply.contains("built")) throw GradleException("v2fixture build failed: $reply (see $log)")
            logger.lifecycle(reply.trim())
            rcon.command("save-all flush")
        }
        val reload = File(runDir, "fixture-reload.log")
        server.use(reload) { rcon -> rcon.command("save-all flush") }
        V2Upgrade.checkReadItsData(reload)

        File(runDir, "plugins").listFiles { file -> file.isFile && file.name.endsWith(".jar") }?.forEach { it.delete() }
        File(runDir, "world/datapacks/v2fixture").deleteRecursively()
        copyStorageFixture(File(runDir, "plugins/ChestsPlusPlus/data/storage.yml"))
        Servers.snapshot(runDir, snapshotDirectory.get().asFile)
        logger.lifecycle(
            "Built the v2 world in $runDir (snapshot in ${snapshotDirectory.get().asFile}); the v2 server used to build it has stopped. " +
                "Start a server to join with ./gradlew runV2Upgrade (on v3) or ./gradlew runV2Server (on v2)."
        )
    }

    /** Clearing the folder under a running server (runV2Server, runV2Upgrade) would break it, so refuse while the port is taken. */
    private fun checkNoServerRunning() {
        val properties = Properties()
        File(fixtureDirectory.get().asFile, "server.properties").reader().use(properties::load)
        val port = properties.getProperty("server-port").toInt()
        try {
            ServerSocket(port, 1, InetAddress.getLoopbackAddress()).close()
        } catch (e: IOException) {
            throw GradleException("Port $port is in use, probably by runV2Server or runV2Upgrade. Stop that server first; this task rebuilds its folder.")
        }
    }

    /** A fresh folder: properties, EULA, op for the tester, the v2 config and the datapack. Returns the RCON password. */
    private fun prepare(runDir: File): String {
        val fixture = fixtureDirectory.get().asFile
        listOf("world", "world_nether", "world_the_end", "plugins", "logs").forEach { File(runDir, it).deleteRecursively() }
        runDir.listFiles { file -> file.isFile && !file.name.endsWith(".jar") }?.forEach { it.delete() }
        runDir.mkdirs()
        val password = Servers.randomPassword()
        Servers.configure(runDir, File(fixture, "server.properties"), mapOf("rcon.port" to rconPort.get().toString(), "rcon.password" to password))
        val tester = testPlayer.get()
        File(runDir, "ops.json").writeText(
            """[{"uuid": "${offlineUuid(tester)}", "name": "$tester", "level": 4, "bypassesPlayerLimit": false}]""" + "\n"
        )
        val pluginsDir = File(runDir, "plugins").apply { mkdirs() }
        plugins.files.forEach { it.copyTo(File(pluginsDir, it.name), overwrite = true) }
        File(fixture, "config.yml").copyTo(File(pluginsDir, "ChestsPlusPlus/config.yml"), overwrite = true)
        File(fixture, "datapack").copyRecursively(File(runDir, "world/datapacks/v2fixture"), overwrite = true)
        return password
    }

    private fun copyStorageFixture(storage: File) {
        if (!storage.isFile) throw GradleException("v2 wrote no storage.yml ($storage)")
        val normalised = storage.readText(StandardCharsets.UTF_8)
            .replace(offlineUuid(testPlayer.get()).toString(), "11111111-1111-1111-1111-111111111111")
            .replace(offlineUuid(otherPlayer.get()).toString(), "22222222-2222-2222-2222-222222222222")
        storageFixture.get().asFile.apply { parentFile.mkdirs() }.writeText(normalised, StandardCharsets.UTF_8)
        logger.lifecycle("Copied v2's storage.yml to ${storageFixture.get().asFile}")
    }

    private fun offlineUuid(name: String): UUID = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(StandardCharsets.UTF_8))
}

/** Puts the v2 world back the way `v2UpgradeFixture` left it, keeping the server jars' caches. */
@DisableCachingByDefault(because = "Restores a folder")
abstract class ResetV2Upgrade : DefaultTask() {
    @get:Internal abstract val runDirectory: DirectoryProperty
    @get:Internal abstract val snapshotDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun reset() {
        val runDir = runDirectory.get().asFile
        val snapshotDir = snapshotDirectory.get().asFile
        if (!snapshotDir.isDirectory) throw GradleException("No snapshot in $snapshotDir; run ./gradlew v2UpgradeFixture first")
        Servers.clear(runDir)
        snapshotDir.copyRecursively(runDir, overwrite = true)
        logger.lifecycle("Restored the v2 world in $runDir")
    }
}

object V2Upgrade {
    /** How v2's own log lines and stack frames can be told apart from the server's. */
    val PLUGIN_MARKERS = listOf("ChestsPlusPlus", "com.jamesdpeters.minecraft.chests")

    /** v2 logs and carries on when it can't read a storage; that would make a broken world, so fail instead. */
    fun checkReadItsData(log: File) {
        val trouble = Servers.errorsFrom(log, PLUGIN_MARKERS)
        if (trouble.isNotEmpty()) {
            throw GradleException("v2 reported errors reading back its own data (first at line ${trouble.first() + 1} of $log)")
        }
    }
}
