package dev.isaacudy.udytils.architecture.gradle

import org.gradle.api.provider.Property

/**
 * Configuration for the udytils architecture plugin:
 *
 *     architecture {
 *         definition.set("architecture.rules.UkptArchitecture")
 *     }
 *
 * [definition] is the fully-qualified name of the module's `ArchitectureDefinition` object. When
 * set, the plugin generates the architecture test classes (`<Name>Test`, `<Name>DocsTest`,
 * `<Name>IntegrityTest`) into `build/generated/architecture/` — nothing test-related needs to be
 * checked into source control. When unset, no tests are generated and `src/architectureTest/kotlin`
 * can carry hand-written ones.
 */
abstract class ArchitectureExtension {
    abstract val definition: Property<String>
}
