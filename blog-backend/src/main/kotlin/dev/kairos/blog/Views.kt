package dev.kairos.blog

import dev.kairos.htmx.fragment
import dev.kairos.htmx.hxConfirm
import dev.kairos.htmx.hxGet
import dev.kairos.htmx.hxPost
import dev.kairos.htmx.hxInclude
import dev.kairos.htmx.hxPushUrl
import dev.kairos.htmx.hxSwap
import dev.kairos.htmx.hxTarget
import dev.kairos.htmx.page
import dev.kairos.markdown.markdown
import dev.kairos.render.ElementNode
import dev.kairos.render.a
import dev.kairos.render.attribute
import dev.kairos.render.button
import dev.kairos.render.div
import dev.kairos.render.el
import dev.kairos.render.form
import dev.kairos.render.article
import dev.kairos.render.h3
import dev.kairos.render.h2
import dev.kairos.render.h1
import dev.kairos.render.flagAttribute
import dev.kairos.render.header
import dev.kairos.render.href
import dev.kairos.render.input
import dev.kairos.render.label
import dev.kairos.render.main
import dev.kairos.render.name
import dev.kairos.render.nav
import dev.kairos.render.p
import dev.kairos.render.placeholder
import dev.kairos.render.span
import dev.kairos.render.textarea
import dev.kairos.render.type
import dev.kairos.render.value
import dev.kairos.render.value as valueAttr
import dev.kairos.runtime.Fonts
import dev.kairos.runtime.Layer
import dev.kairos.runtime.Radius
import dev.kairos.runtime.Spacing
import dev.kairos.runtime.alignItems
import dev.kairos.runtime.backgroundColor
import dev.kairos.runtime.border
import dev.kairos.runtime.borderBottom
import dev.kairos.runtime.borderRadius
import dev.kairos.runtime.color
import dev.kairos.runtime.css
import dev.kairos.runtime.cursor
import dev.kairos.runtime.display
import dev.kairos.runtime.fontFamily
import dev.kairos.runtime.fontSize
import dev.kairos.runtime.fontWeight
import dev.kairos.runtime.gap
import dev.kairos.runtime.justifyContent
import dev.kairos.runtime.lineHeight
import dev.kairos.runtime.margin
import dev.kairos.runtime.maxWidth
import dev.kairos.runtime.padding
import dev.kairos.runtime.styles
import dev.kairos.runtime.textDecoration
import dev.kairos.runtime.whiteSpace
import dev.kairos.runtime.width

/**
 * The blog templates.
 *
 * Colours are CSS variables (theme.css) so a light/dark switch is one class on <html>; the rest
 * of the styling is Kairos generated classes.
 */

var ElementNode.dataKairosId: String by attribute("data-kairos-id")
var ElementNode.dataKairosMount: String by attribute("data-kairos-mount")
var ElementNode.required: Boolean by flagAttribute("required")
var ElementNode.rows: String by attribute("rows")

// -- styles -------------------------------------------------------------------

val pageStyle: String = css {
    fontFamily(Fonts.SANS)
    lineHeight("1.7")
    margin(Spacing.NONE)
    padding(Spacing.NONE)
    backgroundColor("var(--bg)")
    color("var(--fg)")
}

val shellStyle: String = css {
    maxWidth("860px")
    margin("0 auto")
    padding("0 24px 72px 24px")
}

val headerStyle: String = css {
    display("flex")
    alignItems("center")
    justifyContent("space-between")
    gap(Spacing.LG)
    padding("20px 0")
    borderBottom("1px solid var(--border)")
}

val brandStyle: String = css {
    fontSize("18px")
    fontWeight("760")
    color("var(--fg)")
}

val navStyle: String = css {
    display("flex")
    gap(Spacing.MD)
    alignItems("center")
}

val heroStyle: String = css {
    padding("48px 0 8px 0")
}

val heroTitleStyle: String = css {
    fontSize("44px")
    lineHeight("1.15")
    margin("0 0 8px 0")
    fontWeight("800")
}

val mutedStyle: String = css {
    color("var(--muted)")
    fontSize("14px")
}

