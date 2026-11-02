package dev.kairos.browser

import dev.kairos.render.ElementNode
import dev.kairos.render.Node
import dev.kairos.render.RawHtml
import dev.kairos.render.TextNode
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * The browser half of Kairos.
 *
 * The static site is rendered by the compiler / server; this module only wires behaviour onto
 * markup that already exists. Nothing here computes a style value.
 */

/** Finds an element by the @@data-kairos-id@@ attribute the page was rendered with. */
fun byId(id: String): Element? = document.querySelector("[data-kairos-id=\"$id\"]")

/** Replaces one class group with another: the whole runtime cost of a mode switch. */
fun swapClass(element: Element, remove: String, add: String) {
    val removing = remove.split(" ").filter { it.isNotBlank() }.toSet()
    val kept = element.className.split(" ").filter { it.isNotBlank() && it !in removing }.toMutableList()
    add.split(" ").filter { it.isNotBlank() }.forEach { if (it !in kept) kept += it }
    element.className = kept.joinToString(" ")
}

/** Toggles a single class and returns the new state. */
fun toggleClass(element: Element, name: String): Boolean {
    val has = element.className.split(" ").contains(name)
    if (has) {
        element.className = element.className.split(" ").filter { it.isNotBlank() && it != name }.joinToString(" ")
    } else {
        element.className = (element.className + " " + name).trim()
    }
    return !has
}

fun on(element: Element?, event: String, handler: () -> Unit) {
    element?.addEventListener(event, { handler() })
}

/** Builds real DOM from the shared node model, for client side rendering. */
fun Node.toDom(): org.w3c.dom.Node = when (this) {
    is TextNode -> document.createTextNode(value)
    is RawHtml -> {
        val template = document.createElement("template") as org.w3c.dom.HTMLTemplateElement
        template.innerHTML = html
        template.content
    }
    is dev.kairos.render.FragmentNode -> {
        val fragment = document.createDocumentFragment()
        children.forEach { fragment.appendChild(it.toDom()) }
        fragment
    }
    is ElementNode -> {
        val element = document.createElement(tag)
        attributes.forEach { (name, value) -> element.setAttribute(name, value) }
        children.forEach { element.appendChild(it.toDom()) }
        element
    }
}

/** Replaces the content of @@container@@ with the rendered tree. */
fun ElementNode.mountInto(container: Element) {
    while (container.firstChild != null) container.removeChild(container.firstChild!!)
    container.appendChild(toDom())
}

/** Replaces inner HTML from a rendered fragment (used when the server sends HTML). */
fun setHtml(element: Element, html: String) {
    (element as? HTMLElement)?.innerHTML = html
}

fun elementById(id: String): Element? = document.getElementById(id)
