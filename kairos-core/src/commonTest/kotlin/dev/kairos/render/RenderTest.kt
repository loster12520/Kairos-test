package dev.kairos.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderTest {

    @Test
    fun textAndAttributeValuesAreEscaped() {
        val page = el("div") {
            id = "a\"b"
            "<script>alert(1)</script> & more".text
        }
        val html = renderFragment(page)
        assertTrue(html.contains("id=\"a&quot;b\""), "attribute quote not escaped: $html")
        assertTrue(html.contains("&lt;script&gt;"), "text was not escaped: $html")
        assertTrue(html.contains("&amp; more"), "ampersand was not escaped: $html")
        assertTrue(!html.contains("<script>"), "raw script leaked into markup")
    }

    @Test
    fun serialisationIsDeterministic() {
        val build = {
            el("html") {
                body {
                    div {
                        className = "kb000000000000"
                        "hello".text
                    }
                }
            }
        }
        assertEquals(renderToHtml(build()), renderToHtml(build()))
    }

    @Test
    fun textOnlyElementsStayOnOneLine() {
        val html = renderFragment(el("p") { "hello".text })
        assertEquals("<p>hello</p>", html.trim())
    }

    @Test
    fun voidElementsHaveNoChildren() {
        val html = renderFragment(el("input") { type = "text" })
        assertTrue(html.trim().startsWith("<input type=\"text\">"), "unexpected void markup: $html")
    }

    @Test
    fun classListAppendsAndDropsBlanks() {
        val node = el("div") {
            cls("a", "", "b")
            cls("c")
        }
        assertEquals("a b c", node.style)
    }

    @Test
    fun assigningStyleReplacesTheWholeList() {
        val node = el("div") {
            style = "a b"
            style = "c"
        }
        assertEquals("c", node.style)
    }
}
