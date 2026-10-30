package dev.kairos.runtime

/** One CSS declaration: a single (property, value) pair. */
data class Declaration(val property: String, val value: String)

/**
 * Cascade layers, in ascending priority order. A rule in a later layer always wins over one in
 * an earlier layer, no matter which element carries it.
 */
enum class Layer(val letter: Char, val cssName: String) {
    Reset('r', "kairos.reset"),
    Base('b', "kairos.base"),
    Variant('v', "kairos.variant"),
    Override('o', "kairos.override"),
}

/** Marker DSL scope used inside [css]. */
@DslMarker
annotation class KairosDsl

/**
 * Receives the declarations written inside a [css] block. A second declaration for the same
 * property replaces the first (last wins).
 */
@KairosDsl
class StyleScope internal constructor() {
    internal val declarations: LinkedHashMap<String, Declaration> = LinkedHashMap()

    /** Record a raw declaration. Every typed helper funnels through here. */
    fun declaration(property: String, value: String) {
        val cleanProperty = property.trim()
        val cleanValue = value.trim()
        if (cleanProperty.isEmpty() || cleanValue.isEmpty()) return
        requireValidCss(cleanProperty, cleanValue)
        declarations[cleanProperty] = Declaration(cleanProperty, cleanValue)
    }
}

/**
 * Values end up verbatim in the stylesheet, so a stray brace or newline would corrupt it (and
 * defeat pruning, which works line by line).
 */
internal fun requireValidCss(property: String, value: String) {
    val forbidden = charArrayOf(';', '{', '}', '\n', '\r')
    require(forbidden.none { it in property }) { "invalid CSS property: " + property }
    require(forbidden.none { it in value }) { "invalid CSS value for " + property + ": " + value }
}

/**
 * Compile-time style block.
 *
 * The whole block becomes **one class** carrying every declaration, so the number of classes is
 * proportional to the number of style blocks rather than declarations.
 *
 * Every value must be a compile-time constant. A block whose value depends on runtime input is
 * a compile error: the class list has to be identical whether or not the compiler ran. Select a
 * whole block instead:
 *
 * `
 * fun badge(kind: Kind) = when (kind) {
 *     Kind.Ok -> css { backgroundColor("#e6f4ea") }
 *     Kind.Bad -> css { backgroundColor("#fdecea") }
 * }
 * `
 */
fun css(layer: Layer = Layer.Base, block: StyleScope.() -> Unit): String {
    val scope = StyleScope()
    scope.block()
    val ordered = scope.declarations.values.toList()
    if (ordered.isEmpty()) return ""
    val className = classNameFor(layer, ordered)
    KairosRuntimeRegistry.record(className, layer, ordered)
    return className
}

/**
 * Compose style blocks. Priority comes from the cascade layers, never from argument order; two
 * blocks setting the same property in the same layer are rejected at compile time.
 */
fun styles(vararg blocks: String): String {
    val seen = LinkedHashSet<String>()
    blocks.forEach { block ->
        block.split(' ').forEach { name -> if (name.isNotEmpty()) seen.add(name) }
    }
    return seen.joinToString(" ")
}

/**
 * Deterministic class name for a group of declarations. Declarations are sorted by property so
 * the caller's order never leaks into the name; the layer is part of the hash so the same
 * declarations in another layer are another class.
 */
fun classNameFor(layer: Layer, declarations: List<Declaration>): String {
    val canonical = declarations.sortedBy { it.property }.joinToString(";") { it.property + ":" + it.value }
    return "k" + layer.letter + fnv1a64Hex(layer.cssName + "#" + canonical).take(12)
}

fun classNameFor(layer: Layer, declaration: Declaration): String = classNameFor(layer, listOf(declaration))

internal fun fnv1a64Hex(input: String): String {
    var hash = -0x340d631b7bdddcdbL // FNV-1a 64 bit offset basis
    for (ch in input) {
        hash = hash xor ch.code.toLong()
        hash *= 0x100000001b3L // FNV-1a 64 bit prime
    }
    val digits = "0123456789abcdef"
    val out = StringBuilder(16)
    for (i in 15 downTo 0) {
        out.append(digits[((hash ushr (i * 4)) and 0xFL).toInt()])
    }
    return out.toString()
}

