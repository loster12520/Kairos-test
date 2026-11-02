package docsite

import dev.kairos.htmx.page
import dev.kairos.markdown.MarkdownStyles
import dev.kairos.markdown.renderMarkdown
import dev.kairos.render.ElementNode
import dev.kairos.render.Node
import dev.kairos.render.a
import dev.kairos.render.aside
import dev.kairos.render.div
import dev.kairos.render.h1
import dev.kairos.render.href
import dev.kairos.render.main
import dev.kairos.render.nav
import dev.kairos.render.p
import dev.kairos.runtime.Fonts
import dev.kairos.runtime.Layer
import dev.kairos.runtime.Radius
import dev.kairos.runtime.Spacing
import dev.kairos.runtime.backgroundColor
import dev.kairos.runtime.border
import dev.kairos.runtime.borderRadius
import dev.kairos.runtime.color
import dev.kairos.runtime.css
import dev.kairos.runtime.display
import dev.kairos.runtime.fontFamily
import dev.kairos.runtime.fontSize
import dev.kairos.runtime.fontWeight
import dev.kairos.runtime.gap
import dev.kairos.runtime.lineHeight
import dev.kairos.runtime.margin
import dev.kairos.runtime.maxWidth
import dev.kairos.runtime.overflow
import dev.kairos.runtime.padding
import dev.kairos.runtime.textDecoration
import java.io.File

/** One rendered documentation page. */
private data class DocPage(val slug: String, val title: String, val source: File, val relative: String)

private val markdownStyles: MarkdownStyles by lazy {
    MarkdownStyles(
    heading1 = docH1,
    heading2 = docH2,
    heading3 = docH3,
    paragraph = docP,
    codeBlock = docPre,
    code = docCode,
    blockquote = docQuote,
    list = docList,
    listItem = docLi,
        link = docLink,
    )
}

fun main(args: Array<String>) {
    val root = File(args.getOrElse(0) { "." }).absoluteFile.normalize()
    val output = File(args.getOrElse(1) { "build/rendered" })
    output.mkdirs()
    val pages = collectPages(root)
    check(pages.isNotEmpty()) { "no markdown sources found under " + root }
    val byRelative = pages.associateBy { it.relative }
    pages.forEach { source ->
        val html = renderPage(source, pages, byRelative, root)
        File(output, source.slug + ".html").writeText(html)
    }
    File(output, "index.html").writeText(renderIndex(pages, byRelative, root))
    println("rendered " + pages.size + " pages into " + output.absolutePath)
}

private fun collectPages(root: File): List<DocPage> {
    val sources = ArrayList<File>()
    File(root, "README.md").takeIf { it.exists() }?.let { sources += it }
    File(root, "direction.md").takeIf { it.exists() }?.let { sources += it }
    File(root, "direction.dialogue.md").takeIf { it.exists() }?.let { sources += it }
    File(root, ".lignting/kairos/plan-1.0.md").takeIf { it.exists() }?.let { sources += it }
    File(root, "docs").walkTopDown()
        .filter { it.isFile && it.extension == "md" }
        .sortedBy { it.path }
        .forEach { sources += it }
    return sources.map { file ->
        val relative = file.relativeTo(root).invariantSeparatorsPath
        DocPage(slug = slugOf(relative), title = titleOf(file, relative), source = file, relative = relative)
    }.sortedBy { it.slug }
}

private fun slugOf(relative: String): String =
    relative.removeSuffix(".md")
        .removePrefix("./")
        .replace("/", "-")
        .replace(".", "-")
        .replace("_", "-")
        .lowercase()

private fun titleOf(file: File, relative: String): String {
    val first = file.readLines().firstOrNull { it.isNotBlank() }
    return first?.takeIf { it.startsWith("# ") }?.removePrefix("# ")?.trim()
        ?: file.name.removeSuffix(".md")
}

private fun renderPage(page: DocPage, all: List<DocPage>, byRelative: Map<String, DocPage>, root: File): String {
    val body = shell(page.title, all, currentSlug = page.slug) {
        renderMarkdown(contentWithoutTitle(page.source), markdownStyles).forEach { add(it) }
    }
    rewriteLinks(body, byRelative, root, page.source)
    return page(page.title + " · Kairos docs", listOf("kairos.css"), body)
}

private fun renderIndex(all: List<DocPage>, byRelative: Map<String, DocPage>, root: File): String {
    val readme = File(root, "README.md").takeIf { it.exists() } ?: all.first().source
    val body = shell("Kairos", all, currentSlug = "") {
        renderMarkdown(contentWithoutTitle(readme), markdownStyles).forEach { add(it) }
    }
    rewriteLinks(body, byRelative, root, readme)
    return page("Kairos · documentation", listOf("kairos.css"), body)
}