val cardStyle: String = css {
    backgroundColor("var(--surface)")
    border("1px solid var(--border)")
    borderRadius(Radius.LG)
    padding(Spacing.XL)
    margin("16px 0 0 0")
}

val cardTitleStyle: String = css {
    fontSize("20px")
    fontWeight("700")
    margin("0 0 6px 0")
    color("var(--fg)")
}

val linkStyle: String = css {
    color("var(--accent)")
    textDecoration("none")
    cursor("pointer")
}

val buttonStyle: String = css {
    declaration("font", "inherit")
    fontSize("14px")
    padding("8px 14px")
    borderRadius(Radius.FULL)
    border("1px solid var(--border)")
    backgroundColor("var(--surface)")
    color("var(--fg)")
    cursor("pointer")
}

val inputStyle: String = css {
    declaration("font", "inherit")
    padding("9px 12px")
    borderRadius(Radius.MD)
    border("1px solid var(--border)")
    backgroundColor("var(--surface)")
    color("var(--fg)")
    width("100%")
}

val filterInputStyle: String = css {
    declaration("font", "inherit")
    padding("6px 12px")
    borderRadius(Radius.FULL)
    border("1px solid var(--border)")
    backgroundColor("var(--surface)")
    color("var(--fg)")
    width("190px")
}

val fieldStyle: String = css {
    display("grid")
    gap(Spacing.XS)
    margin("14px 0")
}

val proseStyle: String = css {
    whiteSpace("pre-wrap")
}

val commentStyle: String = css {
    border("1px solid var(--border)")
    borderRadius(Radius.MD)
    padding(Spacing.MD)
    margin("12px 0 0 0")
    backgroundColor("var(--surface)")
}

val cardActionsStyle: String = css {
    display("flex")
    gap(Spacing.SM)
    alignItems("center")
    margin("10px 0 0 0")
}

val sortBarStyle: String = css {
    display("flex")
    gap(Spacing.SM)
    alignItems("center")
    margin("0 0 12px 0")
    fontSize("13px")
    color("#5b6071")
}

val smallButtonStyle: String = css {
    declaration("font", "inherit")
    fontSize("13px")
    padding("5px 10px")
    borderRadius(Radius.SM)
    border("1px solid #d5d7dd")
    backgroundColor("#ffffff")
    color("#16181d")
    cursor("pointer")
    hover {
        backgroundColor("#f2f3f7")
    }
}

val formErrorStyle: String = css(Layer.Override) {
    color("#b42318")
    backgroundColor("#fdecea")
    borderRadius(Radius.SM)
    padding("8px 12px")
    margin("0 0 12px 0")
}

val commentaryHeaderStyle: String = css(Layer.Override) {
    display("flex")
    gap(Spacing.SM)
    alignItems("center")
    margin("0 0 6px 0")
}

// -- page ---------------------------------------------------------------------

