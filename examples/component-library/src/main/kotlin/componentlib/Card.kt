package componentlib

import dev.kairos.render.ElementNode
import dev.kairos.render.div
import dev.kairos.render.h3
import dev.kairos.render.p
import dev.kairos.runtime.Layer
import dev.kairos.runtime.Radius
import dev.kairos.runtime.Spacing
import dev.kairos.runtime.backgroundColor
import dev.kairos.runtime.border
import dev.kairos.runtime.borderRadius
import dev.kairos.runtime.css
import dev.kairos.runtime.fontSize
import dev.kairos.runtime.fontWeight
import dev.kairos.runtime.margin
import dev.kairos.runtime.padding

/**
 * A compiled component library: consumers only ever see this jar. The stylesheet and the manifest
 * are embedded in `META-INF/kairos/...` and collected by the consumer's Gradle plugin.
 */
val cardStyle: String = css {
    backgroundColor("#ffffff")
    border("1px solid #e3e5ea")
    borderRadius(Radius.LG)
    padding(Spacing.LG)
    hover { border("1px solid #7c5cff") }
}

val cardTitleStyle: String = css(Layer.Variant) {
    fontSize("16px")
    fontWeight("650")
    margin("0 0 6px 0")
}

fun card(title: String, body: String): ElementNode = div {
    style = cardStyle
    h3 {
        style = cardTitleStyle
        title.text
    }
    p {
        body.text
    }
}
