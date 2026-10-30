import dev.kairos.feature.statsPanel
import dev.kairos.render.ElementNode
import dev.kairos.render.attribute
import dev.kairos.render.body
import dev.kairos.render.button
import dev.kairos.render.charset
import dev.kairos.render.content
import dev.kairos.render.div
import dev.kairos.render.el
import dev.kairos.render.footer
import dev.kairos.render.h1
import dev.kairos.render.h2
import dev.kairos.render.head
import dev.kairos.render.header
import dev.kairos.render.href
import dev.kairos.render.html as htmlRoot
import dev.kairos.render.lang
import dev.kairos.render.link
import dev.kairos.render.main
import dev.kairos.render.meta
import dev.kairos.render.name
import dev.kairos.render.p
import dev.kairos.render.rel
import dev.kairos.render.script
import dev.kairos.render.section
import dev.kairos.render.span
import dev.kairos.render.src
import dev.kairos.render.type
import dev.kairos.runtime.Fonts
import dev.kairos.runtime.Layer
import dev.kairos.runtime.Radius
import dev.kairos.runtime.Spacing
import dev.kairos.runtime.StyleScope
import dev.kairos.runtime.alignItems
import dev.kairos.runtime.backgroundColor
import dev.kairos.runtime.border
import dev.kairos.runtime.borderRadius
import dev.kairos.runtime.borderTop
import dev.kairos.runtime.color
import dev.kairos.runtime.css
import dev.kairos.runtime.cursor
import dev.kairos.runtime.display
import dev.kairos.runtime.fontFamily
import dev.kairos.runtime.fontSize
import dev.kairos.runtime.fontWeight
import dev.kairos.runtime.gap
import dev.kairos.runtime.justifyContent
import dev.kairos.runtime.letterSpacing
import dev.kairos.runtime.lineHeight
import dev.kairos.runtime.margin
import dev.kairos.runtime.marginTop
import dev.kairos.runtime.maxWidth
import dev.kairos.runtime.padding
import dev.kairos.runtime.styles

/**
 * The demo page. Each style block folds into ONE class (grouped); the shipped script only swaps
 * class names and never touches a style value.
 */

enum class Theme { Light, Dark }
enum class Density { Comfortable, Compact }

const val THEME_TOGGLE = "theme-toggle"
const val DENSITY_TOGGLE = "density-toggle"
const val PAGE_MAIN = "page-main"

/** Any attribute can be declared once, anywhere, then used as a property. */
var ElementNode.dataKairosId: String by attribute("data-kairos-id")

// --- reset / base -------------------------------------------------------------

val headingReset: String = css(Layer.Reset) { margin(Spacing.NONE) }

val pageStyle: String = css {
    fontFamily(Fonts.SANS)
    lineHeight("1.6")
    margin(Spacing.NONE)
}

val shellStyle: String = css {
    maxWidth("880px")
    margin("0 auto")
    padding("0 24px 64px 24px")
}

val headerStyle: String = css {
    display("flex")
    alignItems("center")
    justifyContent("space-between")
    gap(Spacing.LG)
    padding("20px 0")
}

val brandStyle: String = css {
    fontSize("18px")
    fontWeight("700")
    letterSpacing("-0.01em")
}

val titleStyle: String = css {
    fontSize("44px")
    lineHeight("1.15")
    margin("24px 0 12px 0")
}

val leadStyle: String = css {
    fontSize("17px")
    maxWidth("640px")
}

val buttonStyle: String = css {
    declaration("font", "inherit")
    fontSize("14px")
    padding("8px 14px")
    borderRadius(Radius.FULL)
    border("1px solid #d5d7dd")
    backgroundColor("#ffffff")
    color("#16181d")
    cursor("pointer")
}

val cardStyle: String = css {
    calloutBorder()
    backgroundColor("#ffffff")
    borderRadius(Radius.LG)
    padding("18px")
}

/** A user defined wrapper. Inline, so the compiler expands it and keeps folding. */
inline fun StyleScope.calloutBorder() = border("1px solid #e3e5ea")

/** Override layer: wins over `cardStyle` for the border property. */
val featuredCardStyle: String = css(Layer.Override) { border("1px solid #7c5cff") }

val featuredCard: String = styles(cardStyle, featuredCardStyle)

val cardTitleStyle: String = css {
    fontSize("15px")
    fontWeight("650")
    margin("0 0 6px 0")
}

val mutedStyle: String = css {
    color("#5b6071")
    fontSize("14px")
}

