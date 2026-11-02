package dev.kairos.browser

import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * Minimal modal behaviour with the accessibility bits that are easy to forget:
 *
 * - focus moves into the dialog when it opens;
 * - Tab and Shift+Tab wrap inside the dialog (no escaping into the page behind it);
 * - Escape closes it;
 * - focus returns to the element that opened it.
 *
 * The dialog is shown/hidden with a class, so all visual rules stay in the compiled stylesheet.
 */
fun installModal(
    trigger: Element,
    modal: Element,
    hiddenClass: String,
    initialFocus: String? = null,
    closeSelector: String? = null,
) {
    var previouslyFocused: Element? = null

    fun focusables(): List<HTMLElement> {
        val nodes = modal.querySelectorAll(
            "button, [href], input, textarea, select, [tabindex]:not([tabindex='-1'])",
        )
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? HTMLElement }
    }

    fun open() {
        previouslyFocused = document.activeElement as? Element
        modal.classList.remove(hiddenClass)
        val preferred = initialFocus?.let { document.querySelector(it) as? HTMLElement }
        (preferred ?: focusables().firstOrNull())?.focus()
    }

    fun close() {
        modal.classList.add(hiddenClass)
        (previouslyFocused as? HTMLElement)?.focus()
        previouslyFocused = null
    }

    trigger.addEventListener("click", { open() })

    closeSelector?.let { selector ->
        (modal.querySelector(selector) as? HTMLElement)?.addEventListener("click", { close() })
    }

    modal.addEventListener("keydown", { event ->
        val keyboard = event as? org.w3c.dom.events.KeyboardEvent ?: return@addEventListener
        when (keyboard.key) {
            "Escape" -> {
                keyboard.preventDefault()
                close()
            }

            "Tab" -> {
                val list = focusables()
                if (list.isEmpty()) return@addEventListener
                val first = list.first()
                val last = list.last()
                val active = document.activeElement
                if (keyboard.shiftKey && active == first) {
                    keyboard.preventDefault()
                    last.focus()
                } else if (!keyboard.shiftKey && active == last) {
                    keyboard.preventDefault()
                    first.focus()
                }
            }
        }
    })
}
