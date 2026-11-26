package dev.isaacudy.udytils.mutation.gradle

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetsContainer

/**
 * The host whose tests run a module's mutants. By default a module with a JVM target uses the
 * JVM, and a module without one uses the Android host. `-Pmutation.hosts=android` selects the
 * Android host for every module that has an Android target. A module runs mutants on one host.
 */
internal object MutationHosts {
    const val JVM = "jvm"
    const val ANDROID = "android"
    private val known = setOf(JVM, ANDROID)

    fun parse(value: String): Set<String> {
        val hosts = value.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
        val unknown = hosts - known
        require(unknown.isEmpty()) { "-P${MutationPlugin.HOSTS_PROPERTY} lists $unknown. Hosts: ${known.joinToString()}." }
        return hosts
    }
}

internal fun Project.mutationHost(): String? {
    val requested = providers.gradleProperty(MutationPlugin.HOSTS_PROPERTY).orNull?.let(MutationHosts::parse)
    val platforms = when (val kotlin = extensions.findByName("kotlin")) {
        is KotlinTargetsContainer -> kotlin.targets.map { it.platformType }.toSet()
        null -> emptySet()
        else -> setOf(KotlinPlatformType.jvm)
    }
    val jvm = KotlinPlatformType.jvm in platforms
    val android = KotlinPlatformType.androidJvm in platforms
    return when {
        requested == null -> if (jvm) MutationHosts.JVM else if (android) MutationHosts.ANDROID else null
        MutationHosts.ANDROID in requested && android -> MutationHosts.ANDROID
        MutationHosts.JVM in requested && jvm -> MutationHosts.JVM
        else -> null
    }
}
