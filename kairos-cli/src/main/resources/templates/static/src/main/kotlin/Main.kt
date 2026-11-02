package starter

import dev.kairos.htmx.page
import dev.kairos.render.div
import dev.kairos.render.h1
import dev.kairos.render.p
import dev.kairos.runtime.css
import dev.kairos.runtime.fontSize
import java.io.File

private val titleStyle: String = css { fontSize("32px") }

fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/site")
    output.mkdirs()
    val body = div {
        h1 { style = titleStyle; "Hello, Kairos".text }
        p { "This page was rendered by Kairos; its stylesheet was folded at compile time.".text }
    }
    val html = page("Kairos starter", listOf("kairos.css"), body)
    val index = File(output, "index.html")
    index.writeText(html)
    println("rendered " + index.absolutePath)
}
