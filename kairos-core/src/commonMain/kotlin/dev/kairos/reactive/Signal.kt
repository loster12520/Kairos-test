package dev.kairos.reactive

/**
 * Kairos' reactive core.
 *
 * The framework's identity is "state that can be enumerated"; reactivity is the other half of
 * that idea applied to time - one signal, one write, one precise update. There is no virtual
 * DOM and no re-render: reading a signal inside an [effect] subscribes that effect to it.
 *
 * The whole runtime is a few hundred lines on purpose. Style decisions stay compile time; this
 * layer only moves the values that genuinely change.
 */

/** Something an [effect] can be cleaned up with. */
fun interface Disposable {
    fun dispose()
}

/**
 * A single reactive value.
 *
 * Reading [current] inside a running effect records a dependency; writing it notifies exactly
 * the effects that read it.
 */
class Signal<T> internal constructor(initial: T) {

    private var value: T = initial
    private val observers = ArrayList<(T) -> Unit>()

    /** Read and, when inside an effect, subscribe to it. */
    var current: T
        get() {
            Reactivity.track(this)
            return value
        }
        set(next) = setAndNotify(next)

    /** Read without subscribing. */
    fun peek(): T = value

    fun update(transform: (T) -> T) {
        current = transform(value)
    }

    /** Manual subscription; returns a handle that stops it. */
    fun observe(observer: (T) -> Unit): Disposable {
        observers += observer
        return Disposable { observers -= observer }
    }

    internal fun setAndNotify(next: T) {
        if (next == value) return
        val previous = value
        value = next
        if (previous == next) return
        observers.toList().forEach { it(next) }
    }
}

/** Creates a reactive value. */
fun <T> signal(initial: T): Signal<T> = Signal(initial)

/**
 * A derived value. [block] runs again whenever a signal it read changes, and its own value only
 * notifies downstream observers when the computed result actually differs.
 */
fun <T> computed(block: () -> T): Signal<T> {
    val initial = block()
    val result = Signal(initial)
    effect {
        val next = block()
        if (next != result.peek()) result.setAndNotify(next)
    }
    return result
}

/** A side effect that re-runs when the signals it read change. */
fun effect(block: () -> Unit): Disposable {
    val effect = Effect(block)
    effect.run()
    return effect
}

/** Stops an effect from re-running and detaches every subscription it made. */
class Effect internal constructor(private val block: () -> Unit) : Disposable {

    private val dependencies = ArrayList<Signal<*>>()
    private var subscriptions = ArrayList<Disposable>()
    private var disposed = false

    fun run() {
        if (disposed) return
        subscriptions.forEach { it.dispose() }
        subscriptions = ArrayList()
        dependencies.clear()
        Reactivity.withTracking(this) { block() }
        dependencies.forEach { signal ->
            subscriptions += signal.observe { run() }
        }
    }

    internal fun dependOn(signal: Signal<*>) {
        if (!dependencies.contains(signal)) dependencies += signal
    }

    override fun dispose() {
        disposed = true
        subscriptions.forEach { it.dispose() }
        subscriptions = ArrayList()
        dependencies.clear()
    }
}

/** Keeps track of the effect currently being evaluated. */
object Reactivity {
    private var active: Effect? = null

    internal fun track(signal: Signal<*>) {
        active?.dependOn(signal)
    }

    internal fun <T> withTracking(effect: Effect, block: () -> T): T {
        val previous = active
        active = effect
        try {
            return block()
        } finally {
            active = previous
        }
    }
}

/**
 * Convenience for the common "exhaustive mode" case: maps a signal through a function that the
 * compiler can fold, so each state is still a static class name.
 */
fun <T> Signal<T>.map(transform: (T) -> String): Signal<String> {
    val source = this
    return computed { transform(source.current) }
}
