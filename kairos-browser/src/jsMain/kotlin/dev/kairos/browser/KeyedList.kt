package dev.kairos.browser

import dev.kairos.reactive.Disposable
import dev.kairos.reactive.Signal
import dev.kairos.reactive.effect
import org.w3c.dom.Element

/**
 * Keyed list rendering: DOM nodes are reused by key, moved into the new order and only created or
 * removed at the edges. Unlike [bindChildren], an update does not rebuild the whole list, so
 * focus, selection and mounted behaviours survive a change.
 *
 * [update] (optional) is called for reused nodes when the item with the same key changed; the
 * caller can refresh text/attributes in place.
 */
fun <T> bindKeyedChildren(
    container: Element,
    signal: Signal<List<T>>,
    key: (T) -> String,
    render: (T) -> dev.kairos.render.Node,
    update: ((Element, T) -> Unit)? = null,
): Disposable {
    val known = HashMap<String, Element>()
    val seenValues = HashMap<String, T>()

    fun apply(items: List<T>) {
        val next = LinkedHashMap<String, Element>()
        val ordered = ArrayList<Element>(items.size)
        items.forEach { item ->
            val itemKey = key(item)
            val existing = known[itemKey]
            if (existing != null) {
                if (update != null && seenValues[itemKey] != item) update(existing, item)
                next[itemKey] = existing
                ordered += existing
            } else {
                val dom = render(item).toDom() as? Element
                if (dom != null) {
                    dom.setAttribute("data-kairos-key", itemKey)
                    next[itemKey] = dom
                    ordered += dom
                }
            }
            seenValues[itemKey] = item
        }
        // Remove nodes that are no longer part of the list.
        known.keys.filter { it !in next }.forEach { stale ->
            known[stale]?.let { element -> element.parentNode?.removeChild(element) }
            seenValues.remove(stale)
        }
        // Place every node at its new index (insertBefore moves existing nodes).
        ordered.forEachIndexed { index, element ->
            val current = container.children.item(index)
            if (current !== element) container.insertBefore(element, current)
        }
        known.clear()
        known.putAll(next)
    }

    apply(signal.peek())
    return effect { apply(signal.current) }
}
