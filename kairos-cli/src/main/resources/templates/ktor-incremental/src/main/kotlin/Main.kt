package starter

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

private val pricingTitle: String = css { fontSize("28px") }

/** Pretend this route already existed before Kairos was added. */
private fun existingPage(): String =
    "<!DOCTYPE html><html><body><h1>Existing app</h1><p>This route is plain Ktor HTML.</p></body></html>"

/** The one page migrated to Kairos. */
private fun pricingPage(): String {
    val body = div {
        h1 { style = pricingTitle; "Pricing".text }
        p { "This route is rendered by Kairos; the rest of the app is untouched.".text }
    }
    return page("Pricing", listOf("/kairos.css"), body)
}

fun main() {
    embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
        routing {
            get("/") { call.respondText(existingPage(), ContentType.Text.Html) }
            get("/pricing") { call.respondText(pricingPage(), ContentType.Text.Html) }
            get("/kairos.css") {
                call.respondText(File("build/kairos/jvm/main/kairos.css").readText(), ContentType.Text.CSS)
            }
        }
    }.start(wait = true)
}