fun blogPage(
    posts: List<Post>,
    commentCounts: Map<Long, Int>,
    csrfToken: String,
    openPost: Post? = null,
    openComments: List<Comment> = emptyList(),
): String {
    val tree = el("div") {
        style = pageStyle
        input {
            type = "hidden"
            id = "csrf-token"
            name = "_csrf"
            value = csrfToken
        }
        div {
            style = shellStyle
            header {
                style = headerStyle
                a {
                    style = brandStyle
                    href = "/"
                    "kairos blog".text
                }
                nav {
                    style = navStyle
                    input {
                        style = filterInputStyle
                        dataKairosId = "filter"
                        dataKairosMount = "filter"
                        type = "search"
                        name = "filter"
                        placeholder = "Filter posts..."
                    }
                    button {
                        style = buttonStyle
                        dataKairosId = "theme-toggle"
                        dataKairosMount = "theme-toggle"
                        type = "button"
                        "Toggle theme".text
                    }
                }
            }
            main {
                div {
                    style = heroStyle
                    h1 { style = heroTitleStyle; "A blog written in Kotlin".text }
                    p {
                        style = mutedStyle
                        "Ktor renders the pages, htmx swaps the fragments, Kairos computes every class at compile time, and a small Kotlin/JS bundle adds the reactive touches.".text
                    }
                }
                div {
                    style = navStyle
                    button {
                        style = buttonStyle
                        dataKairosId = "new-post"
                        type = "button"
                        hxGet = "/new"
                        hxTarget = "#detail"
                        hxSwap = "innerHTML"
                        "New post".text
                    }
                }
                div {
                    id = "detail"
                    openPost?.let { add(postDetailElement(it, openComments, csrfToken)) }
                }
                div {
                    style = sortBarStyle
                    "Sort:".text
                    button {
                        style = smallButtonStyle
                        type = "button"
                        hxGet = "/posts/list"
                        hxTarget = "#post-list"
                        hxSwap = "outerHTML"
                        "Newest".text
                    }
                    button {
                        style = smallButtonStyle
                        type = "button"
                        hxGet = "/posts/list?sort=comments"
                        hxTarget = "#post-list"
                        hxSwap = "outerHTML"
                        "Most comments".text
                    }
                }
                add(postList(posts, commentCounts))
            }
        }
    }
    return page(
        title = "kairos blog",
        stylesheets = listOf("/theme.css", "/kairos.css"),
        bodyNode = tree,
        scripts = listOf(
            "/htmx.js",
            "/kotlin-kotlin-stdlib.js",
            "/kairos-kairos-core.js",
            "/kairos-kairos-browser.js",
            "/kairos-blog-frontend.js",
        ),
    )
}

fun postList(posts: List<Post>, commentCounts: Map<Long, Int>, oob: Boolean = false): ElementNode = div {
    id = "post-list"
    dataKairosMount = "post-list"
    // When a create replaces the list in place, htmx must also swap the out-of-band copy.
    if (oob) attributes["hx-swap-oob"] = "true"
    posts.forEach { post ->
        div {
            style = cardStyle
            attributes["data-title"] = post.title.lowercase()
            h2 {
                style = cardTitleStyle
                a {
                    style = linkStyle
                    href = "/posts/" + post.id
                    hxGet = "/posts/" + post.id
                    hxTarget = "#detail"
                    hxSwap = "innerHTML"
                    hxPushUrl = "true"
                    post.title.text
                }
            }
            p {
                style = mutedStyle
                (post.createdAt.take(10) + " · " + (commentCounts[post.id] ?: 0) + " comments").text
            }
            p { style = proseStyle; excerpt(post.body).text }
            div {
                style = cardActionsStyle
                button {
                    style = smallButtonStyle
                    type = "button"
                    hxGet = "/posts/" + post.id + "/edit"
                    hxTarget = "#detail"
                    hxSwap = "innerHTML"
                    "Edit".text
                }
                button {
                    style = smallButtonStyle
                    type = "button"
                    hxPost = "/posts/" + post.id + "/delete"
                    hxInclude = "#csrf-token"
                    hxTarget = "#post-list"
                    hxSwap = "outerHTML"
                    hxConfirm = "Delete this post?"
                    "Delete".text
                }
            }
        }
    }
}

private fun excerpt(body: String, limit: Int = 180): String {
    val plain = body.lines().filterNot { it.startsWith("#") || it.startsWith("> ") }.joinToString(" ")
    return if (plain.length <= limit) plain else plain.take(limit).trimEnd() + "..."
}

fun postDetailElement(post: Post, comments: List<Comment>, csrfToken: String): ElementNode = el("div") {
    article {
        style = cardStyle
        h2 {
            style = cardTitleStyle
            attributes["id"] = "detail-title"
            attributes["tabindex"] = "-1"
            post.title.text
        }
        p { style = mutedStyle; (post.createdAt.take(10) + " · " + post.slug).text }
        div {
            style = proseStyle
            markdown(post.body)
        }
    }
    div {
        style = cardActionsStyle
        button {
            style = smallButtonStyle
            type = "button"
            hxGet = "/posts/" + post.id + "/edit"
            hxTarget = "#detail"
            hxSwap = "innerHTML"
            "Edit post".text
        }
    }
    add(commentsSection(post.id, comments, csrfToken))
}

fun postDetail(post: Post, comments: List<Comment>, csrfToken: String): String =
    fragment(postDetailElement(post, comments, csrfToken))

