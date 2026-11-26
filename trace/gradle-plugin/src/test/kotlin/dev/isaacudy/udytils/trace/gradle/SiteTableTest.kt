package dev.isaacudy.udytils.trace.gradle

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SiteTableTest {
    private val root = File("/repo")

    @Test
    fun `a site takes the Constructs of the innermost declaration containing its line`() {
        val manifest = Manifest(
            file = "/repo/feature/core/client/src/commonMain/kotlin/Repo.kt",
            sites = listOf(ManifestSite(1, "Repo.greet", line = 12), ManifestSite(2, "outside", line = 40)),
        )
        val classification = Classification(
            constructs = listOf(ClassifiedConstruct("ClientData.Repository")),
            declarations = listOf(
                Declaration("feature/core/client/src/commonMain/kotlin/Repo.kt", 10, 27, listOf("ClientData.Repository")),
                Declaration("feature/core/client/src/commonMain/kotlin/Repo.kt", 1, 30, listOf("Other")),
            ),
        )
        val rendered = SiteTable.join(listOf(manifest), classification, root).render("app", listOf("ClientData.Repository"))
        assertTrue("1L -> listOf(\"ClientData.Repository\")" in rendered, rendered)
        assertTrue("2L ->" !in rendered, rendered)
    }

    @Test
    fun `Construct IDs render as nested constants`() {
        assertEquals(
            """
            object Constructs {
                object ClientData {
                    const val Repository: String = "ClientData.Repository"
                }
                object ClientUi {
                    const val ViewModel: String = "ClientUi.ViewModel"
                }
            }

            """.trimIndent(),
            SiteTable.renderConstructs(listOf("ClientUi.ViewModel", "ClientData.Repository")),
        )
    }
}
