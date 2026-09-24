package dev.isaacudy.udytils.htmlsnapshot

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HtmlSnapshotsTest {

    private val directory: File = Files.createTempDirectory("html-snapshots").toFile()

    @Test
    fun `attribute order and whitespace do not affect the normalised form`() {
        assertEquals(
            normalizeHtml("""<div id="a" class="b"><p>text</p></div>"""),
            normalizeHtml("""<div  class="b"   id="a">
                <p>text</p>
            </div>"""),
        )
    }

    @Test
    fun `documents keep their head, fragments print only their elements`() {
        val document = normalizeHtml("<!DOCTYPE html><html><head><title>t</title></head><body><p>x</p></body></html>")
        val fragment = normalizeHtml("<p>x</p>")

        assertTrue(document.startsWith("<!doctype html>"))
        assertTrue("<title>t</title>" in document)
        assertEquals("<p>x</p>\n", fragment)
    }

    @Test
    fun `scrubbers replace volatile attribute values`() {
        val normalised = normalizeHtml("""<input name="csrf" value="123">""", listOf(HtmlScrubber.attribute("value")))
        assertEquals("""<input name="csrf" value="[scrubbed]">""" + "\n", normalised)
    }

    @Test
    fun `a recorded golden verifies, a different render fails`() {
        HtmlSnapshots(directory, record = true).assertMatches("Page/default", "<p>x</p>")

        HtmlSnapshots(directory, record = false).assertMatches("Page/default", "<p>x</p>")
        assertFailsWith<AssertionError> {
            HtmlSnapshots(directory, record = false).assertMatches("Page/default", "<p>y</p>")
        }
    }

    @Test
    fun `a missing golden fails verification`() {
        val error = assertFailsWith<AssertionError> {
            HtmlSnapshots(directory, record = false).assertMatches("Page/missing", "<p>x</p>")
        }
        assertTrue(HtmlSnapshots.RECORD_PROPERTY in error.message.orEmpty())
    }
}
