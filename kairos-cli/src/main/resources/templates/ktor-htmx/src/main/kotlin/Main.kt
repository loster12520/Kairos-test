package starter

import dev.kairos.htmx.fragment
import dev.kairos.htmx.page
import dev.kairos.render.div
import dev.kairos.render.h1
import dev.kairos.render.p
import dev.kairos.runtime.css
import dev.kairos.runtime.fontSize
import io.ktor.http.ContentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File

private val titleStyle: String = css { fontSize("32px") }

private fun landing(): String {
    val body = div {
        h1 { style = titleStyle; "Hello, Kairos".text }
        p { "Ktor renders the page, htmx swaps the fragment, Kairos compiles the styles.".text }
    }
    return page("Kairos starter", listOf("/kairos.css"), body)
}

fun main() {
    embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
        routing {
            get("/") { call.respondText(landing(), ContentType.Text.Html) }
            get("/fragment") {
                val node = div { h1 { "Fragment".text } }
                call.respondText(fragment(node), ContentType.Text.Html)
            }
            get("/kairos.css") {
                val file = File("build/kairos/jvm/main/kairos.css")
                call.respondText(file.readText(), ContentType.Text.CSS)
            }
        }
    }.start(wait = true)
}
