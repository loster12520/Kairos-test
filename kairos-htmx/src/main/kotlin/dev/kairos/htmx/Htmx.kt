package dev.kairos.htmx

import dev.kairos.render.ElementNode
import dev.kairos.render.Node
import dev.kairos.render.attribute
import dev.kairos.render.body
import dev.kairos.render.charset
import dev.kairos.render.content
import dev.kairos.render.el
import dev.kairos.render.head
import dev.kairos.render.href
import dev.kairos.render.lang
import dev.kairos.render.link
import dev.kairos.render.meta
import dev.kairos.render.name
import dev.kairos.render.rel
import dev.kairos.render.renderFragment
import dev.kairos.render.renderToHtml
import dev.kairos.render.script
import dev.kairos.render.src

/**
 * The server half of Kairos.
 *
 * Templates are ordinary Kotlin functions returning a node tree; rendering produces plain HTML
 * strings, which is exactly what htmx wants as a response body.
 */

/** A complete document. [stylesheet] is the path the page loads its compiled CSS from. */
fun page(
    title: String,
    stylesheets: List<String>,
    bodyNode: ElementNode,
    language: String = "en",
    scripts: List<String> = emptyList(),
    headContent: ElementNode? = null,
): String {
    val document = el("html") {
        lang = language
        head {
            meta { charset = "utf-8" }
            meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
            add(el("title") { text(title) })
            stylesheets.forEach { path -> link { rel = "stylesheet"; href = path } }
            headContent?.let { add(it) }
        }
        body {
            add(bodyNode)
            scripts.forEach { path -> script { src = path } }
        }
    }
    return renderToHtml(document)
}

/** Renders a fragment; this is what an htmx request responds with. */
fun fragment(node: Node): String = renderFragment(node)

// -- htmx attributes ----------------------------------------------------------

var ElementNode.hxGet: String by attribute("hx-get")
var ElementNode.hxPost: String by attribute("hx-post")
var ElementNode.hxPut: String by attribute("hx-put")
var ElementNode.hxDelete: String by attribute("hx-delete")
var ElementNode.hxTarget: String by attribute("hx-target")
var ElementNode.hxSwap: String by attribute("hx-swap")
var ElementNode.hxTrigger: String by attribute("hx-trigger")
var ElementNode.hxIndicator: String by attribute("hx-indicator")
var ElementNode.hxConfirm: String by attribute("hx-confirm")
var ElementNode.hxPushUrl: String by attribute("hx-push-url")
var ElementNode.hxInclude: String by attribute("hx-include")
var ElementNode.hxVals: String by attribute("hx-vals")
var ElementNode.hxBoost: String by attribute("hx-boost")
