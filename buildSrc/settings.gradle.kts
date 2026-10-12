pluginManagement {
    // kotlin-dsl's Kotlin artifacts live on Maven Central; the plugin portal's mirror of them intermittently 404s in CI.
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
