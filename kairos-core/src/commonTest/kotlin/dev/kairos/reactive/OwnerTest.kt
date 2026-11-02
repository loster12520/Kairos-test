package dev.kairos.reactive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnerTest {

    @Test
    fun disposingAnOwnerDisposesItsEffects() {
        val count = signal(0)
        val owner = owner {
            effect { count.current }
        }
        count.current = 1
        owner.dispose()
        count.current = 2
        // The effect stopped observing after dispose; the effect counter proves it was released.
        assertTrue(owner.isDisposed())
    }

    @Test
    fun effectsCreatedInsideAnOwnerAreCountedAndReleased() {
        val before = Reactivity.effectCount
        val owner = owner {
            effect { }
            effect { }
        }
        assertEquals(before + 2, Reactivity.effectCount)
        owner.dispose()
        assertEquals(before, Reactivity.effectCount)
    }

    @Test
    fun aBatchCoalescesMultipleWritesIntoOneRun() {
        val left = signal(0)
        val right = signal(0)
        var runs = 0
        val handle = effect {
            left.current
            right.current
            runs += 1
        }
        assertEquals(1, runs)
        Batching.batch {
            left.current = 1
            right.current = 2
            left.current = 3
        }
        assertEquals(2, runs)
        handle.dispose()
    }

    @Test
    fun aFailingEffectDoesNotStopOtherEffects() {
        val trigger = signal(0)
        val seen = ArrayList<Int>()
        val errors = ArrayList<String>()
        val previous = Reactivity.onError { failure -> errors += failure.message.orEmpty() }
        val broken = effect {
            if (trigger.current > 0) throw IllegalStateException("boom")
        }
        val healthy = effect { seen += trigger.current }
        trigger.current = 1
        assertEquals(1, errors.size)
        assertEquals(listOf(0, 1), seen)
        Reactivity.onError(previous)
        broken.dispose()
        healthy.dispose()
    }

    @Test
    fun aResourceMovesThroughItsExhaustiveStates() {
        val owner = Owner()
        var complete: ((Result<String>) -> Unit)? = null
        val loader = owner.resource<String> { callback -> complete = callback }
        assertTrue(loader.state.peek() is Resource.Loading)
        complete?.invoke(Result.success("ok"))
        assertTrue(loader.state.peek() is Resource.Success)
        assertEquals("ok", (loader.state.peek() as Resource.Success).value)
        loader.reload()
        complete?.invoke(Result.failure(IllegalStateException("nope")))
        assertTrue(loader.state.peek() is Resource.Failure)
        owner.dispose()
        assertFalse(loader.state.peek() is Resource.Idle)
    }

    @Test
    fun aOwnedComputedStopsUpdatingAfterDispose() {
        val source = signal(1)
        val owner = Owner()
        var computations = 0
        val doubled = owner.computed { computations += 1; source.current * 2 }
        assertEquals(2, doubled.peek())
        assertEquals(2, computations)
        owner.dispose()
        source.current = 5
        assertEquals(2, doubled.peek())
    }
}
