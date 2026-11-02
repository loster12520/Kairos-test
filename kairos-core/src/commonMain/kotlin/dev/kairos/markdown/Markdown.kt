package dev.kairos.markdown

import dev.kairos.render.ElementNode
import dev.kairos.render.Node
import dev.kairos.render.TextNode
import dev.kairos.render.el
import dev.kairos.render.href
import dev.kairos.render.isSafeUrl

/**
 * A small Markdown subset, rendered to Kairos nodes.
 *
 * It lives in kairos-core so the server and the browser can render the same text with the same
 * code. Supported: headings, paragraphs, fenced code, unordered lists, blockquotes and the
 * inline forms bold, italic, inline code and links.
 */

/** Built from a char code so this source file never needs a literal fence. */
private val TICK: Char = 0x60.toChar()
private val FENCE: String = TICK.toString().repeat(3)

/** Class names applied to the generated elements; empty means "browser default". */
@dev.kairos.ExperimentalKairosApi
data class MarkdownStyles(
    val heading1: String = "",
    val heading2: String = "",
    val heading3: String = "",
    val paragraph: String = "",
    val codeBlock: String = "",
    val code: String = "",
    val blockquote: String = "",
    val list: String = "",
    val listItem: String = "",
    val link: String = "",
)

private fun ElementNode.withStyle(className: String) {
    if (className.isNotEmpty()) style = className
}

fun renderMarkdown(source: String, styles: MarkdownStyles = MarkdownStyles()): List<Node> {
    val nodes = ArrayList<Node>()
    val lines = source.replace("\r\n", "\n").split("\n")
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        when {
            line.startsWith(FENCE) -> {
                index++
                val code = StringBuilder()
                while (index < lines.size && !lines[index].startsWith(FENCE)) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[index])
                    index++
                }
                index++
                nodes += el("pre") {
                    withStyle(styles.codeBlock)
                    add(el("code") { withStyle(styles.code); text(code.toString()) })
                }
            }

            line.startsWith("### ") -> {
                val content = line.removePrefix("### ")
                nodes += el("h3") { withStyle(styles.heading3); inlineInto(this, content, styles) }
                index++
            }

            line.startsWith("## ") -> {
                val content = line.removePrefix("## ")
                nodes += el("h2") { withStyle(styles.heading2); inlineInto(this, content, styles) }
                index++
            }

            line.startsWith("# ") -> {
                val content = line.removePrefix("# ")
                nodes += el("h1") { withStyle(styles.heading1); inlineInto(this, content, styles) }
                index++
            }

            line.startsWith("> ") -> {
                val quoted = ArrayList<String>()
                while (index < lines.size && lines[index].startsWith("> ")) {
                    quoted += lines[index].removePrefix("> ")
                    index++
                }
                val text = quoted.joinToString(" ")
                nodes += el("blockquote") { withStyle(styles.blockquote); inlineInto(this, text, styles) }
            }

            line.startsWith("- ") || line.startsWith("* ") -> {
                val list = el("ul") { withStyle(styles.list) }
                while (index < lines.size && (lines[index].startsWith("- ") || lines[index].startsWith("* "))) {
                    val content = lines[index].substring(2)
                    val item = el("li") { withStyle(styles.listItem) }
                    inlineInto(item, content, styles)
                    list.add(item)
                    index++
                }
                nodes += list
            }

            line.isBlank() -> index++

            else -> {
                val paragraph = StringBuilder()
                while (index < lines.size && lines[index].isNotBlank() && !isBlockStart(lines[index])) {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(lines[index].trim())
                    index++
                }
                val content = paragraph.toString()
                nodes += el("p") { withStyle(styles.paragraph); inlineInto(this, content, styles) }
            }
        }
    }
    return nodes
}

/** Appends the rendered markdown to an element. */
fun ElementNode.markdown(source: String, styles: MarkdownStyles = MarkdownStyles()) {
    renderMarkdown(source, styles).forEach { add(it) }
}

private fun isBlockStart(line: String): Boolean =
    line.startsWith("# ") || line.startsWith("## ") || line.startsWith("### ") ||
        line.startsWith("> ") || line.startsWith("- ") || line.startsWith("* ") ||
        line.startsWith(FENCE)

private fun inlineInto(parent: ElementNode, line: String, styles: MarkdownStyles) {
    var index = 0
    val buffer = StringBuilder()
    fun flush() {
        if (buffer.isNotEmpty()) {
            parent.add(TextNode(buffer.toString()))
            buffer.setLength(0)
        }
    }
    while (index < line.length) {
        val rest = line.substring(index)
        when {
            rest.startsWith("**") -> {
                val end = line.indexOf("**", index + 2)
                if (end > 0) {
                    flush()
                    val bold = line.substring(index + 2, end)
                    // Bold text may contain links; parse the span recursively.
                    parent.add(el("strong") { inlineInto(this, bold, styles) })
                    index = end + 2
                } else {
                    buffer.append(line[index]); index++
                }
            }
            line[index] == TICK -> {
                val end = line.indexOf(TICK, index + 1)
                if (end > 0) {
                    flush()
                    val code = line.substring(index + 1, end)
                    parent.add(el("code") { text(code) })
                    index = end + 1
                } else {
                    buffer.append(line[index]); index++
                }
            }
            rest.startsWith("*") -> {
                val end = line.indexOf("*", index + 1)
                if (end > 0) {
                    flush()
                    val italic = line.substring(index + 1, end)
                    parent.add(el("em") { inlineInto(this, italic, styles) })
                    index = end + 1
                } else {
                    buffer.append(line[index]); index++
                }
            }
            rest.startsWith("[") -> {
                val close = line.indexOf(']', index)
                val open = if (close > 0) line.indexOf('(', close) else -1
                val endUrl = if (open > 0) line.indexOf(')', open) else -1
                if (close > 0 && open == close + 1 && endUrl > 0) {
                    flush()
                    val label = line.substring(index + 1, close)
                    val url = line.substring(open + 1, endUrl)
                    if (isSafeUrl(url)) {
                        parent.add(el("a") {
                            withStyle(styles.link)
                            href = url
                            text(label)
                        })
                    } else {
                        // Untrusted markdown is never allowed to fail the render: the unsafe link
                        // degrades to its label instead of becoming a live javascript: URL.
                        parent.add(TextNode(label))
                    }
                    index = endUrl + 1
                } else {
                    buffer.append(line[index]); index++
                }
            }
            else -> {
                buffer.append(line[index]); index++
            }
        }
    }
    flush()
}
