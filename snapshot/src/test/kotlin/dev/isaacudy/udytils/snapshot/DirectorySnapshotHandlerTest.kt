package dev.isaacudy.udytils.snapshot

import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.TestName
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File
import java.nio.file.Files
import java.util.Date
import javax.imageio.ImageIO
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DirectorySnapshotHandlerTest {

    private lateinit var root: File
    private lateinit var snapshots: File
    private lateinit var reports: File
    private lateinit var failures: File
    private val originalProperties = mutableMapOf<String, String?>()

    @Before
    fun setUp() {
        root = Files.createTempDirectory("directory-snapshot-handler").toFile()
        snapshots = File(root, "snapshots")
        reports = File(root, "reports")
        failures = File(root, "failures")

        setProperty("paparazzi.snapshot.dir", snapshots.absolutePath)
        setProperty("paparazzi.report.dir", reports.absolutePath)
        setProperty("paparazzi.failures.dir", failures.absolutePath)
        setProperty("paparazzi.test.record", "false")
        setProperty("paparazzi.test.verify", "false")
        setProperty("app.cash.paparazzi.maxPercentDifferenceDefault", null)
    }

    @After
    fun tearDown() {
        originalProperties.forEach { (name, value) ->
            if (value == null) System.clearProperty(name) else System.setProperty(name, value)
        }
        root.deleteRecursively()
    }

    @Test
    fun plainTestWritesReportWithoutChangingGolden() {
        render(solidImage(0xFF000000.toInt()))

        assertFalse(goldenFile().exists())
        assertTrue(File(reports, "directory-snapshots/example/Preview.png").exists())
    }

    @Test
    fun recordWritesGolden() {
        setProperty("paparazzi.test.record", "true")

        render(twoColorImage())

        assertTrue(goldenFile().exists())
    }

    @Test
    fun recordFailsOnUniformRenderAndLeavesGoldenUnwritten() {
        setProperty("paparazzi.test.record", "true")

        val failure = runCatching { render(solidImage(0xFF00FF00.toInt())) }.exceptionOrNull()

        assertTrue(failure is AssertionError)
        assertTrue(failure?.message.orEmpty().contains("Blank render"))
        assertFalse(goldenFile().exists())
    }

    @Test
    fun recordGuardDisabledWritesUniformGolden() {
        setProperty("paparazzi.test.record", "true")

        render(solidImage(0xFF00FF00.toInt()), DirectorySnapshotHandler(failOnUniformRender = false))

        assertTrue(goldenFile().exists())
    }

    @Test
    fun verifyUsesPaparazziPercentageUnits() {
        goldenFile().apply {
            requireNotNull(parentFile).mkdirs()
            ImageIO.write(solidImage(0xFF000000.toInt()), "PNG", this)
        }
        setProperty("paparazzi.test.verify", "true")
        setProperty("app.cash.paparazzi.maxPercentDifferenceDefault", "40.0")
        render(solidImage(0xFFFF0000.toInt()))

        setProperty("app.cash.paparazzi.maxPercentDifferenceDefault", "30.0")

        val failure = runCatching { render(solidImage(0xFFFF0000.toInt())) }.exceptionOrNull()

        assertTrue(failure is AssertionError)
        assertTrue(failure?.message.orEmpty().contains("tolerance 30.0000%"))
    }

    @Test
    fun verifyFailsWhenGoldenIsMissing() {
        setProperty("paparazzi.test.verify", "true")

        val failure = runCatching { render(solidImage(0xFF000000.toInt())) }.exceptionOrNull()

        assertTrue(failure is AssertionError)
        assertTrue(failure?.message.orEmpty().contains("Missing golden image"))
    }

    private fun render(image: BufferedImage, handler: DirectorySnapshotHandler = DirectorySnapshotHandler()) {
        handler
            .newFrameHandler(
                snapshot = Snapshot(
                    name = "example/Preview",
                    testName = TestName("dev.isaacudy.udytils.snapshot", "HandlerTest", "snapshot"),
                    timestamp = Date(0),
                ),
                frameCount = 1,
                fps = -1,
            )
            .use { it.handle(image) }
    }

    private fun goldenFile(): File = File(snapshots, "images/example/Preview.png")

    private fun solidImage(argb: Int): BufferedImage = BufferedImage(1, 1, TYPE_INT_ARGB).apply {
        setRGB(0, 0, argb)
    }

    /** A 2x1 image with two distinct pixels, so the blank-render guard treats it as real content. */
    private fun twoColorImage(): BufferedImage = BufferedImage(2, 1, TYPE_INT_ARGB).apply {
        setRGB(0, 0, 0xFF000000.toInt())
        setRGB(1, 0, 0xFFFFFFFF.toInt())
    }

    private fun setProperty(name: String, value: String?) {
        if (name !in originalProperties) originalProperties[name] = System.getProperty(name)
        if (value == null) System.clearProperty(name) else System.setProperty(name, value)
    }
}
