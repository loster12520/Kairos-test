package dev.kairos.browser

import dev.kairos.reactive.Disposable
import dev.kairos.reactive.Signal
import dev.kairos.reactive.effect
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/**
 * DOM bindings for the reactive core.
 *
 * Every binding is one [effect]: it reads the signal, writes exactly one DOM property, and does
 * nothing else. There is no diffing and no re-render - the cost of a state change is the cost of
 * the one property write it causes.
 */

/** Writes the signal value as text content. */
fun bindText(element: Element, signal: Signal<*>): Disposable {
    element.textContent = signal.peek().toString()
    return effect { element.textContent = signal.current.toString() }
}

/**
 * Maps a signal to a class name and swaps it when the signal changes.
 *
 * This is the companion of the compile-time folding: the class names are still compile time
 * constants, only the choice moves.
 */
fun <T> bindClassName(element: Element, signal: Signal<T>, render: (T) -> String): Disposable {
    var applied = render(signal.peek())
    element.className = applied
    return effect {
        val next = render(signal.current)
        if (next != applied) {
            swapClass(element, applied, next)
            applied = next
        }
    }
}

/** Writes a signal into an attribute; an empty value removes it. */
fun bindAttribute(element: Element, name: String, signal: Signal<*>): Disposable {
    fun apply(value: Any?) {
        val text = value?.toString().orEmpty()
        if (text.isEmpty()) element.removeAttribute(name) else element.setAttribute(name, text)
    }
    apply(signal.peek())
    return effect { apply(signal.current) }
}

/** Rebuilds the children of [container] from a list signal when the list changes. */
fun bindChildren(container: Element, signal: Signal<List<dev.kairos.render.Node>>): Disposable {
    fun apply(nodes: List<dev.kairos.render.Node>) {
        while (container.firstChild != null) container.removeChild(container.firstChild!!)
        nodes.forEach { container.appendChild(it.toDom()) }
    }
    apply(signal.peek())
    return effect { apply(signal.current) }
}

fun onClick(element: Element?, handler: () -> Unit) {
    element?.addEventListener("click", { handler() })
}

fun onInput(element: Element?, handler: (String) -> Unit) {
    element?.addEventListener("input", { event ->
        val target = event.target as? org.w3c.dom.HTMLInputElement
        handler(target?.value.orEmpty())
    })
}

fun onSubmit(element: Element?, handler: (Event) -> Unit) {
    element?.addEventListener("submit", { event ->
        event.preventDefault()
        handler(event)
    })
}

/** Reads the current value of an input element. */
fun inputValue(id: String): String =
    (document.getElementById(id) as? org.w3c.dom.HTMLInputElement)?.value.orEmpty()

/** Clears an input element. */
fun clearInput(id: String) {
    (document.getElementById(id) as? org.w3c.dom.HTMLInputElement)?.value = ""
}
