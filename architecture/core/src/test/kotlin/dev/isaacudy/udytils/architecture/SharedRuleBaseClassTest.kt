package dev.isaacudy.udytils.architecture

import dev.isaacudy.udytils.architecture.fixtures.sharedrules.client.ClientGroup
import dev.isaacudy.udytils.architecture.fixtures.sharedrules.server.ServerGroup
import dev.isaacudy.udytils.architecture.fixtures.sharedrules.client.UseCase as ClientUseCase
import dev.isaacudy.udytils.architecture.fixtures.sharedrules.server.UseCase as ServerUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The shared-rule pattern: rules declared once on an abstract `Construct`/`RuleGroup` base class,
 * instantiated by one concrete `object` per group. Everything here relies on registration being
 * per-*instance* (a base-class `by rule` runs during each object's initialization) and ids being
 * resolved lazily from the concrete object — so one rule body yields one independent rule per side,
 * each under its own id.
 */
class SharedRuleBaseClassTest {

    private val rules = enforcedRules(listOf(ClientGroup, ServerGroup), membership = null)
    private fun rule(id: String): Rule = rules.single { it.id == id }

    @Test
    fun `inherited construct rules register per concrete object under group-prefixed ids`() {
        val ids = rules.map { it.id }
        assertTrue("ClientGroup.UseCase.noMutableState" in ids)
        assertTrue("ServerGroup.UseCase.noMutableState" in ids)
        assertTrue("ClientGroup.UseCase.mayInjectDomainInterfaces" in ids)
        assertTrue("ServerGroup.UseCase.mayInjectDomainInterfaces" in ids)
    }

    @Test
    fun `inherited group rules register per concrete group`() {
        assertEquals(Tag.TESTED, rule("ClientGroup.noPlatformDeps").tag)
        assertEquals(Tag.TESTED, rule("ServerGroup.noPlatformDeps").tag)
    }

    @Test
    fun `side-specific rules extend one side without leaking to the other`() {
        val ids = rules.map { it.id }
        assertTrue("ClientGroup.UseCase.clientOnlyRule" in ids)
        assertFalse(ids.any { "clientOnlyRule" in it && it.startsWith("ServerGroup.") })
    }

    @Test
    fun `base rules render before side-specific rules`() {
        val clientRules = ClientUseCase.declaredRules.map { it.id }
        assertTrue(clientRules.indexOf("ClientGroup.UseCase.noMutableState") < clientRules.indexOf("ClientGroup.UseCase.clientOnlyRule"))
    }

    @Test
    fun `Describe on a base-class property becomes each side's statement`() {
        assertEquals("A UseCase must not contain mutable state", rule("ClientGroup.UseCase.noMutableState").title)
        assertEquals("A UseCase must not contain mutable state", rule("ServerGroup.UseCase.noMutableState").title)
    }

    @Test
    fun `constructor side parameter is available when the shared rule block runs`() {
        assertEquals("Shared rule body, instantiated for the client side", rule("ClientGroup.UseCase.noMutableState").rationale)
        assertEquals("Shared rule body, instantiated for the server side", rule("ServerGroup.UseCase.noMutableState").rationale)
        assertEquals("Shared group rule body, instantiated for the client side", rule("ClientGroup.noPlatformDeps").rationale)
        assertEquals("Shared group rule body, instantiated for the server side", rule("ServerGroup.noPlatformDeps").rationale)
    }

    @Test
    fun `owner resolves through the intermediate base class`() {
        assertSame(ClientGroup, ClientUseCase.owner)
        assertSame(ServerGroup, ServerUseCase.owner)
    }

    @Test
    fun `each side's package gate and side-parameterized requirements apply`() {
        assertTrue(ClientUseCase.requirements.any { "feature..client.domain.." in it.description })
        assertTrue(ServerUseCase.requirements.any { "feature..server.domain.." in it.description })
        assertFalse(ClientUseCase.requirements.any { "server" in it.description })
    }

    @Test
    fun `the catalog passes integrity checks - no duplicate ids across the two sides`() {
        ArchitectureRun(listOf(ClientGroup, ServerGroup), { error("scope is never evaluated") })
    }

    // -- the hazard the pattern's constructor-parameter convention exists to avoid ---------------

    private object HazardGroup : RuleGroup()

    private abstract class OpenValBase<G : RuleGroup> : Construct<G>(requirements = emptyList()) {
        open val sideName: String = "base"

        @Describe("Fixture statement")
        val capturedEagerly by rule {
            rationale("side: $sideName")
            constrain { _, _ -> emptyList() }
        }
    }

    private object HazardConstruct : OpenValBase<HazardGroup>() {
        override val sideName: String = "client"
    }

    @Test
    fun `open vals are unusable in shared rule blocks - rule blocks run before overrides initialize`() {
        // The override's backing field is still null while the base-class initializer runs, so the
        // eagerly-evaluated rationale sees neither "base" nor "client". Side context must therefore
        // be passed as a constructor parameter (as SidedUseCaseRules does).
        assertEquals("side: null", HazardConstruct.capturedEagerly.rationale)
    }
}
