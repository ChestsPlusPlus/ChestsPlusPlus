rootProject.name = "ChestsPlusPlus"

pluginManagement {
    // Plugins' own dependencies come straight from Maven Central rather than through the plugin portal's flaky mirror.
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
