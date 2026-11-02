package dev.kairos.browser

import dev.kairos.reactive.Disposable
import dev.kairos.reactive.Owner
import dev.kairos.reactive.Signal
import kotlinx.browser.document
import org.w3c.dom.Element

/**
 * Declarative mounting for server rendered (or statically rendered) markup.
 *
 * The server owns the HTML; the client only attaches behaviour to elements that opt in with
 * `data-kairos-mount="<name>"`. Mounting is idempotent per element, and [autoMount] re-scans the
 * document after every htmx swap, so fragments coming from the server get their behaviour
 * without a single hand written `byId` call.
 */

/** Attribute that names the behaviour to run on an element. */
const val MOUNT_ATTRIBUTE = "data-kairos-mount"

/** Attribute set once an element has been mounted (idempotency marker). */
const val MOUNTED_ATTRIBUTE = "data-kairos-mounted"

/** The scope handed to a mount handler: the element plus its owned effects. */
class MountScope internal constructor(
    val element: Element,
    internal val owner: Owner,
) {
    fun effect(block: () -> Unit): Disposable = owner.effect(block)

    fun <T> computed(block: () -> T): Signal<T> = owner.computed(block)

    fun on(event: String, handler: (org.w3c.dom.events.Event) -> Unit) {
        element.addEventListener(event, handler)
    }
}

private val handlers = LinkedHashMap<String, (MountScope) -> Unit>()
private val owners = HashMap<Element, Owner>()

/** Registers the behaviour named [name]; the last registration wins. */
fun onMount(name: String, handler: (MountScope) -> Unit) {
    handlers[name] = handler
}

/** Runs the registered behaviour for every not-yet-mounted element under [root]. */
fun mount(root: Element) {
    if (root.getAttribute(MOUNT_ATTRIBUTE).orEmpty().isNotEmpty()) mountOne(root)
    val nodes = root.querySelectorAll("[$MOUNT_ATTRIBUTE]")
    for (index in 0 until nodes.length) {
        val element = nodes.item(index) as? Element ?: continue
        mountOne(element)
    }
}

private fun mountOne(element: Element) {
    if (element.getAttribute(MOUNTED_ATTRIBUTE).orEmpty().isNotEmpty()) return
    val name = element.getAttribute(MOUNT_ATTRIBUTE).orEmpty()
    if (name.isEmpty()) return
    val handler = handlers[name] ?: return
    val owner = Owner()
    owners[element] = owner
    element.setAttribute(MOUNTED_ATTRIBUTE, name)
    try {
        handler(MountScope(element, owner))
    } catch (failure: Throwable) {
        element.removeAttribute(MOUNTED_ATTRIBUTE)
        owners.remove(element)
        owner.dispose()
        println("[kairos] mount '$name' failed: " + (failure.message ?: failure::class.simpleName))
    }
}

/** Disposes every behaviour mounted on [root] or its descendants. */
fun unmount(root: Element) {
    val detached = owners.keys.filter { it === root || root.contains(it) }
    detached.forEach { element ->
        owners.remove(element)?.dispose()
        element.removeAttribute(MOUNTED_ATTRIBUTE)
    }
}

/** Disposes behaviours whose element is no longer in the document. */
fun pruneDetached() {
    val body = document.body ?: return
    val stale = owners.keys.filter { element -> !body.contains(element) }
    stale.forEach { element ->
        owners.remove(element)?.dispose()
    }
}

/**
 * Mounts the current document and keeps it mounted across htmx swaps: before a swap the replaced
 * subtree is unmounted (so its effects are disposed), after the swap the new markup is scanned.
 */
fun autoMount() {
    val body = document.body ?: return
    mount(body)
    body.addEventListener("htmx:beforeSwap", {
        pruneDetached()
    })
    body.addEventListener("htmx:afterSwap", {
        pruneDetached()
        mount(body)
    })
}

/** Number of currently mounted elements; useful for leak tests. */
fun mountedCount(): Int = owners.size
