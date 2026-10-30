import dev.kairos.browser.bindClassName
import dev.kairos.browser.bindText
import dev.kairos.browser.byId
import dev.kairos.browser.onClick
import dev.kairos.reactive.signal
import kotlinx.browser.document
import kotlin.js.js

fun main() {
    println("hero=" + heroClass())
    println("theme=" + themeClass(Theme.Dark))
    println("card=" + copyCardClass())
    println("badge=" + badgeClass(true))
    println("folds=" + dev.kairos.runtime.KairosRuntimeRegistry.size())

    if (!hasDom()) return
    wire()
}

private fun hasDom(): Boolean = js("typeof document !== 'undefined'") as Boolean

/**
 * The state of the page is two signals plus a counter, and every binding is one effect.
 * No style value is read or computed here - only class names and text.
 */
private fun wire() {
    val theme = signal(Theme.Light)
    val density = signal(Density.Comfortable)
    val count = signal(0)

    document.documentElement?.let { root ->
        bindClassName(root, theme) { themeClass(it) }
    }
    byId(PAGE_MAIN)?.let { main ->
        bindClassName(main, density) { densityClass(it) }
    }
    byId("counter-value")?.let { bindText(it, count) }

    onClick(byId("counter-increment")) { count.update { it + 1 } }
    onClick(byId(THEME_TOGGLE)) {
        theme.update { if (it == Theme.Light) Theme.Dark else Theme.Light }
    }
    onClick(byId(DENSITY_TOGGLE)) {
        density.update { if (it == Density.Comfortable) Density.Compact else Density.Comfortable }
    }
}
