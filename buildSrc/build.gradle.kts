plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(plugin(libs.plugins.shadow))
    implementation(plugin(libs.plugins.run.paper))
    implementation(plugin(libs.plugins.resource.factory.paper))
    implementation(plugin(libs.plugins.plugwright))
    // Lets the convention plugins read the version catalog as `libs` (https://github.com/gradle/gradle/issues/15383).
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

/** A plugin's marker coordinates, so the convention plugins can apply it by id. */
fun plugin(plugin: Provider<PluginDependency>) = plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
