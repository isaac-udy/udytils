package dev.isaacudy.udytils.rules.web

import dev.isaacudy.udytils.rules.Packages
import dev.isaacudy.udytils.rules.forbiddenImports

import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.RuleGroup

@Describe(
    "The web family serves server-rendered HTML: `htmx` (typed htmx and Alpine attributes for " +
        "kotlinx.html, Ktor request and response helpers, out-of-band list updates, bundled scripts) " +
        "and `html-snapshot` (golden-file assertions over normalised HTML). Both are JVM-only and " +
        "standalone within udytils.",
)
object WebModules : RuleGroup() {

    @Describe("The web family must not depend on any other udytils family, and its two modules must not depend on each other")
    val standaloneModules by rule {
        rationale(
            "a server-rendered application takes either module without inheriting KMP, Compose or " +
                "rpc dependencies, and a test-only snapshot library stays out of production classpaths",
        )
        scope { scope, exempt ->
            forbiddenImports(
                scope = scope,
                exempt = exempt,
                inFiles = { "/web/htmx/" in it },
                forbidden = listOf(Packages.UDYTILS),
                allowed = listOf(Packages.HTMX),
                because = "the htmx module is standalone within udytils",
            ) + forbiddenImports(
                scope = scope,
                exempt = exempt,
                inFiles = { "/web/html-snapshot/" in it },
                forbidden = listOf(Packages.UDYTILS),
                allowed = listOf(Packages.HTML_SNAPSHOT),
                because = "the html-snapshot module is standalone within udytils",
            )
        }
    }
}
