package dev.kairos.render

/**
 * URL attribute policy.
 *
 * A user supplied link is the classic XSS vector: `<a href="javascript:...">` executes without a
 * single `<script>` tag. Kairos validates URL attributes wherever they are written, so a value
 * that cannot be proven safe fails at build time (author code) or is dropped by the renderer
 * (untrusted markdown), never shipped.
 *
 * Relative URLs are always allowed; absolute URLs must use an allow-listed scheme. Protocol
 * relative URLs (`//host`) inherit the page scheme and are allowed.
 */
private val allowedSchemes = setOf("http", "https", "mailto", "tel")

/** Attributes whose value is interpreted as a URL by the browser. */
val URL_ATTRIBUTES: Set<String> = setOf(
    "href", "src", "action", "formaction", "poster", "cite", "data", "background", "xlink:href",
)

/**
 * Returns [value] when it is safe to emit in a URL attribute, throws otherwise.
 *
 * The check is deliberately conservative: `javascript:`, `vbscript:` and `data:` are rejected
 * (data URLs can carry HTML documents even when they look like images).
 */
fun requireSafeUrl(attribute: String, value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return value
    if (trimmed.startsWith("//")) return value
    val colon = trimmed.indexOf(':')
    if (colon <= 0) return value
    val firstSeparator = trimmed.indexOfAny(charArrayOf('/', '?', '#'))
    if (firstSeparator in 1..<colon) return value
    val scheme = trimmed.substring(0, colon).lowercase()
    require(scheme in allowedSchemes) {
        "blocked URL scheme '" + scheme + "' in " + attribute + ": " + value
    }
    return value
}

/** True when [value] can be emitted in a URL attribute. */
fun isSafeUrl(value: String): Boolean = runCatching { requireSafeUrl("url", value) }.isSuccess
