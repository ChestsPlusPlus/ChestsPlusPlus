import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import xyz.jpenilla.resourcefactory.bukkit.Permission

// The plugin itself. The test and dev servers are convention plugins in buildSrc: chestsplusplus.dev-server (runServer),
// chestsplusplus.e2e, chestsplusplus.v2-upgrade and chestsplusplus.benchmark.
plugins {
    java
    id("com.gradleup.shadow")
    id("xyz.jpenilla.resource-factory-paper-convention")
    alias(libs.plugins.spotless)
    id("chestsplusplus.dev-server")
    id("chestsplusplus.e2e")
    id("chestsplusplus.v2-upgrade")
    id("chestsplusplus.benchmark")
}

group = "com.jamesdpeters"
version = providers.gradleProperty("releaseVersion").getOrElse("3.0.0-SNAPSHOT")
description = "Enhances chests and hoppers with ChestLinks, AutoCraft and hopper filters."

java {
    toolchain.languageVersion = JavaLanguageVersion.of(JAVA_VERSION)
}

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
    testRuntimeOnly(libs.slf4j.jdk14)
    testCompileOnly(libs.jspecify)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = JAVA_VERSION
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

paperPluginYaml {
    name = "ChestsPlusPlus"
    main = "$PLUGIN_PACKAGE.ChestsPlusPlus"
    bootstrapper = "$PLUGIN_PACKAGE.ChestsPlusPlusBootstrap"
    loader = "$PLUGIN_PACKAGE.ChestsPlusPlusLoader"
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
            "chestsplusplus.admin.migrate" to "Import ChestsPlusPlus v2 data and convert v2 hopper filters",
        )
        mapOf(Permission.Default.TRUE to everyone, Permission.Default.OP to ops).forEach { (level, nodes) ->
            nodes.forEach { (node, text) ->
                register(node) {
                    description = text
                    default = level
                }
            }
        }
    }
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveClassifier = ""
    relocate("org.bstats", "$PLUGIN_PACKAGE.libs.bstats")
}

val verifyReleaseJar = tasks.register<VerifyReleaseJar>("verifyReleaseJar") {
    group = "verification"
    description = "Fails if the release jar contains test-harness, spike or debug code."
    jar = tasks.shadowJar.flatMap { it.archiveFile }
    forbiddenPackages = listOf("$PLUGIN_PACKAGE.testharness".replace('.', '/') + "/", "$PLUGIN_PACKAGE.spikes".replace('.', '/') + "/")
    forbiddenStrings = listOf("cpptest", "testharness", "spike")
    expectedMain = "$PLUGIN_PACKAGE.ChestsPlusPlus"
    expectedBootstrapper = "$PLUGIN_PACKAGE.ChestsPlusPlusBootstrap"
    expectedLoader = "$PLUGIN_PACKAGE.ChestsPlusPlusLoader"
    allowedDescriptorKeys =
        listOf("api-version", "name", "version", "main", "bootstrapper", "loader", "description", "authors", "website", "permissions")
    report = layout.buildDirectory.file("reports/verifyReleaseJar.txt")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.build {
    dependsOn(verifyReleaseJar)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

fun taggedTests(name: String, tag: String, what: String) = tasks.register<Test>(name) {
    group = "verification"
    description = "Runs tests tagged '$tag' ($what)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags(tag) }
}
taggedTests("unitTest", "unit", "pure logic, no server")
taggedTests("integrationTest", "integration", "MockBukkit")

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
