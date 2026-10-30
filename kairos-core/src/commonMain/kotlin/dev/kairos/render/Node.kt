package dev.kairos.render

/** A renderer agnostic node. Values are plain data so every backend agrees. */
sealed interface Node

/** A text leaf. Escaping happens in the serializer, never here. */
data class TextNode(val value: String) : Node

/**
 * An element.
 *
 * Attributes are written through properties rather than through an `attr(...)` call:
 * `element.style`, `element.id`, and anything else an author declares once, anywhere:
 *
 * `
 * var ElementNode.dataKairosId: String by attribute("data-kairos-id")
 * `
 *
 * Inside a builder block the receiver is the element, so plain assignment and the
 * `"literal".text` extension both work without ceremony.
 */
class ElementNode(val tag: String) : Node {
    val attributes: LinkedHashMap<String, String> = LinkedHashMap()
    val children: MutableList<Node> = ArrayList()

    var id: String
        get() = attributes["id"].orEmpty()
        set(value) {
            attributes["id"] = value
        }

    /** The CSS class list. Assigning replaces it; `+=` appends because the getter reads it back. */
    var style: String
        get() = attributes["class"].orEmpty()
        set(value) {
            if (value.isBlank()) attributes.remove("class") else attributes["class"] = value.trim()
        }

    /** Alias of [style]; `"class"` is a Kotlin keyword so the property can not use that name. */
    var className: String
        get() = style
        set(value) {
            style = value
        }

    /**
     * Appends a text child: `"hello".text` inside a builder block.
     *
     * It is a member extension property on purpose - the receiver of the builder block tells it
     * where to append, so there is no ambient state.
     */
    val String.text: Unit
        get() {
            if (isNotEmpty()) children += TextNode(this)
        }

    /** Appends a text child from a computed value. */
    fun text(value: String) {
        if (value.isNotEmpty()) children += TextNode(value)
    }

    /**
     * Appends classes. Blank entries are dropped, which is what a folded style expression
     * produces when it resolves to nothing.
     */
    fun cls(vararg values: String) {
        val addition = values.filter { it.isNotBlank() }.joinToString(" ").trim()
        if (addition.isEmpty()) return
        style = if (style.isEmpty()) addition else style + " " + addition
    }

    fun add(node: Node) {
        children += node
    }

    fun addAll(nodes: List<Node>) {
        children += nodes
    }
}

fun el(tag: String, block: ElementNode.() -> Unit = {}): ElementNode = ElementNode(tag).apply(block)

// Root builders: usable where there is no ambient ElementNode receiver.
fun html(block: ElementNode.() -> Unit = {}): ElementNode = el("html", block)
fun div(block: ElementNode.() -> Unit = {}): ElementNode = el("div", block)

