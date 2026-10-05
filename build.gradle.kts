import me.drownek.plugwright.api.secret
import me.drownek.plugwright.external.ExternalMode
import xyz.jpenilla.resourcefactory.ResourceFactoryExtension
import xyz.jpenilla.resourcefactory.bukkit.Permission
import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.resource.factory.paper)
    alias(libs.plugins.spotless)
    alias(libs.plugins.plugwright)
}

group = "com.jamesdpeters"
version = providers.gradleProperty("releaseVersion").getOrElse("3.0.0-SNAPSHOT")
description = "Enhances chests and hoppers with ChestLinks, AutoCraft and hopper filters."

val javaVersion = 25
val pluginPackage = "com.jamesdpeters.chestsplusplus"
val harnessPackage = "$pluginPackage.testharness"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}

// ---------------------------------------------------------------------------------------------------------------
// Source sets: main (shipped), test (unit + MockBukkit), testHarness (E2E-only plugin, never shipped; plan §10.3).
// ---------------------------------------------------------------------------------------------------------------
val testHarness: SourceSet = sourceSets.create("testHarness")

// Libraries Paper downloads at startup (ChestsPlusPlusLoader reads paper-libraries.txt) instead of us shading them.
val paperLibrary: Configuration by configurations.creating
configurations.compileOnly { extendsFrom(paperLibrary) }
configurations.testImplementation { extendsFrom(paperLibrary) }

dependencies {
    paperLibrary(libs.jdbi.core)
    compileOnly(libs.paper.api)
    compileOnly(libs.jspecify)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.bstats.bukkit)

    // Tests compile and run against paper-api 26.2 + mockbukkit-v26.2 until mockbukkit-v26.3 exists (plan §10.2).
    testImplementation(libs.paper.api.test)
    testImplementation(libs.mockbukkit)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.sqlite.jdbc)
    testCompileOnly(libs.jspecify)
    testRuntimeOnly(libs.junit.platform.launcher)

    "testHarnessCompileOnly"(libs.paper.api)
    "testHarnessCompileOnly"(libs.jspecify)
    "testHarnessCompileOnly"(sourceSets.main.get().output)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
    // -classfile: paper-api's JOML dependency (used by Display transformations) trips it on every use.
    // -processing: Lombok is the only annotation processor, so every other annotation is "unclaimed".
    // -parameters: JDBI binds :named SQL parameters and maps columns onto records by parameter name.
    options.compilerArgs.addAll(listOf("-Xlint:all,-classfile,-processing", "-Werror", "-parameters"))
}

val paperLibrariesDir = layout.buildDirectory.dir("generated/paperLibraries")
val writePaperLibraries = tasks.register("writePaperLibraries") {
    val coordinates = paperLibrary.dependencies.map { "${it.group}:${it.name}:${it.version}" }
    inputs.property("coordinates", coordinates)
    outputs.dir(paperLibrariesDir)
    doLast { paperLibrariesDir.get().file("paper-libraries.txt").asFile.writeText(coordinates.joinToString("\n", postfix = "\n")) }
}
sourceSets.main { resources.srcDir(writePaperLibraries) }

// ---------------------------------------------------------------------------------------------------------------
// paper-plugin.yml (generated)
// ---------------------------------------------------------------------------------------------------------------
paperPluginYaml {
    name = "ChestsPlusPlus"
    main = "$pluginPackage.ChestsPlusPlus"
    bootstrapper = "$pluginPackage.ChestsPlusPlusBootstrap"
    loader = "$pluginPackage.ChestsPlusPlusLoader"
    apiVersion = "26.3"
    authors.add("James Peters")
    website = "https://github.com/ChestsPlusPlus/ChestsPlusPlus"
    permissions {
        val everyone = mapOf(
            "chestsplusplus.chestlink.create" to "Create and link ChestLinks",
            "chestsplusplus.chestlink.open" to "Open ChestLinks by clicking them",
            "chestsplusplus.chestlink.remote" to "Open ChestLinks remotely (command or menu)",
            "chestsplusplus.chestlink.menu" to "Use the ChestLink menu",
            "chestsplusplus.chestlink.remove" to "Remove your ChestLinks",
            "chestsplusplus.chestlink.sort" to "Change ChestLink sorting",
            "chestsplusplus.chestlink.members" to "Manage ChestLink members",
            "chestsplusplus.autocraft.create" to "Create and link AutoCrafters",
            "chestsplusplus.autocraft.open" to "Open AutoCrafters by clicking them",
            "chestsplusplus.autocraft.remote" to "Open AutoCrafters remotely (command or menu)",
            "chestsplusplus.autocraft.menu" to "Use the AutoCraft menu",
            "chestsplusplus.autocraft.remove" to "Remove your AutoCrafters",
            "chestsplusplus.autocraft.members" to "Manage AutoCrafter members",
            "chestsplusplus.filter" to "Edit hopper filters",
            "chestsplusplus.trust" to "Manage your trusted players",
        )
        val ops = mapOf(
            "chestsplusplus.admin.bypass" to "Access and manage every group",
            "chestsplusplus.admin.reload" to "Use /chestsplusplus reload",
            "chestsplusplus.admin.update" to "Receive update notifications",
            "chestsplusplus.admin.version" to "Use /chestsplusplus version",
        )
        everyone.forEach { (node, text) ->
            register(node) {
                description = text
                default = Permission.Default.TRUE
            }
        }
        ops.forEach { (node, text) ->
            register(node) {
                description = text
                default = Permission.Default.OP
            }
        }
    }
}

