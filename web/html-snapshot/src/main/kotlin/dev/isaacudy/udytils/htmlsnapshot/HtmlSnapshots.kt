package dev.isaacudy.udytils.htmlsnapshot

import java.io.File
import kotlin.test.assertEquals

/**
 * Golden-file assertions over [normalizeHtml] output. Goldens live at `<directory>/<name>.html`.
 * With [record] set, [assertMatches] writes the golden instead of comparing against it.
 */
class HtmlSnapshots(
    private val directory: File = File(System.getProperty(DIRECTORY_PROPERTY) ?: DEFAULT_DIRECTORY),
    private val record: Boolean = System.getProperty(RECORD_PROPERTY).toBoolean(),
    private val scrubbers: List<HtmlScrubber> = emptyList(),
) {

    /** [name] is a `/`-separated path such as `GreetingsPage/empty`. */
    fun assertMatches(name: String, html: String) {
        require(namePattern.matches(name)) { "Snapshot name `$name` must be `/`-separated letters, digits, '.', '_' and '-'" }
        val golden = File(directory, "$name.html")
        val actual = normalizeHtml(html, scrubbers)

        if (record) {
            golden.parentFile.mkdirs()
            golden.writeText(actual)
            return
        }
        if (!golden.exists()) {
            throw AssertionError("No golden for `$name` at ${golden.path}. Record it by running the tests with -D$RECORD_PROPERTY=true.")
        }
        assertEquals(golden.readText(), actual, "HTML snapshot `$name` differs from ${golden.path}")
    }

    companion object {
        const val RECORD_PROPERTY: String = "udytils.htmlSnapshot.record"
        const val DIRECTORY_PROPERTY: String = "udytils.htmlSnapshot.dir"
        const val DEFAULT_DIRECTORY: String = "src/test/snapshots/html"

        private val namePattern = Regex("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)*")
    }
}
