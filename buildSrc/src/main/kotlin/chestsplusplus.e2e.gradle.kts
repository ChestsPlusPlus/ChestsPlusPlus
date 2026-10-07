// E2E: Paper 26.3 + ViaVersion/ViaBackwards, Plugwright bots speak 26.1, and commands go over RCON. The testHarness source set is a
// plugin with inspection commands for these servers only; it is never shipped.

import me.drownek.plugwright.api.secret
import me.drownek.plugwright.external.ExternalMode
import xyz.jpenilla.resourcefactory.ResourceFactoryExtension
import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    java
    id("com.gradleup.shadow")
    id("xyz.jpenilla.resource-factory-paper-convention")
    id("xyz.jpenilla.run-paper")
    id("io.github.drownek.plugwright")
    id("chestsplusplus.server-downloads")
}

val e2eRunDir = layout.projectDirectory.dir("run/e2e")
val e2eServerPort = 25565
val e2eRconPort = 25575

val testHarness: SourceSet = sourceSets.create("testHarness")

dependencies {
    "testHarnessCompileOnly"(libs.paper.api)
    "testHarnessCompileOnly"(libs.jspecify)
    "testHarnessCompileOnly"(sourceSets.main.get().output)
}

testHarness.extensions.configure<ResourceFactoryExtension> {
    paperPluginYaml {
        name = "ChestsPlusPlus-TestHarness"
        version = project.version.toString()
        description = "E2E inspection commands for ChestsPlusPlus. Test servers only; never shipped."
        main = "$PLUGIN_PACKAGE.testharness.ChestsPlusPlusTestHarness"
        apiVersion = "26.3"
        dependencies {
            server("ChestsPlusPlus", PaperPluginYaml.Load.BEFORE, required = true, joinClasspath = true)
        }
    }
}

val testHarnessJar = tasks.register<Jar>("testHarnessJar") {
    group = "build"
    description = "Builds ChestsPlusPlus-TestHarness.jar (E2E servers only, never shipped)."
    archiveBaseName = "ChestsPlusPlus-TestHarness"
    from(testHarness.output)
}

val e2ePlugins = files(releaseJar, testHarnessJar.flatMap { it.archiveFile }, downloaded("downloadViaVersion"), downloaded("downloadViaBackwards"))

val startE2eServer = tasks.register<StartE2eServer>("startE2eServer") {
    group = "e2e"
    description = "Starts the Via-bridged Paper 26.3 E2E server in the background and waits for it to be ready."
    javaLauncher = serverLauncher
    serverJar = downloaded("downloadE2ePaper")
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
    serverJar(downloaded("downloadE2ePaper"))
    pluginJars.from(e2ePlugins)
    runDirectory(e2eRunDir.asFile)
    javaLauncher = serverLauncher
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
