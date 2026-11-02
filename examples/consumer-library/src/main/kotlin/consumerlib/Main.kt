package consumerlib

import componentlib.card
import dev.kairos.htmx.page
import dev.kairos.render.div
import dev.kairos.render.h1
import dev.kairos.runtime.css
import dev.kairos.runtime.fontSize
import java.io.File

/** Uses a component from a binary dependency plus one locally compiled style block. */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/rendered")
    out.mkdirs()

    val hero = css {
        fontSize("32px")
    }

    val body = div {
        h1 {
            style = hero
            "Binary component consumer".text
        }
        add(card("Compiled card", "This card was compiled in another module and consumed as a jar."))
    }

    val html = page(
        title = "Kairos library consumer",
        stylesheets = listOf("kairos.css"),
        bodyNode = body,
    )
    val index = File(out, "index.html")
    index.writeText(html)
    println("rendered " + index.absolutePath)
}
