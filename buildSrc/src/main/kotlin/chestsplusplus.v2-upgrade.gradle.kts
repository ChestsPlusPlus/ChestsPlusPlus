// The v2 → v3 upgrade test bed (docs/v2-migration.md): a flat world built by ChestsPlusPlus v2 on Paper 1.21.7, then run
// on v3. The v2Fixture source set is the helper plugin that lays the world out through v2's own classes.

import xyz.jpenilla.runpaper.task.RunServer

plugins {
    java
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
    id("chestsplusplus.server-downloads")
    id("chestsplusplus.dev-server")
}

val v2RunDir = layout.projectDirectory.dir("run/v2-upgrade")
val v2SnapshotDir = layout.projectDirectory.dir("run/v2-upgrade-snapshot")

tasks.register<BuildV2Jar>("buildV2Jar") {
    group = "v2 upgrade"
    description = "Builds the ChestsPlusPlus v2 jar from the master branch with Maven."
    commit = providers.exec { commandLine("git", "rev-parse", "master") }.standardOutput.asText.map { it.trim() }
    mavenExecutable = providers.gradleProperty("chestsplusplus.maven")
        .orElse(if (System.getProperty("os.name").startsWith("Windows")) "mvn.cmd" else "mvn")
    javaLauncher = serverLauncher
    repository = layout.projectDirectory
    workDirectory = layout.buildDirectory.dir("v2/maven")
    output = layout.buildDirectory.file("v2/ChestsPlusPlus-v2.jar")
}

val v2Fixture: SourceSet = sourceSets.create("v2Fixture")

dependencies {
    "v2FixtureCompileOnly"(libs.paper.api.v2)
    "v2FixtureCompileOnly"(libs.jspecify)
    "v2FixtureCompileOnly"(v2Jar())
}

tasks.register<Jar>("v2FixtureJar") {
    group = "v2 upgrade"
    description = "Builds the helper plugin that lays out v2 worlds for the upgrade and the benchmark (test beds only, never shipped)."
    archiveBaseName = "ChestsPlusPlus-V2Fixture"
    from(v2Fixture.output)
}

tasks.register<BuildV2UpgradeFixture>("v2UpgradeFixture") {
    group = "v2 upgrade"
    description = "Builds run/v2-upgrade: a flat world with every v2 upgrade case, made by v2 on Paper 1.21.7. Needs -Pchestsplusplus.testPlayer=<name>."
    javaLauncher = serverLauncher
    serverJar = downloaded("downloadV2Paper")
    plugins.from(v2Jar(), tasks.named<Jar>("v2FixtureJar").flatMap { it.archiveFile }, downloaded("downloadViaVersion"))
    fixtureDirectory = layout.projectDirectory.dir("src/test/v2-upgrade")
    testPlayer = providers.gradleProperty("chestsplusplus.testPlayer")
    otherPlayer = "Alex"
    acceptEula = eulaAccepted
    rconPort = 25576
    runDirectory = v2RunDir
    snapshotDirectory = v2SnapshotDir
    storageFixture = layout.projectDirectory.file("src/test/resources/v2/fixture-storage.yml")
}

tasks.register<ResetV2Upgrade>("resetV2Upgrade") {
    group = "v2 upgrade"
    description = "Restores run/v2-upgrade to the v2 world v2UpgradeFixture built, so the upgrade can be tried again."
    runDirectory = v2RunDir
    snapshotDirectory = v2SnapshotDir
}

tasks.register<RunServer>("runV2Server") {
    group = "v2 upgrade"
    description = "Runs the v2 world on v2 (Paper 1.21.7 + ViaVersion, so a current client can join), for adding cases by hand."
    // run-paper needs the version even with a server jar.
    minecraftVersion(libs.versions.v2PaperServer.get())
    serverJar(downloaded("downloadV2Paper"))
    pluginJars.from(v2Jar(), downloaded("downloadViaVersion"))
    runDirectory(v2RunDir.asFile)
    javaLauncher = serverLauncher
    // The AC-missing case's recipe comes from a datapack removed before the upgrade. v2 drops a group whose recipe is gone and
    // saves without it, so the datapack is back for as long as v2 runs. resetV2Upgrade also removes it.
    val datapack = v2RunDir.dir("world/datapacks/v2fixture").asFile
    doFirst { layout.projectDirectory.dir("src/test/v2-upgrade/datapack").asFile.copyRecursively(datapack, overwrite = true) }
    doLast { datapack.deleteRecursively() }
}

tasks.register<RunServer>("runV2Upgrade") {
    group = "v2 upgrade"
    description = "Runs the v2 world on v3 (Paper 26.3) with hot-swap, like runServer. resetV2Upgrade puts it back to v2."
    minecraftVersion(libs.versions.paperServer.get())
    build(libs.versions.paperServerBuild.get().toInt())
    pluginJars.from(releaseJar)
    runDirectory(v2RunDir.asFile)
    hotSwap()
}
