package dev.isaacudy.udytils.rules

import com.lemonappdev.konsist.api.Konsist
import dev.isaacudy.udytils.architecture.ArchitectureDefinition
import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.DocsConfig
import dev.isaacudy.udytils.rules.architecture.ArchitectureModules
import dev.isaacudy.udytils.rules.core.CoreModule
import dev.isaacudy.udytils.rules.postgres.PostgresModules
import dev.isaacudy.udytils.rules.ui.UiModule
import dev.isaacudy.udytils.rules.urpc.UrpcModules

@Describe(
    """
    # The udytils architecture

    udytils is not one library but a set of independent families — core, ui, urpc, postgres,
    and the architecture framework — living in one repository. The one structural promise the
    repository makes is that the families stay independent: core depends on nothing, ui builds
    only on core, and the server-side families never leak into each other or into UI code.

    The rules below express those boundaries as import rules over each family's sources, so a
    dependency that would entangle two families fails the build instead of quietly becoming
    load-bearing.

    {{toc}}
    """,
)
object UdytilsArchitecture : ArchitectureDefinition(
    groups = listOf(CoreModule, UiModule, UrpcModules, PostgresModules, ArchitectureModules),
    scope = {
        Konsist.scopeFromProject().slice { file ->
            val path = file.path.replace('\\', '/')
            val library = listOf("/core/src/", "/ui/src/", "/urpc/", "/postgres/", "/architecture/")
                .any { it in path }
            val excluded = listOf(
                "/build/",
                "/samples/",
                "/urpc/sample/",
                "/architecture/udytils/", // the catalog itself is not governed code
                "/src/test/",
                "/src/commonTest/",
                "/src/jvmTest/",
                "/src/iosTest/",
                "/src/androidUnitTest/",
            ).any { it in path }
            library && !excluded
        }
    },
    membership = null,
    docs = DocsConfig(
        module = "architecture/udytils",
        regenerateCommand = "UPDATE_ARCHITECTURE_DOCS=true ./gradlew :udytils-architecture:test",
        verifyCommand = "./gradlew :udytils-architecture:test",
    ),
)
