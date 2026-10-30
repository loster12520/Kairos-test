package dev.kairos.blog

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.File

/**
 * The blog server.
 *
 * Two rendering duties: full pages (rendered once, rarely) and htmx fragments (rendered per
 * interaction). Both go through the same Kairos templates, so a fragment is just a smaller tree.
 */
fun main() {
    val databaseFile = File(System.getenv("KAIROS_DB") ?: "build/blog.db")
    Db.start(databaseFile)
    val port = (System.getenv("PORT") ?: "8080").toInt()
    embeddedServer(Netty, port = port, host = "127.0.0.1") { blogModule() }.start(wait = true)
}

fun Application.blogModule() {
    install(CallLogging)

    val cssFile = File(System.getenv("KAIROS_CSS") ?: "build/kairos/blog.css")
    val assetsDir = File(
        System.getenv("KAIROS_ASSETS")
            ?: "../blog-frontend/build/compileSync/js/main/productionExecutable/kotlin"
    )

    routing {
        get("/") {
            val posts = Db.posts()
            call.respondText(blogPage(posts, Db.commentCounts(posts.map { it.id })), ContentType.Text.Html)
        }

        get("/posts/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respondText("bad id", status = HttpStatusCode.BadRequest)
                return@get
            }
            val post = Db.post(id)
            if (post == null) {
                call.respondText("not found", status = HttpStatusCode.NotFound)
                return@get
            }
            val comments = Db.comments(id)
            // htmx sends HX-Request; a direct visit (deep link, refresh) must get a full page.
            if (call.request.headers["HX-Request"] != "true") {
                val posts = Db.posts()
                call.respondText(
                    blogPage(posts, Db.commentCounts(posts.map { it.id }), post, comments),
                    ContentType.Text.Html,
                )
                return@get
            }
            call.respondText(postDetail(post, comments), ContentType.Text.Html)
        }

        get("/new") {
            call.respondText(newPostForm(), ContentType.Text.Html)
        }

        post("/posts") {
            val form = call.receiveParameters()
            val title = form["title"]?.trim().orEmpty()
            val body = form["body"]?.trim().orEmpty()
            if (title.isEmpty() || body.isEmpty()) {
                call.respondText("title and body are required", status = HttpStatusCode.BadRequest)
                return@post
            }
            val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "post" }
            Db.insertPost(title, slug, body)
            val posts = Db.posts()
            call.respondText(dev.kairos.htmx.fragment(postList(posts, Db.commentCounts(posts.map { it.id }))), ContentType.Text.Html)
        }

        post("/posts/{id}/comments") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null || Db.post(id) == null) {
                call.respondText("not found", status = HttpStatusCode.NotFound)
                return@post
            }
            val form = call.receiveParameters()
            val author = form["author"]?.trim().orEmpty().ifEmpty { "anonymous" }
            val body = form["body"]?.trim().orEmpty()
            if (body.isEmpty()) {
                call.respondText("comment is required", status = HttpStatusCode.BadRequest)
                return@post
            }
            Db.insertComment(id, author, body)
            call.respondText(dev.kairos.htmx.fragment(commentsSection(id, Db.comments(id))), ContentType.Text.Html)
        }

        get("/htmx.js") {
            val body = Thread.currentThread().contextClassLoader.getResourceAsStream("htmx.min.js")
                ?.bufferedReader()?.readText()
            if (body == null) {
                call.respondText("not found", status = HttpStatusCode.NotFound)
                return@get
            }
            call.respondText(body, ContentType.Text.JavaScript)
        }

        get("/theme.css") {
            val body = Thread.currentThread().contextClassLoader.getResourceAsStream("static/theme.css")
                ?.bufferedReader()?.readText()
            if (body == null) {
                call.respondText("not found", status = HttpStatusCode.NotFound)
                return@get
            }
            call.respondText(body, ContentType.Text.CSS)
        }

        get("/kairos.css") {
            if (!cssFile.exists()) {
                call.respondText("/* stylesheet not built */", ContentType.Text.CSS)
                return@get
            }
            call.respondText(cssFile.readText(), ContentType.Text.CSS)
        }

        get("/{asset}") {
            val name = call.parameters["asset"].orEmpty()
            val file = File(assetsDir, name)
            if (!name.endsWith(".js") || !file.exists()) {
                call.respondText("not found", status = HttpStatusCode.NotFound)
                return@get
            }
            call.respondText(file.readText(), ContentType.Text.JavaScript)
        }
    }
}
