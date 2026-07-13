package dev.isaacudy.udytils.rules.urpc

import dev.isaacudy.udytils.rules.Packages
import dev.isaacudy.udytils.rules.forbiddenImports

import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.RuleGroup

@Describe(
    "The urpc family is a typed RPC framework: protocol (shared contract types), client (KMP Ktor " +
        "client), server (JVM Ktor routing), processor (KSP codegen) and koin (server DI glue). The " +
        "protocol module is the only thing the two sides share.",
)
object UrpcModules : RuleGroup() {

    @Describe("The urpc protocol module may depend only on the core module")
    val protocolIsTheSharedContract by rule {
        rationale(
            "protocol is what generated code and both transports compile against; if it reached " +
                "into client or server, every consumer would drag in both transport stacks",
        )
        scope { scope, exempt ->
            forbiddenImports(
                scope = scope,
                exempt = exempt,
                inFiles = { "/urpc/protocol/" in it },
                forbidden = Packages.UI + listOf(
                    Packages.URPC + "client.",
                    Packages.URPC + "server.",
                    Packages.URPC + "koin.",
                    Packages.POSTGRES,
                    Packages.ARCHITECTURE,
                ),
                because = "protocol is the shared contract and must stay transport-free",
            )
        }
    }

    @Describe("The urpc client and server modules must not depend on each other")
    val clientAndServerStayApart by rule {
        rationale(
            "a KMP client that pulled in Ktor server code (or vice versa) could not compile for " +
                "its targets; the only shared vocabulary is the protocol module",
        )
        scope { scope, exempt ->
            forbiddenImports(
                scope = scope,
                exempt = exempt,
                inFiles = { "/urpc/client/" in it },
                forbidden = listOf(Packages.URPC + "server.", Packages.URPC + "koin."),
                because = "the client must not reach into server-side urpc modules",
            ) + forbiddenImports(
                scope = scope,
                exempt = exempt,
                inFiles = { "/urpc/server/" in it || "/urpc/koin/" in it },
                forbidden = listOf(Packages.URPC + "client."),
                because = "server-side urpc modules must not reach into the client",
            )
        }
    }

    @Describe("The urpc family must not depend on the ui, postgres, or architecture families")
    val staysOutOfOtherFamilies by rule {
        rationale(
            "urpc is transport infrastructure; UI rendering and database access belong to the " +
                "application wiring the pieces together, not to the framework",
        )
        scope { scope, exempt ->
            forbiddenImports(
                scope = scope,
                exempt = exempt,
                inFiles = { "/urpc/" in it },
                forbidden = Packages.UI + listOf(Packages.POSTGRES, Packages.ARCHITECTURE),
                because = "urpc must stay independent of the other udytils families",
            )
        }
    }
}
