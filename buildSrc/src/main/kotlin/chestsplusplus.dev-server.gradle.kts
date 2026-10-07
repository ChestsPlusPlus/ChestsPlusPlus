// ./gradlew runServer: Paper 26.3 with only the plugin. While it runs, `./gradlew classes` (or an IntelliJ build) hot-swaps the changed
// classes: the JetBrains Runtime allows structural changes and devAgent watches the class output.

plugins {
    java
    id("xyz.jpenilla.run-paper")
}

val devAgent: SourceSet = sourceSets.create("devAgent")

tasks.register<Jar>("devAgentJar") {
    group = "build"
    description = "Builds the hot-swap agent for runServer (dev only, never shipped)."
    archiveBaseName = "ChestsPlusPlus-DevAgent"
    from(devAgent.output)
    manifest.attributes("Premain-Class" to "$PLUGIN_PACKAGE.devagent.HotSwapAgent", "Can-Redefine-Classes" to "true")
}

tasks.runServer {
    minecraftVersion(libs.versions.paperServer.get())
    build(libs.versions.paperServerBuild.get().toInt())
    hotSwap()
}