// Builders that append themselves to the current element.
fun ElementNode.html(block: ElementNode.() -> Unit = {}): ElementNode = el("html", block).also { children += it }
fun ElementNode.head(block: ElementNode.() -> Unit = {}): ElementNode = el("head", block).also { children += it }
fun ElementNode.body(block: ElementNode.() -> Unit = {}): ElementNode = el("body", block).also { children += it }
fun ElementNode.main(block: ElementNode.() -> Unit = {}): ElementNode = el("main", block).also { children += it }
fun ElementNode.div(block: ElementNode.() -> Unit = {}): ElementNode = el("div", block).also { children += it }
fun ElementNode.header(block: ElementNode.() -> Unit = {}): ElementNode = el("header", block).also { children += it }
fun ElementNode.footer(block: ElementNode.() -> Unit = {}): ElementNode = el("footer", block).also { children += it }
fun ElementNode.nav(block: ElementNode.() -> Unit = {}): ElementNode = el("nav", block).also { children += it }
fun ElementNode.section(block: ElementNode.() -> Unit = {}): ElementNode = el("section", block).also { children += it }
fun ElementNode.article(block: ElementNode.() -> Unit = {}): ElementNode = el("article", block).also { children += it }
fun ElementNode.aside(block: ElementNode.() -> Unit = {}): ElementNode = el("aside", block).also { children += it }
fun ElementNode.h1(block: ElementNode.() -> Unit = {}): ElementNode = el("h1", block).also { children += it }
fun ElementNode.h2(block: ElementNode.() -> Unit = {}): ElementNode = el("h2", block).also { children += it }
fun ElementNode.h3(block: ElementNode.() -> Unit = {}): ElementNode = el("h3", block).also { children += it }
fun ElementNode.p(block: ElementNode.() -> Unit = {}): ElementNode = el("p", block).also { children += it }
fun ElementNode.span(block: ElementNode.() -> Unit = {}): ElementNode = el("span", block).also { children += it }
fun ElementNode.a(block: ElementNode.() -> Unit = {}): ElementNode = el("a", block).also { children += it }
fun ElementNode.ul(block: ElementNode.() -> Unit = {}): ElementNode = el("ul", block).also { children += it }
fun ElementNode.ol(block: ElementNode.() -> Unit = {}): ElementNode = el("ol", block).also { children += it }
fun ElementNode.li(block: ElementNode.() -> Unit = {}): ElementNode = el("li", block).also { children += it }
fun ElementNode.button(block: ElementNode.() -> Unit = {}): ElementNode = el("button", block).also { children += it }
fun ElementNode.form(block: ElementNode.() -> Unit = {}): ElementNode = el("form", block).also { children += it }
fun ElementNode.label(block: ElementNode.() -> Unit = {}): ElementNode = el("label", block).also { children += it }
fun ElementNode.textarea(block: ElementNode.() -> Unit = {}): ElementNode = el("textarea", block).also { children += it }
fun ElementNode.select(block: ElementNode.() -> Unit = {}): ElementNode = el("select", block).also { children += it }
fun ElementNode.option(block: ElementNode.() -> Unit = {}): ElementNode = el("option", block).also { children += it }
fun ElementNode.code(block: ElementNode.() -> Unit = {}): ElementNode = el("code", block).also { children += it }
fun ElementNode.pre(block: ElementNode.() -> Unit = {}): ElementNode = el("pre", block).also { children += it }
fun ElementNode.time(block: ElementNode.() -> Unit = {}): ElementNode = el("time", block).also { children += it }
fun ElementNode.strong(block: ElementNode.() -> Unit = {}): ElementNode = el("strong", block).also { children += it }
fun ElementNode.em(block: ElementNode.() -> Unit = {}): ElementNode = el("em", block).also { children += it }
fun ElementNode.blockquote(block: ElementNode.() -> Unit = {}): ElementNode = el("blockquote", block).also { children += it }
fun ElementNode.table(block: ElementNode.() -> Unit = {}): ElementNode = el("table", block).also { children += it }
fun ElementNode.tr(block: ElementNode.() -> Unit = {}): ElementNode = el("tr", block).also { children += it }
fun ElementNode.td(block: ElementNode.() -> Unit = {}): ElementNode = el("td", block).also { children += it }
fun ElementNode.th(block: ElementNode.() -> Unit = {}): ElementNode = el("th", block).also { children += it }
fun ElementNode.small(block: ElementNode.() -> Unit = {}): ElementNode = el("small", block).also { children += it }

// Void elements: they take attributes but never children.
fun ElementNode.script(block: ElementNode.() -> Unit = {}): ElementNode = el("script", block).also { children += it }
fun ElementNode.style(block: ElementNode.() -> Unit = {}): ElementNode = el("style", block).also { children += it }
fun ElementNode.meta(block: ElementNode.() -> Unit = {}): ElementNode = el("meta", block).also { children += it }
fun ElementNode.link(block: ElementNode.() -> Unit = {}): ElementNode = el("link", block).also { children += it }
fun ElementNode.input(block: ElementNode.() -> Unit = {}): ElementNode = el("input", block).also { children += it }
fun ElementNode.hr(): ElementNode = el("hr").also { children += it }
fun ElementNode.br(): ElementNode = el("br").also { children += it }