/** Dev-only registry of the styles that reached the runtime instead of being folded. */
object KairosRuntimeRegistry {
    private val entries = LinkedHashMap<String, Pair<Layer, List<Declaration>>>()

    fun record(className: String, layer: Layer, declarations: List<Declaration>) {
        if (!entries.containsKey(className)) entries[className] = layer to declarations
    }

    fun snapshot(): Map<String, Pair<Layer, List<Declaration>>> {
        val out = LinkedHashMap<String, Pair<Layer, List<Declaration>>>()
        entries.keys.sorted().forEach { out[it] = entries.getValue(it) }
        return out
    }

    fun size(): Int = entries.size

    fun clear() = entries.clear()
}

// -- units, scales and typed helpers ------------------------------------------

/** Common spacing scale. `const val` so the compiler reads the value straight from IR. */
object Spacing {
    const val NONE = "0"
    const val XS = "4px"
    const val SM = "8px"
    const val MD = "12px"
    const val LG = "16px"
    const val XL = "24px"
    const val XXL = "32px"
}

object Radius {
    const val SM = "4px"
    const val MD = "8px"
    const val LG = "14px"
    const val FULL = "999px"
}

object Fonts {
    const val SANS = "ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, sans-serif"
    const val MONO = "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"
}

/** Pixel shorthand: `12.px` is `\"12px\"`. */
inline val Int.px: String
    get() = "" + this + "px"

inline val Int.pct: String
    get() = "" + this + "%"

inline fun StyleScope.display(value: String) = declaration("display", value)
inline fun StyleScope.width(value: String) = declaration("width", value)
inline fun StyleScope.height(value: String) = declaration("height", value)
inline fun StyleScope.maxWidth(value: String) = declaration("max-width", value)
inline fun StyleScope.minHeight(value: String) = declaration("min-height", value)
inline fun StyleScope.margin(value: String) = declaration("margin", value)
inline fun StyleScope.marginTop(value: String) = declaration("margin-top", value)
inline fun StyleScope.marginBottom(value: String) = declaration("margin-bottom", value)
inline fun StyleScope.padding(value: String) = declaration("padding", value)
inline fun StyleScope.paddingTop(value: String) = declaration("padding-top", value)
inline fun StyleScope.paddingBottom(value: String) = declaration("padding-bottom", value)
inline fun StyleScope.gap(value: String) = declaration("gap", value)
inline fun StyleScope.color(value: String) = declaration("color", value)
inline fun StyleScope.backgroundColor(value: String) = declaration("background-color", value)
inline fun StyleScope.fontFamily(value: String) = declaration("font-family", value)
inline fun StyleScope.fontSize(value: String) = declaration("font-size", value)
inline fun StyleScope.fontWeight(value: String) = declaration("font-weight", value)
inline fun StyleScope.lineHeight(value: String) = declaration("line-height", value)
inline fun StyleScope.letterSpacing(value: String) = declaration("letter-spacing", value)
inline fun StyleScope.border(value: String) = declaration("border", value)
inline fun StyleScope.borderTop(value: String) = declaration("border-top", value)
inline fun StyleScope.borderBottom(value: String) = declaration("border-bottom", value)
inline fun StyleScope.borderRadius(value: String) = declaration("border-radius", value)
inline fun StyleScope.opacity(value: String) = declaration("opacity", value)
inline fun StyleScope.cursor(value: String) = declaration("cursor", value)
inline fun StyleScope.alignItems(value: String) = declaration("align-items", value)
inline fun StyleScope.justifyContent(value: String) = declaration("justify-content", value)
inline fun StyleScope.textAlign(value: String) = declaration("text-align", value)
inline fun StyleScope.textDecoration(value: String) = declaration("text-decoration", value)
inline fun StyleScope.flexDirection(value: String) = declaration("flex-direction", value)
inline fun StyleScope.flexWrap(value: String) = declaration("flex-wrap", value)
inline fun StyleScope.gridTemplateColumns(value: String) = declaration("grid-template-columns", value)
inline fun StyleScope.overflow(value: String) = declaration("overflow", value)
inline fun StyleScope.whiteSpace(value: String) = declaration("white-space", value)
inline fun StyleScope.listStyle(value: String) = declaration("list-style", value)
