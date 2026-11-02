package dev.kairos.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StyleConditionTest {

    private val color = Declaration("color", "#111111")
    private val background = Declaration("background-color", "#222222")

    @Test
    fun aConditionChangesTheClassName() {
        val plain = classNameFor(Layer.Base, listOf(color))
        val hover = classNameFor(Layer.Base, listOf(color), listOf(StyleCondition.PseudoClass("hover")))
        assertNotEquals(plain, hover)
        assertEquals(hover, classNameFor(Layer.Base, listOf(color), listOf(StyleCondition.PseudoClass("hover"))))
    }

    @Test
    fun differentConditionsProduceDifferentNames() {
        val declarations = listOf(color)
        val hover = classNameFor(Layer.Base, declarations, listOf(StyleCondition.PseudoClass("hover")))
        val active = classNameFor(Layer.Base, declarations, listOf(StyleCondition.PseudoClass("active")))
        val media = classNameFor(Layer.Base, declarations, listOf(StyleCondition.Media("(max-width: 600px)")))
        assertNotEquals(hover, active)
        assertNotEquals(hover, media)
    }

    @Test
    fun theConditionSuffixAndAtRuleAreStable() {
        assertEquals(":hover", StyleCondition.PseudoClass("hover").selectorSuffix)
        assertEquals("::before", StyleCondition.PseudoElement("before").selectorSuffix)
        assertEquals("@media (max-width: 600px)", StyleCondition.Media("(max-width: 600px)").atRule)
        assertEquals(null, StyleCondition.PseudoClass("hover").atRule)
        assertEquals("p:hover", StyleCondition.PseudoClass("hover").key)
        assertEquals("m:(max-width: 600px)", StyleCondition.Media("(max-width: 600px)").key)
    }

    @Test
    fun aBlockWithConditionsReturnsOneClassPerRule() {
        KairosRuntimeRegistry.clear()
        val classes = css(Layer.Base) {
            color("#111111")
            hover { backgroundColor("#222222") }
            media("(max-width: 600px)") { padding("8px") }
        }
        val parts = classes.split(" ").filter { it.isNotEmpty() }
        assertEquals(3, parts.size, "expected base + hover + media classes, got $classes")
        parts.forEach { assertTrue(it.matches(Regex("kb[0-9a-f]{12}")), "unexpected class name: $it") }
        val conditions = KairosRuntimeRegistry.conditions()
        assertEquals(3, conditions.size)
        assertTrue(conditions.values.any { it.isEmpty() }, "base rule missing")
        assertTrue(conditions.values.any { it.singleOrNull() == StyleCondition.PseudoClass("hover") })
        assertTrue(conditions.values.any { it.singleOrNull() == StyleCondition.Media("(max-width: 600px)") })
    }

    @Test
    fun aConditionOnlyBlockStillProducesAClass() {
        KairosRuntimeRegistry.clear()
        val classes = css { hover { backgroundColor("#222222") } }
        assertTrue(classes.matches(Regex("kb[0-9a-f]{12}")), "unexpected class list: $classes")
    }

    @Test
    fun nestedConditionsAccumulateInOrder() {
        KairosRuntimeRegistry.clear()
        css {
            media("(max-width: 600px)") {
                hover { backgroundColor("#222222") }
            }
        }
        val conditions = KairosRuntimeRegistry.conditions().values.single()
        assertEquals(
            listOf(StyleCondition.Media("(max-width: 600px)"), StyleCondition.PseudoClass("hover")),
            conditions,
        )
    }

    @Test
    fun invalidQueriesAreRejected() {
        var failed = false
        try {
            css { media("(max-width: 600px) { }") { color("red") } }
        } catch (failure: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed, "a query containing braces must be rejected")
    }
}
