package dev.kairos.runtime

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClassNameTest {

    private val a = Declaration("color", "#111111")
    private val b = Declaration("padding", "24px")

    @Test
    fun classNamesAreDeterministicForTheSameDeclarations() {
        kotlin.test.assertEquals(classNameFor(Layer.Base, listOf(a, b)), classNameFor(Layer.Base, listOf(a, b)))
    }

    @Test
    fun declarationOrderDoesNotChangeTheName() {
        kotlin.test.assertEquals(classNameFor(Layer.Base, listOf(a, b)), classNameFor(Layer.Base, listOf(b, a)))
    }

    @Test
    fun theLayerIsPartOfTheIdentity() {
        kotlin.test.assertNotEquals(classNameFor(Layer.Base, listOf(a)), classNameFor(Layer.Override, listOf(a)))
        kotlin.test.assertNotEquals(classNameFor(Layer.Reset, listOf(a)), classNameFor(Layer.Variant, listOf(a)))
    }

    @Test
    fun theNameShapeIsStable() {
        val name = classNameFor(Layer.Base, listOf(a))
        assertTrue(name.matches(Regex("kb[0-9a-f]{12}")), "unexpected class name: $name")
    }

    @Test
    fun differentValuesProduceDifferentNames() {
        kotlin.test.assertNotEquals(
            classNameFor(Layer.Base, listOf(Declaration("color", "#111111"))),
            classNameFor(Layer.Base, listOf(Declaration("color", "#222222"))),
        )
    }

    @Test
    fun theRuntimeCssBlockUsesTheSameAlgorithm() {
        val block = css(Layer.Base) {
            color("#111111")
            padding("24px")
        }
        kotlin.test.assertEquals(classNameFor(Layer.Base, listOf(a, b)), block)
    }

    @Test
    fun stylesComposesWithoutDuplicatesAndKeepsFirstOccurrenceOrder() {
        val first = classNameFor(Layer.Base, listOf(a))
        val second = classNameFor(Layer.Base, listOf(b))
        kotlin.test.assertEquals("$first $second", styles(first, second))
        kotlin.test.assertEquals("$second $first", styles(second, first))
        kotlin.test.assertEquals("$first $second", styles("$first $second", first))
        kotlin.test.assertEquals("", styles())
    }

    @Test
    fun declarationsRejectStylesheetBreakingInput() {
        assertFailsWith<IllegalArgumentException> {
            css(Layer.Base) { declaration("color", "red; }") }
        }
    }
}
