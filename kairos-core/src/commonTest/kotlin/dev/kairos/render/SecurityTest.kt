package dev.kairos.render

import dev.kairos.markdown.renderMarkdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SecurityTest {

    @Test
    fun unsafeSchemesAreRejected() {
        for (value in listOf(
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox(1)",
        )) {
            assertFailsWith<IllegalArgumentException>("expected rejection of " + value) {
                requireSafeUrl("href", value)
            }
        }
    }

    @Test
    fun safeUrlsAreAccepted() {
        for (value in listOf(
            "https://example.com/a?b=c",
            "http://example.com",
            "/relative/path",
            "./local",
            "../up",
            "#anchor",
            "mailto:a@b.c",
            "tel:+123",
            "//cdn.example.com/x.js",
            "example.com/no-scheme",
        )) {
            assertEquals(value, requireSafeUrl("href", value))
        }
    }

    @Test
    fun theAttributeDelegateRejectsUnsafeHrefs() {
        assertFailsWith<IllegalArgumentException> {
            el("a") { href = "javascript:alert(1)" }
        }
    }

    @Test
    fun rawMapAccessCannotSmuggleUnsafeUrlsPastSerialisation() {
        val anchor = el("a") { text("x") }
        anchor.attributes["href"] = "javascript:alert(1)"
        assertFailsWith<IllegalArgumentException> { renderFragment(anchor) }
    }

    @Test
    fun rawHtmlIsOnlyAvailableThroughTheExplicitEntryPoint() {
        val node = unsafeRawHtml("<b>trusted</b>")
        val html = renderFragment(el("div") { add(node) })
        assertTrue(html.contains("<b>trusted</b>"), "raw html was escaped or dropped: $html")
    }

    @Test
    fun markdownDowngradesUnsafeLinksToTheirLabel() {
        val nodes = renderMarkdown("read [this](javascript:alert(1)) now")
        val html = renderFragment(el("p") { nodes.forEach { add(it) } })
        assertTrue(!html.contains("javascript:"), "unsafe link leaked into markdown output: $html")
        assertTrue(!html.contains("<a"), "unsafe link should degrade to plain text: $html")
        assertTrue(html.contains("this"), "link label should survive: $html")
    }

    @Test
    fun markdownKeepsSafeLinks() {
        val nodes = renderMarkdown("[site](https://example.com)")
        val html = renderFragment(el("p") { nodes.forEach { add(it) } })
        assertTrue(html.contains("<a href=\"https://example.com\">site</a>"), "safe link lost: $html")
    }
}
