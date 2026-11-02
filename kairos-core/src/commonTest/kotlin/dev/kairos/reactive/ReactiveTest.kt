package dev.kairos.reactive

import kotlin.test.Test
import kotlin.test.assertEquals

class ReactiveTest {

    @Test
    fun anEffectRunsInitiallyAndOnEveryWrite() {
        val count = signal(0)
        val seen = ArrayList<Int>()
        val effect = effect { seen += count.current }
        count.current = 1
        count.current = 2
        effect.dispose()
        count.current = 3
        assertEquals(listOf(0, 1, 2), seen)
    }

    @Test
    fun writingTheSameValueDoesNotNotify() {
        val count = signal(1)
        var runs = 0
        val effect = effect {
            count.current
            runs += 1
        }
        count.current = 1
        effect.dispose()
        assertEquals(1, runs)
    }

    @Test
    fun computedRecomputesAndNotifiesOnlyOnRealChanges() {
        val base = signal(1)
        val doubled = computed { base.current * 2 }
        val seen = ArrayList<Int>()
        val effect = effect { seen += doubled.current }
        base.current = 2
        base.current = 2
        effect.dispose()
        assertEquals(4, doubled.peek())
        assertEquals(listOf(2, 4), seen)
    }

    @Test
    fun disposeDetachesTheEffectFromItsSignals() {
        val count = signal(0)
        var runs = 0
        val handle = effect {
            count.current
            runs += 1
        }
        handle.dispose()
        count.current = 10
        assertEquals(1, runs)
    }

    @Test
    fun manualObservationStopsWhenTheHandleIsDisposed() {
        val count = signal(0)
        val seen = ArrayList<Int>()
        val handle = count.observe { seen += it }
        count.current = 1
        handle.dispose()
        count.current = 2
        assertEquals(listOf(1), seen)
    }
}
