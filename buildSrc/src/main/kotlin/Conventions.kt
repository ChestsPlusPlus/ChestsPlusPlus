import java.io.File
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import xyz.jpenilla.runpaper.task.RunServer

const val JAVA_VERSION = 25
const val PLUGIN_PACKAGE = "com.jamesdpeters.chestsplusplus"

val Project.libs: LibrariesForLibs get() = the()

/** Where downloaded servers and plugins are kept, shared between checkouts. */
val Project.serverCacheDir: File get() = gradle.gradleUserHomeDir.resolve("caches/chestsplusplus-e2e")

/** Running a server means accepting the Minecraft EULA, with -Pchestsplusplus.acceptMinecraftEula=true or ACCEPT_MINECRAFT_EULA=true. */
val Project.eulaAccepted: Provider<Boolean>
    get() = providers.gradleProperty("chestsplusplus.acceptMinecraftEula")
        .orElse(providers.environmentVariable("ACCEPT_MINECRAFT_EULA"))
        .map { it.toBoolean() }
        .orElse(false)

val Project.serverLauncher: Provider<JavaLauncher>
    get() = extensions.getByType<JavaToolchainService>().launcherFor { languageVersion.set(JavaLanguageVersion.of(JAVA_VERSION)) }

val Project.releaseJar: Provider<RegularFile> get() = tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }

/** A file fetched by one of the `chestsplusplus.server-downloads` tasks; depending on it runs the download. */
fun Project.downloaded(task: String): Provider<RegularFile> = tasks.named<DownloadFile>(task).flatMap { it.destination }

/** The v2 plugin jar: built from `master` with Maven, unless -Pchestsplusplus.v2Jar points at one. */
fun Project.v2Jar(): FileCollection =
    // files(task) carries the task dependency, which a mapped provider would lose.
    providers.gradleProperty("chestsplusplus.v2Jar").map { files(it) }.getOrElse(files(tasks.named("buildV2Jar")))

/** Runs on the JetBrains Runtime with the dev agent, which swaps in classes as `./gradlew classes` rebuilds them. */
fun RunServer.hotSwap() {
    javaLauncher.set(project.extensions.getByType<JavaToolchainService>().launcherFor {
        languageVersion.set(JavaLanguageVersion.of(JAVA_VERSION))
        vendor.set(JvmVendorSpec.JETBRAINS)
    })
    val agentJar = project.tasks.named<Jar>("devAgentJar")
    dependsOn(agentJar)
    val agent = agentJar.flatMap { it.archiveFile }
    val classes = project.extensions.getByType<SourceSetContainer>().named("main").flatMap { it.java.destinationDirectory }
    jvmArgumentProviders.add { listOf("-XX:+AllowEnhancedClassRedefinition", "-javaagent:${agent.get().asFile}=${classes.get().asFile}") }
}
