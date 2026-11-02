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
        // Observer callbacks route through [Batching], which deduplicates per effect inside a
        // batch. Outside a batch the effect runs immediately, exactly as before.
        observers.toList().forEach { it(next) }
    }
}

/** Creates a reactive value. */
fun <T> signal(initial: T): Signal<T> = Signal(initial)

/** Root owner that keeps module level [computed] effects alive for the lifetime of the app. */
private val rootOwner = Owner()

/**
 * A derived value. [block] runs again whenever a signal it read changes, and its own value only
 * notifies downstream observers when the computed result actually differs.
 *
 * The recomputation effect is owned by the root owner: computed values are module scoped and live
 * as long as the application does. Scoped computations should use [Owner.computed] instead.
 */
fun <T> computed(block: () -> T): Signal<T> = rootOwner.computed(block)

/** A side effect that re-runs when the signals it read change; owned by the module root. */
fun effect(block: () -> Unit): Disposable = Effect(block).also { it.run() }

/**
 * Coalesces writes: every effect queued inside the block runs once after the outermost block
 * exits, so ten signals written together still produce one DOM update per effect.
 */
object Batching {
    private var depth = 0
    private val queue = LinkedHashSet<Effect>()

    fun batch(block: () -> Unit) {
        depth += 1
        try {
            block()
        } finally {
            depth -= 1
            if (depth == 0) flush()
        }
    }

    /** Queues [effect] once per batch; effects are deduplicated by identity. */
    internal fun schedule(effect: Effect) {
        if (depth == 0) {
            effect.run()
        } else {
            queue += effect
        }
    }

    internal fun isBatching(): Boolean = depth > 0

    private fun flush() {
        while (queue.isNotEmpty()) {
            val pending = queue.toList()
            queue.clear()
            pending.forEach { effect ->
                try {
                    effect.run()
                } catch (failure: Throwable) {
                    Reactivity.reportError(failure)
                }
            }
        }
    }
}

/** Stops an effect from re-running and detaches every subscription it made. */
class Effect internal constructor(private val block: () -> Unit) : Disposable {

    private val dependencies = ArrayList<Signal<*>>()

    init {
        Reactivity.recordEffectCreated()
    }
    private var subscriptions = ArrayList<Disposable>()
    private var disposed = false

    fun run() {
        if (disposed) return
        subscriptions.forEach { it.dispose() }
        subscriptions = ArrayList()
        dependencies.clear()
        // Exceptions are isolated: one broken effect must not stop the rest of the page, and it
        // must not lose its subscriptions (otherwise it could never recover).
        try {
            Reactivity.withTracking(this) { block() }
        } catch (failure: Throwable) {
            Reactivity.reportError(failure)
        }
        dependencies.forEach { signal ->
            subscriptions += signal.observe { Batching.schedule(this) }
        }
    }

    internal fun dependOn(signal: Signal<*>) {
        if (!dependencies.contains(signal)) dependencies += signal
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        Reactivity.recordEffectDisposed()
        subscriptions.forEach { it.dispose() }
        subscriptions = ArrayList()
        dependencies.clear()
    }
}

/** Keeps track of the effect currently being evaluated. */
object Reactivity {
    private var active: Effect? = null
    private var errorHandler: (Throwable) -> Unit = { failure ->
        println("[kairos] reactive error: " + (failure.message ?: failure::class.simpleName))
    }

    var effectCount: Int = 0
        private set

    fun track(signal: Signal<*>) {
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

    /** Installs the sink for exceptions thrown by effects; returns the previous handler. */
    fun onError(handler: (Throwable) -> Unit): (Throwable) -> Unit {
        val previous = errorHandler
        errorHandler = handler
        return previous
    }

    internal fun reportError(failure: Throwable) {
        try {
            errorHandler(failure)
        } catch (ignored: Throwable) {
            // An error handler that throws must not take the runtime down.
        }
    }

    internal fun recordEffectCreated() {
        effectCount += 1
    }

    internal fun recordEffectDisposed() {
        effectCount -= 1
    }
}
