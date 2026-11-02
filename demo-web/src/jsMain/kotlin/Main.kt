import dev.kairos.browser.bindClassName
import dev.kairos.browser.bindKeyedChildren
import dev.kairos.browser.bindText
import dev.kairos.browser.byId
import dev.kairos.browser.elementById
import dev.kairos.browser.installModal
import dev.kairos.browser.onClick
import dev.kairos.reactive.signal
import dev.kairos.render.el
import dev.kairos.runtime.css
import dev.kairos.runtime.padding
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

    // Modal: focus in, Tab trap, Escape to close, focus restored to the trigger.
    byId("modal-open")?.let { trigger ->
        elementById("shortcuts-modal")?.let { modal ->
            installModal(
                trigger = trigger,
                modal = modal,
                hiddenClass = modalHiddenStyle,
                initialFocus = "#modal-close",
                closeSelector = "#modal-close",
            )
        }
    }

    // Keyed list: adding an item reuses the existing DOM nodes (focus/scroll survive).
    byId("keyed-list")?.let { list ->
        val itemStyle: String = css { padding("6px 10px") }
        val items = signal(listOf("Alpha", "Beta"))
        bindKeyedChildren(
            container = list,
            signal = items,
            key = { it },
            render = { label -> el("li") { style = itemStyle; label.text } },
        )
        onClick(byId("keyed-add")) {
            items.update { current -> current + ("Item " + (current.size + 1)) }
        }
    }
}
