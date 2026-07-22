package dev.isaacudy.udytils.snapshot

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the fail-fast guard behind [assertNoGoldenPathCollisions]. Two previews resolving to the
 * same golden path would silently overwrite one another's PNG, so this has to fail loudly and name
 * every claimant.
 */
class GoldenPathCollisionTest {

    @Test
    fun uniquePathsProduceNoMessage() {
        val message = goldenPathCollisionMessage(
            listOf(
                GoldenPathClaim("feature/ukpt/ui/UkptScreenPreview", "feature.ukpt.ui.UkptScreenKt#UkptScreenPreview"),
                GoldenPathClaim("feature/ukpt/ui/UkptEmptyPreview", "feature.ukpt.ui.UkptScreenKt#UkptEmptyPreview"),
            ),
        )

        assertNull(message)
    }

    @Test
    fun collidingPathsNameEveryClaimant() {
        val message = goldenPathCollisionMessage(
            listOf(
                GoldenPathClaim("feature/ukpt/ui/Preview", "feature.ukpt.ui.AKt#Preview"),
                GoldenPathClaim("feature/ukpt/ui/Preview", "feature.ukpt.ui.BKt#Preview"),
                GoldenPathClaim("feature/ukpt/ui/Other", "feature.ukpt.ui.CKt#Other"),
            ),
        )

        requireNotNull(message)
        assertTrue(message.contains("1 path(s)"))
        assertTrue(message.contains("feature/ukpt/ui/Preview.png"))
        assertTrue(message.contains("feature.ukpt.ui.AKt#Preview"))
        assertTrue(message.contains("feature.ukpt.ui.BKt#Preview"))
        // The non-colliding path is not part of the failure.
        assertTrue(!message.contains("feature.ukpt.ui.CKt#Other"))
    }

    @Test
    fun everyCollidingPathIsReported() {
        val message = goldenPathCollisionMessage(
            listOf(
                GoldenPathClaim("a/One", "a.AKt#One"),
                GoldenPathClaim("a/One", "a.BKt#One"),
                GoldenPathClaim("b/Two", "b.CKt#Two"),
                GoldenPathClaim("b/Two", "b.DKt#Two"),
            ),
        )

        requireNotNull(message)
        assertTrue(message.contains("2 path(s)"))
        assertTrue(message.contains("a/One.png"))
        assertTrue(message.contains("b/Two.png"))
    }
}
