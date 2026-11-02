package dev.kairos.reactive

/**
 * Owns a group of effects and disposes them together.
 *
 * Every piece of UI that binds behaviour (a mounted component, a list item, a form) creates one
 * owner. When that piece leaves the DOM its owner is disposed, which detaches every signal
 * subscription it made - this is the answer to "who cleans up effects?" that a virtual DOM would
 * otherwise hide.
 */
class Owner {

    private val children = ArrayList<Disposable>()
    private var disposed = false

    fun isDisposed(): Boolean = disposed

    /** Registers [this] with the owner; already disposed owners dispose the handle immediately. */
    fun <T : Disposable> T.owned(): T {
        if (disposed) {
            dispose()
        } else {
            children += this
        }
        return this
    }

    /** Creates an effect owned by this owner. */
    fun effect(block: () -> Unit): Disposable = dev.kairos.reactive.effect(block).owned()

    /** Creates a computed value owned by this owner. */
    fun <T> computed(block: () -> T): Signal<T> {
        val initial = block()
        val result = Signal<T>(initial)
        effect {
            val next = block()
            if (next != result.peek()) result.setAndNotify(next)
        }
        return result
    }

    /**
     * A callback based resource with an exhaustive state machine. `load` reports its result
     * through the callback; the state moves Idle -> Loading -> Success/Failure. No coroutines are
     * required, which keeps the runtime usable from plain DOM callbacks.
     */
    fun <T> resource(load: ((Result<T>) -> Unit) -> Unit): ResourceLoader<T> {
        val state = signal<Resource<T>>(Resource.Idle)
        val reload = {
            state.current = Resource.Loading
            load { result ->
                state.current = result.fold(
                    onSuccess = { Resource.Success(it) },
                    onFailure = { Resource.Failure(it) },
                )
            }
        }
        reload()
        return ResourceLoader(state, reload).owned()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        children.reversed().forEach { child ->
            try {
                child.dispose()
            } catch (failure: Throwable) {
                Reactivity.reportError(failure)
            }
        }
        children.clear()
    }
}

/** Creates an owner and runs [block] with it; dispose the returned owner when the UI goes away. */
fun owner(block: Owner.() -> Unit): Owner = Owner().apply(block)

/** The exhaustive lifecycle of an asynchronous resource. */
sealed interface Resource<out T> {
    data object Idle : Resource<Nothing>
    data object Loading : Resource<Nothing>
    data class Success<T>(val value: T) : Resource<T>
    data class Failure(val error: Throwable) : Resource<Nothing>
}

/** Handle for a resource: the read only state signal plus a manual reload. */
class ResourceLoader<T>(
    val state: Signal<Resource<T>>,
    private val reloadAction: () -> Unit,
) : Disposable {

    private var disposed = false

    fun reload() {
        if (!disposed) reloadAction()
    }

    override fun dispose() {
        disposed = true
    }
}
