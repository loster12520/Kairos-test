package dev.kairos.compiler

import dev.kairos.runtime.Declaration
import dev.kairos.runtime.Layer
import dev.kairos.runtime.classNameFor
import java.io.File

/** A resolved source position: file plus 1-based line and column. */
data class SourceRef(val file: String, val line: Int, val column: Int, val offset: Int)

/** One class accumulated during a compilation: a group of declarations in one layer. */
class AccumulatedRule(
    val className: String,
    val layer: Layer,
    val declarations: List<Declaration>,
) {
    val sources: MutableSet<SourceRef> = sortedSetOf(compareBy({ it.file }, { it.line }, { it.column }))
}

/** A property set in two different layers by one composition: the later layer wins. */
data class Conflict(
    val property: String,
    val winnerLayer: String,
    val winnerValue: String,
    val loserLayer: String,
    val loserValue: String,
    val where: SourceRef?,
)

/**
 * Accumulates the folded style blocks of one compilation and writes the deterministic artifacts.
 *
 * Hard failures (an unresolvable block, an invalid declaration, a hash collision, an ambiguous
 * composition) are collected and turned into a build failure by [throwIfFailed].
 */
class KairosCollector(
    private val outputDir: File,
    private val moduleName: String = "unknown",
) {

    private val rules = sortedMapOf<String, AccumulatedRule>()
    private val errors = LinkedHashSet<String>()
    private val notes = ArrayList<String>()
    private val conflicts = ArrayList<Conflict>()
    private val fileCache = HashMap<String, String?>()
    private var sawSources = false

    val module: String get() = moduleName
    fun hasErrors(): Boolean = errors.isNotEmpty()

    @Synchronized
    fun markSources(present: Boolean) {
        if (present) sawSources = true
    }

    /** Registers a whole style block as one class and returns that class name. */
    @Synchronized
    fun register(layer: Layer, declarations: List<Declaration>, file: String, offset: Int): String {
        val clean = declarations
            .map { Declaration(it.property.trim(), it.value.trim()) }
            .sortedBy { it.property }
        if (clean.isEmpty()) {
            errors.add(position(file, offset) + " - style block declares nothing")
            return ""
        }
        clean.forEach { declaration ->
            if (!isValidCssFragment(declaration.property) || !isValidCssFragment(declaration.value)) {
                errors.add(position(file, offset) + " - declaration would corrupt the stylesheet: " + declaration.property + ": " + declaration.value)
            }
        }
        val className = classNameFor(layer, clean)
        val existing = rules[className]
        if (existing != null && (existing.declarations != clean || existing.layer != layer)) {
            errors.add(
                position(file, offset) + " - class name collision on " + className + ": two different declaration groups hash to the same name"
            )
            return className
        }
        val rule = existing ?: AccumulatedRule(className, layer, clean).also { rules[className] = it }
        rule.sources += sourceRef(file, offset)
        return className
    }

    @Synchronized
    fun recordConflict(
        property: String,
        left: Pair<Layer, Declaration>,
        right: Pair<Layer, Declaration>,
        file: String,
        offset: Int,
    ) {
        val (winnerLayer, winner) = if (left.first.ordinal >= right.first.ordinal) left else right
        val (loserLayer, loser) = if (left.first.ordinal >= right.first.ordinal) right else left
        conflicts += Conflict(property, winnerLayer.cssName, winner.value, loserLayer.cssName, loser.value, sourceRef(file, offset))
    }

    @Synchronized
    fun reportError(file: String, offset: Int, reason: String) {
        errors.add(position(file, offset) + " - " + reason)
    }

    @Synchronized
    fun reportNote(message: String) {
        notes += message
    }

    @Synchronized
    fun flush() {
        if (!sawSources) return
        // Always write once sources were seen: a module whose styles were deleted must not keep
        // serving the previous build's stylesheet.
        outputDir.mkdirs()
        writeAtomically(File(outputDir, "kairos.css"), renderCss())
        writeAtomically(File(outputDir, "kairos.map.json"), renderMap())
        writeAtomically(File(outputDir, "kairos-report.txt"), renderReport())
    }

    fun throwIfFailed() {
        if (errors.isEmpty()) return
        throw IllegalStateException(
            "kairos found " + errors.size + " problem(s) in module " + moduleName + ":" +
                System.lineSeparator() + errors.joinToString(System.lineSeparator()) { "  " + it }
        )
    }

    private fun isValidCssFragment(text: String): Boolean =
        text.none { it == ';' || it == '{' || it == '}' || it == '\n' || it == '\r' }

    private fun position(file: String, offset: Int): String {
        val ref = sourceRef(file, offset)
        return ref.file.replace('\\', '/').substringAfterLast('/') + ":" + ref.line + ":" + ref.column
    }

    // -- css -----------------------------------------------------------------

    private fun renderCss(): String = buildString {
        val layers = Layer.entries
        appendLine("@layer " + layers.joinToString(", ") { it.cssName } + ";")
        appendLine()
        layers.forEach { layer ->
            val inLayer = rules.values.filter { it.layer == layer }
            if (inLayer.isEmpty()) return@forEach
            appendLine("@layer " + layer.cssName + " {")
            inLayer.forEach { rule ->
                val body = rule.declarations.joinToString(" ") {
                    it.property + ": " + it.value + ";"
                }
                appendLine("  ." + rule.className + " { " + body + " }")
            }
            appendLine("}")
            appendLine()
        }
        if (rules.isEmpty()) appendLine("/* no rules */")
    }

    // -- map -----------------------------------------------------------------

    private fun renderMap(): String = buildString {
        appendLine("{")
        appendLine("  \"version\": 3,")
        appendLine("  \"module\": \"" + escape(moduleName) + "\",")
        appendLine("  \"layers\": [" + Layer.entries.joinToString(", ") { "\"" + it.cssName + "\"" } + "],")
        appendLine("  \"rules\": [")
        val list = rules.values.toList()
        list.forEachIndexed { index, rule ->
            append("    { \"className\": \"").append(rule.className).append("\"")
            append(", \"layer\": \"").append(rule.layer.cssName).append("\"")
            append(", \"layerIndex\": ").append(rule.layer.ordinal)
            append(", \"declarations\": [")
            append(rule.declarations.joinToString(", ") { declaration ->
                "{ \"property\": \"" + escape(declaration.property) + "\", \"value\": \"" + escape(declaration.value) + "\" }"
            })
            append("], \"sources\": [")
            append(rule.sources.joinToString(", ") { source ->
                "{ \"file\": \"" + escape(source.file) + "\", \"line\": " + source.line +
                    ", \"column\": " + source.column + ", \"offset\": " + source.offset + " }"
            })
            append("] }")
            if (index != list.lastIndex) append(',')
            appendLine()
        }
        appendLine("  ],")
        appendLine("  \"conflicts\": [")
        conflicts.forEachIndexed { index, conflict ->
            append("    { \"property\": \"").append(escape(conflict.property)).append("\"")
            append(", \"winnerLayer\": \"").append(conflict.winnerLayer).append("\"")
            append(", \"winnerValue\": \"").append(escape(conflict.winnerValue)).append("\"")
            append(", \"loserLayer\": \"").append(conflict.loserLayer).append("\"")
            append(", \"loserValue\": \"").append(escape(conflict.loserValue)).append("\"")
            conflict.where?.let {
                append(", \"where\": { \"file\": \"").append(escape(it.file)).append("\", \"line\": ")
                    .append(it.line).append(", \"column\": ").append(it.column).append(" }")
            }
            append(" }")
            if (index != conflicts.lastIndex) append(',')
            appendLine()
        }
        appendLine("  ],")
        appendLine("  \"errors\": [")
        val errorList = errors.toList()
        errorList.forEachIndexed { index, error ->
            append("    \"").append(escape(error)).append("\"")
            if (index != errorList.lastIndex) append(',')
            appendLine()
        }
        appendLine("  ],")
        appendLine("  \"notes\": [")
        notes.forEachIndexed { index, note ->
            append("    \"").append(escape(note)).append("\"")
            if (index != notes.lastIndex) append(',')
            appendLine()
        }
        appendLine("  ]")
        appendLine("}")
    }

    // -- report ---------------------------------------------------------------

    private fun renderReport(): String = buildString {
        appendLine("# kairos report - module " + moduleName)
        appendLine()
        appendLine("## classes (" + rules.size + ")")
        rules.values.forEach { rule ->
            appendLine(rule.className + "  " + rule.layer.cssName + "  " + rule.declarations.joinToString("; ") { it.property + ":" + it.value })
        }
        appendLine("## errors (" + errors.size + ")")
        if (errors.isEmpty()) appendLine("none") else errors.forEach { appendLine(it) }
        appendLine()
        appendLine("## conflicts (" + conflicts.size + ")")
        if (conflicts.isEmpty()) {
            appendLine("none")
        } else {
            conflicts.forEach {
                val where = it.where?.let { w -> w.file.replace('\\', '/').substringAfterLast('/') + ":" + w.line } ?: "?"
                appendLine(it.property + ": " + it.winnerLayer + " (" + it.winnerValue + ") beats " + it.loserLayer + " (" + it.loserValue + ") at " + where)
            }
        }
        appendLine()
        appendLine("## notes (" + notes.size + ")")
        if (notes.isEmpty()) appendLine("none") else notes.forEach { appendLine(it) }
    }

    // -- helpers --------------------------------------------------------------

    @Synchronized
    private fun sourceRef(file: String, offset: Int): SourceRef {
        if (offset < 0) return SourceRef(file, 0, 0, offset)
        val text = fileCache.getOrPut(file) { runCatching { File(file).readText() }.getOrNull() }
            ?: return SourceRef(file, 0, 0, offset)
        val safeOffset = offset.coerceIn(0, text.length)
        var line = 1
        var lineStart = 0
        for (i in 0 until safeOffset) {
            if (text[i] == '\n') {
                line++
                lineStart = i + 1
            }
        }
        return SourceRef(file, line, safeOffset - lineStart + 1, offset)
    }

    private fun escape(value: String): String = buildString {
        value.forEach { ch ->
            when {
                ch == '\\' -> append("\\\\")
                ch == '"' -> append("\\\"")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch < ' ' -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else -> append(ch)
            }
        }
    }

    private fun writeAtomically(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + "." + System.nanoTime() + ".tmp")
        tmp.writeText(content)
        try {
            java.nio.file.Files.move(
                tmp.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (failure: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
