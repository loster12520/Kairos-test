package dev.kairos.runtime

/**
 * Shorthand/longhand relationships between CSS properties.
 *
 * Two declarations in the same layer are only unambiguous when they cannot overlap. `color` and
 * `padding` never overlap; `padding` and `padding-top` do, because the shorthand resets the
 * longhand depending on source order. The compiler refuses such compositions instead of relying
 * on the order the declarations happen to be serialised in.
 *
 * The table is deliberately explicit: only relationships this file states are treated as
 * overlapping, so a missing entry degrades to "independent properties" rather than to a wrong
 * automatic merge.
 */
object PropertyRelations {

    private val longhands: Map<String, Set<String>> = mapOf(
        "margin" to setOf("margin-top", "margin-right", "margin-bottom", "margin-left", "margin-block", "margin-inline"),
        "padding" to setOf("padding-top", "padding-right", "padding-bottom", "padding-left", "padding-block", "padding-inline"),
        "inset" to setOf("top", "right", "bottom", "left", "inset-block", "inset-inline"),
        "border" to setOf(
            "border-width", "border-style", "border-color",
            "border-top", "border-right", "border-bottom", "border-left",
            "border-top-width", "border-right-width", "border-bottom-width", "border-left-width",
            "border-top-style", "border-right-style", "border-bottom-style", "border-left-style",
            "border-top-color", "border-right-color", "border-bottom-color", "border-left-color",
        ),
        "border-width" to setOf("border-top-width", "border-right-width", "border-bottom-width", "border-left-width"),
        "border-style" to setOf("border-top-style", "border-right-style", "border-bottom-style", "border-left-style"),
        "border-color" to setOf("border-top-color", "border-right-color", "border-bottom-color", "border-left-color"),
        "border-radius" to setOf("border-top-left-radius", "border-top-right-radius", "border-bottom-right-radius", "border-bottom-left-radius"),
        "background" to setOf(
            "background-color", "background-image", "background-position", "background-size",
            "background-repeat", "background-origin", "background-clip", "background-attachment",
        ),
        "font" to setOf("font-style", "font-variant", "font-weight", "font-stretch", "font-size", "line-height", "font-family"),
        "flex" to setOf("flex-grow", "flex-shrink", "flex-basis"),
        "gap" to setOf("row-gap", "column-gap"),
        "overflow" to setOf("overflow-x", "overflow-y"),
        "place-items" to setOf("align-items", "justify-items"),
        "place-content" to setOf("align-content", "justify-content"),
        "list-style" to setOf("list-style-type", "list-style-position", "list-style-image"),
        "outline" to setOf("outline-width", "outline-style", "outline-color"),
        "transition" to setOf("transition-property", "transition-duration", "transition-timing-function", "transition-delay"),
        "animation" to setOf("animation-name", "animation-duration", "animation-timing-function", "animation-delay", "animation-iteration-count", "animation-direction", "animation-fill-mode", "animation-play-state"),
    )

    /** Longhands of [property], or an empty set when it is not a shorthand. */
    fun longhandsOf(property: String): Set<String> = longhands[property].orEmpty()

    /** The shorthand [property] belongs to, or null when it is not a longhand in this table. */
    fun shorthandOf(property: String): String? =
        longhands.entries.firstOrNull { property in it.value }?.key

    /** True when the two properties can write the same underlying CSS longhand. */
    fun overlaps(left: String, right: String): Boolean {
        if (left == right) return true
        return right in longhandsOf(left) || left in longhandsOf(right)
    }
}
