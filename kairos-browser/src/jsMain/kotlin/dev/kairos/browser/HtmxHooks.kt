package dev.kairos.browser

import kotlinx.browser.document
import kotlin.js.js

/**
 * Adds a header to every htmx request.
 *
 * htmx fires `htmx:configRequest` before each request, including boosted links and fragment
 * swaps, so one registration covers CSRF tokens, API keys and trace headers.
 */
fun htmxHeader(headerName: String, headerValue: String) {
    val body = document.body ?: return
    body.addEventListener("htmx:configRequest", { event ->
        val detail: dynamic = js("event.detail")
        detail.headers[headerName] = headerValue
    })
}

/**
 * Moves focus to the first match of [selector] after an htmx swap, and restores it to [fallback]
 * when the focused element was replaced. Use it to keep keyboard users on the fragment they just
 * triggered.
 */
fun focusAfterSwap(selector: String, fallback: String? = null) {
    val body = document.body ?: return
    body.addEventListener("htmx:afterSwap", {
        val target = document.querySelector(selector) as? org.w3c.dom.HTMLElement
        if (target != null) {
            target.focus()
        } else if (fallback != null) {
            (document.querySelector(fallback) as? org.w3c.dom.HTMLElement)?.focus()
        }
    })
}