private fun shell(title: String, all: List<DocPage>, currentSlug: String, content: ElementNode.() -> Unit): ElementNode = div {
    style = pageStyle
    div {
        style = shellStyle
        aside {
            style = sidebarStyle
            a {
                style = brandStyle
                href = "index.html"
                "Kairos".text
            }
            p {
                style = taglineStyle
                "1.0 路线 · 契约 · 指南".text
            }
            nav {
                style = navStyle
                all.forEach { entry ->
                    a {
                        style = if (entry.slug == currentSlug) activeLinkStyle else linkStyle
                        href = entry.slug + ".html"
                        entry.title.text
                    }
                }
            }
            p {
                style = footnoteStyle
                "本站由 Kairos 自举渲染（dogfood）".text
            }
        }
        main {
            style = mainStyle
            h1 {
                style = pageTitleStyle
                title.text
            }
            content()
        }
    }
}

/** The page already renders a title, so the source's leading `# heading` is dropped. */
private fun contentWithoutTitle(file: File): String {
    val lines = file.readText().lines()
    val first = lines.indexOfFirst { it.isNotBlank() }
    return if (first >= 0 && lines[first].startsWith("# ")) {
        lines.drop(first + 1).joinToString("\n")
    } else {
        file.readText()
    }
}

private fun rewriteLinks(node: Node, byRelative: Map<String, DocPage>, root: File, current: File) {
    if (node is dev.kairos.render.FragmentNode) {
        node.children.forEach { rewriteLinks(it, byRelative, root, current) }
        return
    }
    if (node !is ElementNode) return
    val href = node.attributes["href"]
    if (href != null && !href.contains("://")) {
        val hashIndex = href.indexOf('#')
        val path = if (hashIndex >= 0) href.substring(0, hashIndex) else href
        val anchor = if (hashIndex >= 0) href.substring(hashIndex) else ""
        if (path.endsWith(".md")) {
            val mapped = runCatching {
                val target = File(current.parentFile, path).normalize()
                val relative = target.relativeTo(root).invariantSeparatorsPath
                byRelative[relative]
            }.getOrNull()
            if (mapped != null) {
                node.attributes["href"] = mapped.slug + ".html" + anchor
            } else {
                // A source that is not part of the site must not become a dead link.
                node.attributes.remove("href")
            }
        }
    }
    node.children.forEach { rewriteLinks(it, byRelative, root, current) }
}

// -- compiled styles ----------------------------------------------------------

private val pageStyle: String = css {
    fontFamily(Fonts.SANS)
    lineHeight("1.6")
    margin("0")
    color("#16181d")
    backgroundColor("#f7f7f8")
}

private val shellStyle: String = css {
    display("grid")
    declaration("grid-template-columns", "260px minmax(0, 1fr)")
    gap("32px")
    maxWidth("1180px")
    margin("0 auto")
    padding("32px 24px 64px 24px")
    media("(max-width: 820px)") {
        declaration("grid-template-columns", "1fr")
        padding("16px")
    }
}

private val sidebarStyle: String = css {
    declaration("position", "sticky")
    declaration("top", "24px")
    declaration("align-self", "start")
}

private val brandStyle: String = css(Layer.Variant) {
    fontSize("20px")
    fontWeight("700")
    color("#16181d")
    textDecoration("none")
}

private val taglineStyle: String = css {
    fontSize("13px")
    color("#5b6071")
    margin("4px 0 16px 0")
}

private val navStyle: String = css {
    display("flex")
    declaration("flex-direction", "column")
    gap("2px")
}

private val linkStyle: String = css {
    fontSize("13px")
    color("#5b6071")
    textDecoration("none")
    padding("4px 8px")
    borderRadius(Radius.SM)
    hover {
        backgroundColor("#eceef3")
        color("#16181d")
    }
}

private val activeLinkStyle: String = css(Layer.Override) {
    fontSize("13px")
    color("#3f3aa0")
    backgroundColor("#e8eaf5")
    fontWeight("600")
    padding("4px 8px")
    borderRadius(Radius.SM)
}

private val footnoteStyle: String = css {
    fontSize("12px")
    color("#8a90a0")
    margin("24px 0 0 0")
}

private val mainStyle: String = css {
    backgroundColor("#ffffff")
    borderRadius(Radius.LG)
    border("1px solid #e3e5ea")
    padding(Spacing.XXL)
    overflow("hidden")
}

private val pageTitleStyle: String = css(Layer.Variant) {
    fontSize("30px")
    margin("0 0 16px 0")
}

private val docH1: String = css { fontSize("26px"); margin("0 0 14px 0") }
private val docH2: String = css { fontSize("20px"); margin("26px 0 10px 0") }
private val docH3: String = css { fontSize("16px"); margin("18px 0 8px 0") }
private val docP: String = css { margin("0 0 12px 0") }
private val docPre: String = css {
    backgroundColor("#f2f3f7")
    borderRadius(Radius.SM)
    padding("12px 14px")
    overflow("auto")
    fontSize("13px")
}
private val docCode: String = css { fontFamily(Fonts.MONO); fontSize("13px") }
private val docQuote: String = css {
    declaration("border-left", "3px solid #7c5cff")
    margin("0 0 12px 0")
    padding("8px 14px")
    color("#5b6071")
}
private val docList: String = css { margin("0 0 12px 0"); padding("0 0 0 22px") }
private val docLi: String = css { margin("2px 0") }
private val docLink: String = css { color("#3f3aa0"); textDecoration("underline") }
