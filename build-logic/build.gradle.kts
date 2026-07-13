plugins {
    `kotlin-dsl`
}

dependencies {
    // Makes the vanniktech plugin's classes available to the precompiled script plugins.
    implementation(plugin(libs.plugins.vanniktech.mavenPublish))
}

/** Maps a version-catalog plugin alias to its plugin-marker artifact coordinates. */
fun plugin(dependency: Provider<PluginDependency>): Provider<String> =
    dependency.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