sourceSets.named("testHarness") {
    extensions.configure<ResourceFactoryExtension> {
        paperPluginYaml {
            name = "ChestsPlusPlus-TestHarness"
            version = project.version.toString()
            description = "E2E inspection commands for ChestsPlusPlus. Test servers only; never shipped."
            main = "$harnessPackage.ChestsPlusPlusTestHarness"
            apiVersion = "26.3"
            dependencies {
                server("ChestsPlusPlus", PaperPluginYaml.Load.BEFORE, required = true, joinClasspath = true)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Jars
// ---------------------------------------------------------------------------------------------------------------
tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveClassifier = ""
    relocate("org.bstats", "$pluginPackage.libs.bstats")
}

val testHarnessJar = tasks.register<Jar>("testHarnessJar") {
    group = "build"
    description = "Builds ChestsPlusPlus-TestHarness.jar (E2E servers only, never shipped)."
    archiveBaseName = "ChestsPlusPlus-TestHarness"
    from(testHarness.output)
}

val verifyReleaseJar = tasks.register<VerifyReleaseJar>("verifyReleaseJar") {
    group = "verification"
    description = "Fails if the release jar contains test-harness, spike or debug code."
    jar = tasks.shadowJar.flatMap { it.archiveFile }
    forbiddenPackages = listOf(harnessPackage.replace('.', '/') + "/", "$pluginPackage.spikes".replace('.', '/') + "/")
    forbiddenStrings = listOf("cpptest", "testharness", "spike")
    expectedMain = "$pluginPackage.ChestsPlusPlus"
    expectedBootstrapper = "$pluginPackage.ChestsPlusPlusBootstrap"
    allowedDescriptorKeys =
        listOf("api-version", "name", "version", "main", "bootstrapper", "description", "authors", "website", "permissions")
    report = layout.buildDirectory.file("reports/verifyReleaseJar.txt")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.build {
    dependsOn(verifyReleaseJar)
}

// ---------------------------------------------------------------------------------------------------------------
// Tests: `test` runs everything; tags allow running layers separately.
// ---------------------------------------------------------------------------------------------------------------
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.register<Test>("unitTest") {
    group = "verification"
    description = "Runs tests tagged 'unit' (pure logic, no server)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("unit") }
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs tests tagged 'integration' (MockBukkit)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
}

// ---------------------------------------------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------------------------------------------
spotless {
    java {
        target("src/*/java/**/*.java")
        eclipse().configFile("config/eclipse-formatter.xml")
        importOrder("\\#", "")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Dev server: ./gradlew runServer (Paper 26.3, plugin only)
// ---------------------------------------------------------------------------------------------------------------
tasks.runServer {
    minecraftVersion(libs.versions.paperServer.get())
    build(libs.versions.paperServerBuild.get().toInt())
}

// ---------------------------------------------------------------------------------------------------------------
// E2E (plan §10.3.1, Stage A): Paper 26.3 + ViaVersion/ViaBackwards, bots speak 26.1, commands over RCON.
// ---------------------------------------------------------------------------------------------------------------
val e2eRunDir = layout.projectDirectory.dir("run/e2e")
val e2eCacheDir = gradle.gradleUserHomeDir.resolve("caches/chestsplusplus-e2e")
val e2eServerPort = 25565
val e2eRconPort = 25575

val downloadE2ePaper = tasks.register<DownloadFile>("downloadE2ePaper") {
    url = libs.versions.paperServerUrl
    checksum = "sha256:" + libs.versions.paperServerSha256.get()
    destination = File(e2eCacheDir, "paper-${libs.versions.paperServer.get()}-${libs.versions.paperServerBuild.get()}.jar")
}
val downloadViaVersion = tasks.register<DownloadFile>("downloadViaVersion") {
    url = libs.versions.viaversionUrl
    checksum = "sha512:" + libs.versions.viaversionSha512.get()
    destination = File(e2eCacheDir, "ViaVersion-${libs.versions.viaversion.get()}.jar")
}
val downloadViaBackwards = tasks.register<DownloadFile>("downloadViaBackwards") {
    url = libs.versions.viabackwardsUrl
    checksum = "sha512:" + libs.versions.viabackwardsSha512.get()
    destination = File(e2eCacheDir, "ViaBackwards-${libs.versions.viabackwards.get()}.jar")
}

val e2ePlugins = files(
    tasks.shadowJar.flatMap { it.archiveFile },
    testHarnessJar.flatMap { it.archiveFile },
    downloadViaVersion.flatMap { it.destination },
    downloadViaBackwards.flatMap { it.destination },
)
val e2eLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(javaVersion) }
val eulaAccepted = providers.gradleProperty("chestsplusplus.acceptMinecraftEula")
    .orElse(providers.environmentVariable("ACCEPT_MINECRAFT_EULA"))
    .map { it.toBoolean() }
    .orElse(false)

val startE2eServer = tasks.register<StartE2eServer>("startE2eServer") {
    group = "e2e"
    description = "Starts the Via-bridged Paper 26.3 E2E server in the background and waits for it to be ready."
    javaLauncher = e2eLauncher
    serverJar = downloadE2ePaper.flatMap { it.destination }
    plugins.from(e2ePlugins)
    serverProperties = layout.projectDirectory.file("src/test/e2e-server/server.properties")
    runDirectory = e2eRunDir
    jvmArgs = listOf("-Xmx2G")
    startupTimeoutSeconds = 300L
    acceptEula = eulaAccepted
    rconPassword = providers.environmentVariable("E2E_RCON_PASSWORD")
}

val stopE2eServer = tasks.register<StopE2eServer>("stopE2eServer") {
    group = "e2e"
    description = "Stops the E2E server over RCON."
    runDirectory = e2eRunDir
    rconPort = e2eRconPort
    stopTimeoutSeconds = 60L
}

// The same server in the foreground, for poking at it by hand (run startE2eServer once first to create the fixture).
tasks.register<RunServer>("runE2eServer") {
    group = "e2e"
    description = "Runs the E2E server (Paper 26.3 + Via + test harness) in the foreground."
    serverJar(downloadE2ePaper.flatMap { it.destination })
    pluginJars.from(e2ePlugins)
    runDirectory(e2eRunDir.asFile)
    javaLauncher = e2eLauncher
}

plugwright {
    testsDir.set(file("src/test/e2e"))
    downloadNode.set(true)
    primaryEnvironment.set("paper263")
    environments {
        create("paper263", ExternalMode) {
            host.set("127.0.0.1")
            port.set(e2eServerPort)
            minecraftVersion.set("26.1") // bot protocol; ViaBackwards translates to the 26.3 server
            includeInMatrix.set(true)
            // Offline-mode server without an auth plugin: every lease gets a fresh, never-reused name.
            accounts {
                autoRegister {
                    usernamePattern.set("pw_%s")
                    max.set(4)
                    // Required by Plugwright but never used: no auth plugin runs on the E2E server.
                    password.set(secret.file(file("src/test/e2e-server/bot-password.txt")))
                }
            }
            console {
                rcon {
                    port.set(e2eRconPort)
                    password.set(secret.file(e2eRunDir.file(E2eServer.PASSWORD_FILE).asFile))
                }
            }
        }
    }
}

// Only `e2e` owns the server lifecycle; plugwrightTest on its own targets whatever server is already up.
val e2eRequested = gradle.startParameter.taskNames.any { it == "e2e" || it.endsWith(":e2e") }
tasks.matching { it.name == "plugwrightTest" }.configureEach {
    mustRunAfter(startE2eServer)
    if (e2eRequested) finalizedBy(stopE2eServer)
}

tasks.register("e2e") {
    group = "e2e"
    description = "Starts the E2E server, runs the Plugwright suite against it, then stops it."
    dependsOn(startE2eServer, "plugwrightTest")
    finalizedBy(stopE2eServer)
}
