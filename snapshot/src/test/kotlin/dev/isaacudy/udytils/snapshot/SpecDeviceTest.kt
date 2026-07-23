package dev.isaacudy.udytils.snapshot

import com.android.resources.ScreenOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers [deviceConfigFromSpec], the pure parser behind [PreviewSnapshotCase.specDeviceConfig].
 *
 * The exact `width x height x dpi` it produces is load-bearing: a store/marketing golden is verified
 * at that pixel size, so a parsing change silently moves every marketing golden.
 */
class SpecDeviceTest {

    @Test
    fun parsesPixelSpecWithDpi() {
        val config = deviceConfigFromSpec("spec:width=1080px,height=2400px,dpi=440")

        requireNotNull(config)
        assertEquals(1080, config.screenWidth)
        assertEquals(2400, config.screenHeight)
        assertEquals(440, config.xdpi)
        assertEquals(440, config.ydpi)
        assertEquals(ScreenOrientation.PORTRAIT, config.orientation)
    }

    @Test
    fun acceptsBareNumbersAndDefaultsDpiToMdpi() {
        val config = deviceConfigFromSpec("spec:width=800,height=600")

        requireNotNull(config)
        assertEquals(800, config.screenWidth)
        assertEquals(600, config.screenHeight)
        assertEquals(160, config.xdpi)
        // Wider than tall resolves to landscape.
        assertEquals(ScreenOrientation.LANDSCAPE, config.orientation)
    }

    @Test
    fun toleratesWhitespaceAndKeyOrder() {
        val config = deviceConfigFromSpec("spec: dpi=320 , height=1000px , width=500px ")

        requireNotNull(config)
        assertEquals(500, config.screenWidth)
        assertEquals(1000, config.screenHeight)
        assertEquals(320, config.xdpi)
    }

    @Test
    fun returnsNullForNonSpecOrIncompleteDevices() {
        // Not a spec device (blank, named-device forms), or missing a required dimension.
        assertNull(deviceConfigFromSpec(""))
        assertNull(deviceConfigFromSpec("id:pixel_5"))
        assertNull(deviceConfigFromSpec("spec:width=1080px,dpi=440"))
        assertNull(deviceConfigFromSpec("spec:width=wide,height=2400px"))
    }
}
