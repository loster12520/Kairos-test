package dev.kairos.render

/**
 * Deterministic HTML serialiser.
 *
 * Lives in commonMain so the exact same markup is produced by the JVM (server side / static
 * generation) and by Kotlin/JS (client side) - the two halves of Kairos share one renderer.
 *
 * The output depends only on the tree: attributes keep insertion order, indentation is fixed,
 * and there is no timestamp or random id anywhere.
 */
fun renderToHtml(root: Node, indent: String = "  "): String = buildString {
    append("<!DOCTYPE html>")
    append('\n')
    renderNode(root, this, indent, 0)
}

/** Serialises a node without the doctype, for fragments embedded in an existing document. */
fun renderFragment(node: Node, indent: String = "  "): String = buildString {
    renderNode(node, this, indent, 0)
}

private val voidElements = setOf(
    "area", "base", "br", "col", "embed", "hr", "img",
    "input", "link", "meta", "source", "track", "wbr",
)

private fun renderNode(node: Node, out: StringBuilder, indent: String, depth: Int) {
    when (node) {
        is TextNode -> {
            out.append(indent.repeat(depth)).append(escapeText(node.value)).append('\n')
        }

        is RawHtml -> {
            out.append(indent.repeat(depth)).append(node.html).append('\n')
        }

        is FragmentNode -> {
            // A fragment contributes its children at the current depth; it never emits a tag.
            node.children.forEach { renderNode(it, out, indent, depth) }
        }

        is ElementNode -> {
            requireValidName(node.tag, "element name")
            node.attributes.keys.forEach { requireValidName(it, "attribute name") }
            if (node.tag in voidElements && node.children.isNotEmpty()) {
                throw IllegalStateException("void element <" + node.tag + "> must not have children")
            }
            val pad = indent.repeat(depth)
            out.append(pad).append('<').append(node.tag)
            node.attributes.forEach { (name, value) ->
                // Second line of defence: attributes set through raw map access still cannot
                // smuggle a javascript: URL into the document.
                val safe = if (name.lowercase() in URL_ATTRIBUTES) requireSafeUrl(name, value) else value
                out.append(' ').append(name).append("=\"").append(escapeAttribute(safe)).append('"')
            }
            if (node.tag in voidElements) {
                out.append(">").append('\n')
                return
            }
            out.append('>')
            if (node.children.isEmpty()) {
                out.append("</").append(node.tag).append(">").append('\n')
                return
            }
            // Text only elements stay on one line: <title>Foo</title>, not a three line blob.
            val inline = textOf(node.children)
            if (inline != null) {
                out.append(escapeText(inline)).append("</").append(node.tag).append(">").append('\n')
                return
            }
            out.append('\n')
            node.children.forEach { renderNode(it, out, indent, depth + 1) }
            out.append(pad).append("</").append(node.tag).append(">").append('\n')
        }
    }
}

private fun requireValidName(name: String, what: String) {
    require(name.isNotEmpty()) { what + " must not be empty" }
    require(name.all { it.isLetterOrDigit() || it == '-' || it == ':' || it == '_' }) {
        what + " contains characters that cannot be emitted safely: " + name
    }
}

/** Adjacent text nodes are joined so the serialiser never invents whitespace between them. */
private fun textOf(children: List<Node>): String? {
    if (children.any { it !is TextNode }) return null
    return children.filterIsInstance<TextNode>().joinToString("") { it.value }
}

private fun escapeText(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun escapeAttribute(value: String): String =
    escapeText(value).replace("\"", "&quot;")