val footerStyle: String = css {
    borderTop("1px solid #e3e5ea")
    marginTop("40px")
    padding("24px 0 0 0")
}

// --- variants: one class per mode --------------------------------------------

fun themeClass(theme: Theme): String = when (theme) {
    Theme.Light -> css(Layer.Variant) {
        backgroundColor("#f7f7f8")
        color("#16181d")
    }
    Theme.Dark -> css(Layer.Variant) {
        backgroundColor("#12141a")
        color("#eef0f4")
    }
}

fun densityClass(density: Density): String = when (density) {
    Density.Comfortable -> css { gap(Spacing.XL) }
    Density.Compact -> css { gap(Spacing.SM) }
}

/**
 * A ternary inside a style block. The compiler lifts both arms into classes and emits a class
 * selection, so the value never reaches the runtime - it demonstrates the tier 2 optimisation
 * without giving up "one block, one class".
 */
fun badgeClass(urgent: Boolean): String = css {
    fontSize(if (urgent) "16px" else "12px")
    fontWeight("700")
}

val initialThemeClass: String = themeClass(Theme.Light)

// --- page ---------------------------------------------------------------------

fun buildPage(): ElementNode = htmlRoot {
    lang = "en"
    style = initialThemeClass
    head {
        meta { charset = "utf-8" }
        meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
        el("title") { "Kairos - styles compiled, not computed".text }
        link { rel = "stylesheet"; href = "site.css" }
        link { rel = "stylesheet"; href = "kairos.css" }
    }
    body {
        style = pageStyle
        div {
            style = shellStyle
            header {
                style = headerStyle
                span {
                    style = styles(brandStyle, badgeClass(false))
                    "kairos".text
                }
                div {
                    button {
                        style = buttonStyle
                        dataKairosId = THEME_TOGGLE
                        type = "button"
                        "Toggle theme".text
                    }
                    button {
                        style = buttonStyle
                        dataKairosId = DENSITY_TOGGLE
                        type = "button"
                        "Toggle density".text
                    }
                }
            }
            main {
                dataKairosId = PAGE_MAIN
                style = densityClass(Density.Comfortable)
                h1 { style = styles(headingReset, titleStyle); "Styles compiled, not computed.".text }
                p {
                    style = leadStyle
                    ("Every style block on this page was folded into a single class at compile time. " +
                        "The shipped JavaScript only swaps class names - it never reads or writes a style value.").text
                }
                section {
                    div {
                        style = cardStyle
                        h2 { style = styles(headingReset, cardTitleStyle); "One block, one class".text }
                        p { style = mutedStyle; "Five declarations become a single class name instead of five.".text }
                    }
                    div {
                        style = cardStyle
                        h2 { style = styles(headingReset, cardTitleStyle); "Layered cascade".text }
                        p { style = mutedStyle; "reset < base < variant < override decides priority, never source order.".text }
                    }
                    div {
                        style = featuredCard
                        h2 { style = styles(headingReset, cardTitleStyle); "Override layer".text }
                        p { style = mutedStyle; "The override layer wins the border property by layer order alone.".text }
                    }
                }
                section {
                    div {
                        style = cardStyle
                        h2 { style = styles(headingReset, cardTitleStyle); "Reactive counter".text }
                        p {
                            style = mutedStyle
                            span { dataKairosId = "counter-value"; "0".text }
                            " clicks - the text node is the only thing that changes.".text
                        }
                        button {
                            style = buttonStyle
                            dataKairosId = "counter-increment"
                            type = "button"
                            "Increment".text
                        }
                    }
                }
                add(statsPanel())
            }
            footer {
                style = footerStyle
                p { style = mutedStyle; "Generated by the Kairos compiler plugin from Kotlin source.".text }
            }
        }
        script { src = "kotlin-kotlin-stdlib.js" }
        script { src = "kairos-kairos-core.js" }
        script { src = "kairos-kairos-browser.js" }
        script { src = "kairos-demo-web.js" }
    }
}

// --- fixtures used by the acceptance harness ----------------------------------

/** Tier 1: every declaration is a literal, so this block folds into one class. */
fun heroClass(): String = css {
    color("#111111")
    padding(Spacing.XL)
}

/** The all-runtime path must produce exactly the same class name as the folded one. */
fun copyCardClass(): String = css {
    border("1px solid #e3e5ea")
    backgroundColor("#ffffff")
    borderRadius(Radius.LG)
    padding("18px")
}
