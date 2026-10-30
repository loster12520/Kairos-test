package dev.kairos.feature

import dev.kairos.render.ElementNode
import dev.kairos.render.attribute
import dev.kairos.render.div
import dev.kairos.render.el
import dev.kairos.render.h2
import dev.kairos.render.p
import dev.kairos.render.span
import dev.kairos.runtime.Layer
import dev.kairos.runtime.Radius
import dev.kairos.runtime.Spacing
import dev.kairos.runtime.StyleScope
import dev.kairos.runtime.alignItems
import dev.kairos.runtime.backgroundColor
import dev.kairos.runtime.border
import dev.kairos.runtime.borderRadius
import dev.kairos.runtime.color
import dev.kairos.runtime.css
import dev.kairos.runtime.display
import dev.kairos.runtime.fontSize
import dev.kairos.runtime.fontWeight
import dev.kairos.runtime.gap
import dev.kairos.runtime.justifyContent
import dev.kairos.runtime.margin
import dev.kairos.runtime.padding
import dev.kairos.runtime.styles

/**
 * A second module: it is compiled on its own, so it produces its own kairos.css / kairos.map.json.
 * The site build merges both modules' maps into one stylesheet.
 */

enum class Trend { Up, Down }

var ElementNode.trend: String by attribute("data-trend")

inline fun StyleScope.panelSurface() = border("1px solid #e3e5ea")

val panelStyle: String = css {
    display("grid")
    gap(Spacing.LG)
    padding(Spacing.XL)
    borderRadius(Radius.LG)
    backgroundColor("#ffffff")
    panelSurface()
}

val panelRowStyle: String = css {
    display("flex")
    alignItems("center")
    justifyContent("space-between")
}

val panelTitleStyle: String = css {
    fontSize("15px")
    fontWeight("650")
    margin("0")
}

val trendValueStyle: String = css {
    fontSize("28px")
    fontWeight("700")
}

val trendUpClass: String = css(Layer.Variant) { color("#0a7d32") }
val trendDownClass: String = css(Layer.Variant) { color("#c0392b") }

fun trendClass(trend: Trend): String = when (trend) {
    Trend.Up -> trendUpClass
    Trend.Down -> trendDownClass
}

fun statsPanel(): ElementNode = el("section") {
    div {
        style = panelStyle
        div {
            style = panelRowStyle
            h2 { style = styles(panelTitleStyle); "Build stats".text }
            span {
                style = styles(trendValueStyle, trendUpClass)
                trend = "up"
                "+18%".text
            }
        }
        p { style = panelTitleStyle; "Classes emitted by this module: 8".text }
    }
}