private fun commentForm(
    postId: Long,
    csrfToken: String,
    error: String = "",
    author: String = "",
    body: String = "",
): ElementNode = el("form") {
    style = cardStyle
    hxPost = "/posts/" + postId + "/comments"
    hxTarget = "#comments"
    hxSwap = "outerHTML"
    h2 { style = cardTitleStyle; "Add a comment".text }
    if (error.isNotEmpty()) {
        p { style = formErrorStyle; error.text }
    }
    p { style = mutedStyle; "Markdown is supported: **bold**, *italic*, lists, code.".text }
    input {
        type = "hidden"
        name = "_csrf"
        value = csrfToken
    }
    div {
        style = fieldStyle
        label { "Name".text }
        input {
            name = "author"
            type = "text"
            required = true
            style = inputStyle
            value = author
        }
    }
    div {
        style = fieldStyle
        label { "Comment".text }
        textarea {
            name = "body"
            required = true
            rows = "4"
            style = inputStyle
            body.text
        }
    }
    button { style = buttonStyle; type = "submit"; "Post comment".text }
}

fun commentsSection(
    postId: Long,
    comments: List<Comment>,
    csrfToken: String,
    error: String = "",
    author: String = "",
    body: String = "",
): ElementNode = div {
    id = "comments"
    // Screen readers announce appended comments without stealing focus.
    attributes["role"] = "status"
    attributes["aria-live"] = "polite"
    comments.forEach { comment ->
        div {
            style = commentStyle
            div {
                style = commentaryHeaderStyle
                span { style = mutedStyle; comment.author.text }
                span { style = mutedStyle; ("· " + comment.createdAt.take(16).replace('T', ' ')).text }
            }
            div {
                style = proseStyle
                markdown(comment.body)
            }
        }
    }
    add(commentForm(postId, csrfToken, error, author, body))
}

fun editPostForm(
    post: Post,
    csrfToken: String,
    title: String = post.title,
    body: String = post.body,
    error: String = "",
): String = fragment(el("div") {
    id = "edit-post-form"
    form {
        style = cardStyle
        hxPost = "/posts/" + post.id
        hxTarget = "#detail"
        hxSwap = "innerHTML"
        h2 { style = cardTitleStyle; "Edit post".text }
        if (error.isNotEmpty()) {
            p { style = formErrorStyle; error.text }
        }
        input {
            type = "hidden"
            name = "_csrf"
            value = csrfToken
        }
        div {
            style = fieldStyle
            label { "Title".text }
            input {
                name = "title"
                type = "text"
                required = true
                style = inputStyle
                value = title
            }
        }
        div {
            style = fieldStyle
            label { "Body".text }
            textarea {
                name = "body"
                required = true
                rows = "8"
                style = inputStyle
                body.text
            }
        }
        div {
            style = cardActionsStyle
            button { style = buttonStyle; type = "submit"; "Save".text }
            button {
                style = smallButtonStyle
                type = "button"
                hxGet = "/posts/" + post.id
                hxTarget = "#detail"
                hxSwap = "innerHTML"
                "Cancel".text
            }
        }
    }
})

fun newPostForm(
    csrfToken: String,
    title: String = "",
    body: String = "",
    error: String = "",
): String = fragment(el("div") {
    id = "new-post-form"
    form {
        style = cardStyle
        hxPost = "/posts"
        hxTarget = "#new-post-form"
        hxSwap = "outerHTML"
        h2 { style = cardTitleStyle; "New post".text }
        if (error.isNotEmpty()) {
            p { style = formErrorStyle; error.text }
        }
        p { style = mutedStyle; "The body is rendered as Markdown.".text }
        input {
            type = "hidden"
            name = "_csrf"
            value = csrfToken
        }
        div {
            style = fieldStyle
            label { "Title".text }
            input {
                name = "title"
                type = "text"
                required = true
                style = inputStyle
                value = title
            }
        }
        div {
            style = fieldStyle
            label { "Body".text }
            textarea {
                name = "body"
                required = true
                rows = "8"
                style = inputStyle
            }
        }
        button { style = buttonStyle; type = "submit"; "Publish".text }
    }
})
