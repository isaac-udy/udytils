package dev.isaacudy.udytils.permissions

import dev.isaacudy.udytils.permissions.IosLocationPermissionState.Authorization
import dev.isaacudy.udytils.permissions.IosLocationPermissionState.Request
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosLocationPermissionStateTest {
    private val background = Permission.Location(requirePrecise = true, requireBackground = true)

    @Test
    fun firstRequestUsesWhenInUseBeforeAlways() {
        val first = IosLocationPermissionState(Authorization.NotDetermined, precise = false)
        assertEquals(Request.WhenInUse, first.request(background))
        assertFalse(first.grants(background))

        val foregroundGranted = first.copy(authorization = Authorization.WhenInUse, precise = true)
        assertEquals(Request.Always, foregroundGranted.request(background))
        assertFalse(foregroundGranted.grants(background))

        val alwaysGranted = foregroundGranted.copy(authorization = Authorization.Always)
        assertEquals(Request.None, alwaysGranted.request(background))
        assertTrue(alwaysGranted.grants(background))
    }

    @Test
    fun settingsDowngradeRevokesBackgroundGrantAndOffersRecovery() {
        val granted = IosLocationPermissionState(Authorization.Always, precise = true)
        assertTrue(granted.grants(background))
        for (authorization in listOf(Authorization.Denied, Authorization.Restricted)) {
            val revoked = granted.copy(authorization = authorization)
            assertFalse(revoked.grants(background))
            assertEquals(Request.Settings, revoked.request(background))
        }
        val foregroundOnly = granted.copy(authorization = Authorization.WhenInUse)
        assertFalse(foregroundOnly.grants(background))
        assertEquals(Request.Always, foregroundOnly.request(background))
        assertTrue(foregroundOnly.grants(background.copy(requireBackground = false)))
    }

    @Test
    fun reducedAccuracyRequiresSettingsEvenWithAlwaysAuthorization() {
        val reduced = IosLocationPermissionState(Authorization.Always, precise = false)
        assertFalse(reduced.grants(background))
        assertEquals(Request.Settings, reduced.request(background))
        assertTrue(reduced.grants(background.copy(requirePrecise = false)))
        assertTrue(reduced.copy(precise = true).grants(background))
    }
}
