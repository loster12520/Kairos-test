package consumer

import dev.kairos.htmx.page
import dev.kairos.render.div
import dev.kairos.render.h1
import dev.kairos.render.p
import dev.kairos.runtime.Layer
import dev.kairos.runtime.backgroundColor
import dev.kairos.runtime.borderRadius
import dev.kairos.runtime.color
import dev.kairos.runtime.css
import dev.kairos.runtime.fontSize
import dev.kairos.runtime.padding
import java.io.File

/**
 * The smallest possible out-of-tree consumer: only published Gradle coordinates and one plugin
 * id. Everything else (class folding, stylesheet generation) happens through `dev.kairos`.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/rendered")
    out.mkdirs()

    val cardStyle = css {
        backgroundColor("#ffffff")
        borderRadius("14px")
        padding("24px")
    }
    val headingStyle = css(Layer.Variant) {
        fontSize("30px")
    }
    val mutedStyle = css(Layer.Override) {
        color("#5c6470")
    }

    val body = div {
        style = css { padding("40px") }
        h1 {
            style = headingStyle
            "Kairos consumer works".text
        }
        p {
            style = mutedStyle
            "This page was built by a project that only depends on published artifacts.".text
        }
        div {
            style = cardStyle
            "The class names and the stylesheet came from the dev.kairos Gradle plugin.".text
        }
    }

    val html = page(
        title = "Kairos consumer",
        stylesheets = listOf("kairos.css"),
        bodyNode = body,
    )
    val index = File(out, "index.html")
    index.writeText(html)
    println("rendered " + index.absolutePath)
}
