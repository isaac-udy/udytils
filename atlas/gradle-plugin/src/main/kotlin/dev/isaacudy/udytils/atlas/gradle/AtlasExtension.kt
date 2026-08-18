package dev.isaacudy.udytils.atlas.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Configuration for the `dev.isaacudy.udytils.atlas` plugin.
 *
 *     atlas {
 *         chromeEdge("HomeDestination", "SettingsDestination")
 *         featureGroupFallback("app/admin", "admin")
 *     }
 */
abstract class AtlasExtension {
    abstract val chromeEdges: ListProperty<Pair<String, String>>
    abstract val outputDirectory: DirectoryProperty
    abstract val projectName: Property<String>
    abstract val sourceRoots: ListProperty<String>
    abstract val goldenRoots: ListProperty<String>
    abstract val extraExcludePaths: ListProperty<String>
    abstract val featureGroupFallbacks: ListProperty<Pair<String, String>>

    fun chromeEdge(source: String, target: String) {
        chromeEdges.add(source to target)
    }

    fun featureGroupFallback(pathPrefix: String, label: String) {
        featureGroupFallbacks.add(pathPrefix to label)
    }
}
