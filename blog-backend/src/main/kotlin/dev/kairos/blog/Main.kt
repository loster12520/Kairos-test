package dev.kairos.blog

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
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

private const val CONTENT_SECURITY_POLICY =
    // style-src allows inline styles because htmx applies transient inline styles to indicators;
    // scripts stay strict (no inline, no eval).
    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
        "connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'"

/** HTML responses carry the CSP and the anti sniffing headers; fragments get them too. */
private suspend fun ApplicationCall.respondHtml(html: String, status: HttpStatusCode = HttpStatusCode.OK) {
    response.header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
    response.header("X-Content-Type-Options", "nosniff")
    response.header("Referrer-Policy", "no-referrer")
    respondText(html, ContentType.Text.Html, status)
}

fun Application.blogModule() {
    install(CallLogging)
    // One token per server process is enough for the sample: every form carries it and every
    // mutating route compares it before touching the database.
    val csrfToken = java.util.UUID.randomUUID().toString()

    val cssFile = File(System.getenv("KAIROS_CSS") ?: "build/kairos/blog.css")
    val assetsDir = File(
        System.getenv("KAIROS_ASSETS")
            ?: "../blog-frontend/build/compileSync/js/main/productionExecutable/kotlin"
    )

    routing {
        get("/") {
            val posts = Db.posts()
            call.respondHtml(blogPage(posts, Db.commentCounts(posts.map { it.id }), csrfToken))
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
                call.respondHtml(
                    blogPage(posts, Db.commentCounts(posts.map { it.id }), csrfToken, post, comments),
                )
                return@get
            }
            call.respondHtml(postDetail(post, comments, csrfToken))
        }

        get("/posts/list") {
            val sort = call.request.queryParameters["sort"] ?: "newest"
            val posts = Db.posts(sort)
            call.respondHtml(dev.kairos.htmx.fragment(postList(posts, Db.commentCounts(posts.map { it.id }))))
        }

        get("/posts/{id}/edit") {
            val id = call.parameters["id"]?.toLongOrNull()
            val post = id?.let { Db.post(it) }
            if (post == null) {
                call.respondHtml("not found", HttpStatusCode.NotFound)
                return@get
            }
            call.respondHtml(editPostForm(post, csrfToken))
        }

        get("/new") {
            call.respondHtml(newPostForm(csrfToken))
        }

        post("/posts") {
            val form = call.receiveParameters()
            if (form["_csrf"] != csrfToken) {
                call.respondHtml("csrf validation failed", HttpStatusCode.Forbidden)
                return@post
            }
            val title = form["title"]?.trim().orEmpty()
            val body = form["body"]?.trim().orEmpty()
            if (title.isEmpty() || body.isEmpty()) {
                // Re-render the form with the error and the user's input, never a bare error page.
                call.respondHtml(newPostForm(csrfToken, title, body, "title and body are required"))
                return@post
            }
            val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "post" }
            Db.insertPost(title, slug, body)
            val posts = Db.posts()
            // The form resets and the list updates out of band in the same response.
            call.respondHtml(
                newPostForm(csrfToken) +
                    dev.kairos.htmx.fragment(postList(posts, Db.commentCounts(posts.map { it.id }), oob = true)),
            )
        }

        post("/posts/{id}") {
            val form = call.receiveParameters()
            if (form["_csrf"] != csrfToken) {
                call.respondHtml("csrf validation failed", HttpStatusCode.Forbidden)
                return@post
            }
            val id = call.parameters["id"]?.toLongOrNull()
            val post = id?.let { Db.post(it) }
            if (id == null || post == null) {
                call.respondHtml("not found", HttpStatusCode.NotFound)
                return@post
            }
            val title = form["title"]?.trim().orEmpty()
            val body = form["body"]?.trim().orEmpty()
            if (title.isEmpty() || body.isEmpty()) {
                call.respondHtml(editPostForm(post, csrfToken, title, body, "title and body are required"))
                return@post
            }
            Db.updatePost(id, title, body)
            val updated = Db.post(id) ?: post
            val posts = Db.posts()
            call.respondHtml(
                postDetail(updated, Db.comments(id), csrfToken) +
                    dev.kairos.htmx.fragment(postList(posts, Db.commentCounts(posts.map { it.id }), oob = true)),
            )
        }

        post("/posts/{id}/delete") {
            val form = call.receiveParameters()
            if (form["_csrf"] != csrfToken) {
                call.respondHtml("csrf validation failed", HttpStatusCode.Forbidden)
                return@post
            }
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null || Db.post(id) == null) {
                call.respondHtml("not found", HttpStatusCode.NotFound)
                return@post
            }
            Db.deletePost(id)
            val posts = Db.posts()
            call.respondHtml(dev.kairos.htmx.fragment(postList(posts, Db.commentCounts(posts.map { it.id }))))
        }

        post("/posts/{id}/comments") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null || Db.post(id) == null) {
                call.respondText("not found", status = HttpStatusCode.NotFound)
                return@post
            }
            val form = call.receiveParameters()
            if (form["_csrf"] != csrfToken) {
                call.respondHtml("csrf validation failed", HttpStatusCode.Forbidden)
                return@post
            }
            val author = form["author"]?.trim().orEmpty().ifEmpty { "anonymous" }
            val body = form["body"]?.trim().orEmpty()
            if (body.isEmpty()) {
                call.respondHtml(
                    dev.kairos.htmx.fragment(
                        commentsSection(id, Db.comments(id), csrfToken, error = "comment is required", author = author, body = body),
                    ),
                )
                return@post
            }
            Db.insertComment(id, author, body)
            call.respondHtml(dev.kairos.htmx.fragment(commentsSection(id, Db.comments(id), csrfToken)))
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
