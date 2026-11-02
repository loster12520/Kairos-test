package dev.kairos.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FragmentTest {

    @Test
    fun aGroupEmitsItsChildrenWithoutAWrapperElement() {
        val node = group {
            span { "a".text }
            span { "b".text }
        }
        val html = renderFragment(node)
        assertEquals(2, html.lines().count { it.trim().startsWith("<span") })
        assertTrue(!html.contains("<fragment"), "the builder leaked its internal tag: $html")
    }

    @Test
    fun componentsArePlainFunctionsReturningNodes() {
        fun badge(label: String, tone: String): Node = group {
            span { style = tone; label.text }
        }
        val card = el("div") {
            add(badge("alpha", "kba"))
            add(badge("beta", "kbb"))
        }
        val html = renderFragment(card)
        assertTrue(html.contains("<span class=\"kba\">alpha</span>"), html)
        assertTrue(html.contains("<span class=\"kbb\">beta</span>"), html)
    }

    @Test
    fun aSlotIsJustAParameter() {
        fun panel(content: Node): Node = el("section") { add(content) }
        val html = renderFragment(panel(group { span { "slot".text } }))
        assertTrue(html.contains("<section>"), html)
        assertTrue(html.contains("<span>slot</span>"), html)
    }

    @Test
    fun groupDoesNotBreakTextOrdering() {
        val node = el("p") {
            add(group {
                add(TextNode("one "))
                add(TextNode("two"))
            })
            add(TextNode(" three"))
        }
        val html = renderFragment(node)
        for (part in listOf("one", "two", "three")) {
            assertTrue(html.contains(part), "lost text '" + part + "': " + html)
        }
    }
}
