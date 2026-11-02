package dev.kairos.blog.frontend

import dev.kairos.browser.autoMount
import dev.kairos.browser.bindClassName
import dev.kairos.browser.focusAfterSwap
import dev.kairos.browser.onMount
import dev.kairos.reactive.signal
import dev.kairos.runtime.css
import dev.kairos.runtime.display
import kotlinx.browser.document
import kotlinx.browser.localStorage
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.js.js

/**
 * The client half of the blog.
 *
 * The server owns rendering (htmx fragments); this bundle attaches behaviour through
 * `data-kairos-mount` declarations. `autoMount()` re-scans after every htmx swap, so newly
 * swapped markup is wired automatically - there is no manual `byId` bookkeeping and no revision
 * signal.
 */

private val hiddenStyle: String = css { display("none") }
private const val DARK_THEME = "dark"

/** Shared across mounts: the filter input and the list live in different subtrees. */
private val query = signal("")

fun main() {
    if (js("typeof document === 'undefined'") as Boolean) return
    val loading = js("document.readyState === 'loading'") as Boolean
    if (loading) {
        document.addEventListener("DOMContentLoaded", { start() })
    } else {
        start()
    }
}

private fun start() {
    val dark = signal(loadTheme())

    document.documentElement?.let { root ->
        bindClassName(root, dark) { isDark -> if (isDark) DARK_THEME else "" }
    }

    onMount("theme-toggle") { scope ->
        scope.on("click") {
            dark.update { !it }
            saveTheme(dark.peek())
        }
    }

    onMount("filter") { scope ->
        scope.on("input") { event ->
            query.current = (event.target as? HTMLInputElement)?.value.orEmpty()
            applyFilterToAllLists()
        }
    }

    onMount("post-list") { scope ->
        applyFilter(scope.element)
    }

    document.body?.addEventListener("htmx:afterSwap", { applyFilterToAllLists() })
    // Keyboard users stay oriented: after a fragment swap focus moves to the new title.
    focusAfterSwap("#detail-title", "#post-list")
    autoMount()
    println("blog-frontend ready")
}

private fun applyFilterToAllLists() {
    val lists = document.querySelectorAll("[data-kairos-mount=\"post-list\"]")
    for (index in 0 until lists.length) {
        (lists.item(index) as? Element)?.let { applyFilter(it) }
    }
}

private fun applyFilter(list: Element) {
    val needle = query.peek().trim().lowercase()
    val cards = list.querySelectorAll("[data-title]")
    for (index in 0 until cards.length) {
        val card = cards.item(index) as? HTMLElement ?: continue
        val title = card.getAttribute("data-title").orEmpty()
        val matches = needle.isEmpty() || title.contains(needle)
        if (matches) card.classList.remove(hiddenStyle) else card.classList.add(hiddenStyle)
    }
}

private fun loadTheme(): Boolean = runCatching { localStorage.getItem("kairos-theme") == "dark" }.getOrDefault(false)

private fun saveTheme(dark: Boolean) {
    runCatching { localStorage.setItem("kairos-theme", if (dark) "dark" else "light") }
}
