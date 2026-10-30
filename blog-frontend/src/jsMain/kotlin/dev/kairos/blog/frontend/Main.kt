package dev.kairos.blog.frontend

import dev.kairos.browser.bindClassName
import dev.kairos.browser.byId
import dev.kairos.browser.onClick
import dev.kairos.browser.onInput
import dev.kairos.reactive.effect
import dev.kairos.reactive.signal
import dev.kairos.runtime.css
import dev.kairos.runtime.display
import kotlinx.browser.document
import kotlinx.browser.localStorage
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import kotlin.js.js

/**
 * The client half of the blog.
 *
 * The server owns rendering (htmx fragments); this bundle only adds the two interactions that
 * would otherwise need a round trip: filtering the list as you type, and switching the theme.
 * Both are signals plus one effect each, and neither recomputes a style value.
 */

private val hiddenStyle: String = css { display("none") }
/* Theme colours live in theme.css as variables; the toggle only flips a class on <html>. */
private val darkTheme: String = "dark"

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
    val query = signal("")
    /** Bumped on every htmx swap so the filter re-applies to the new DOM. */
    val revision = signal(0)

    document.documentElement?.let { root ->
        bindClassName(root, dark) { isDark -> if (isDark) darkTheme else "" }
    }
    onClick(byId("theme-toggle")) {
        dark.update { !it }
        saveTheme(dark.peek())
    }
    onInput(byId("filter")) { query.current = it }

    document.body?.addEventListener("htmx:afterSwap", { revision.update { it + 1 } })

    effect {
        val needle = query.current.trim().lowercase()
        revision.current
        val cards = document.querySelectorAll("#post-list [data-title]")
        for (index in 0 until cards.length) {
            val card = cards.item(index) as? HTMLElement ?: continue
            val title = card.getAttribute("data-title").orEmpty()
            val matches = needle.isEmpty() || title.contains(needle)
            if (matches) card.classList.remove(hiddenStyle) else card.classList.add(hiddenStyle)
        }
    }

    println("blog-frontend ready")
}

private fun loadTheme(): Boolean = runCatching { localStorage.getItem("kairos-theme") == "dark" }.getOrDefault(false)

private fun saveTheme(dark: Boolean) {
    runCatching { localStorage.setItem("kairos-theme", if (dark) "dark" else "light") }
}
