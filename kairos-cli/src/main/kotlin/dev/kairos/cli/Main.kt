package dev.kairos.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.system.exitProcess

/**
 * `kairos` command line tool: the pull based side of debugging and packaging.
 *
 *   why <class>    which Kotlin line produced this class, and what does it declare
 *   report         what the build decided, in aggregate
 *   merge          combine the maps of several modules into one stylesheet
 *   prune          drop rules whose class never appears in the shipped artifacts
 */
fun main(args: Array<String>) {
    val command = args.firstOrNull()
    val options = parseOptions(args.drop(1))
    try {
        when (command) {
            "why" -> why(options)
            "report" -> report(options)
            "merge" -> merge(options)
            "prune" -> prune(options)
            "-h", "--help", null -> usage()
            else -> {
                System.err.println("unknown command: " + command)
                usage()
                exitProcess(2)
            }
        }
    } catch (failure: ToolError) {
        System.err.println("kairos: " + failure.message)
        exitProcess(1)
    }
}

class ToolError(message: String) : RuntimeException(message)

private class Options(private val values: Map<String, List<String>>, private val positionals: List<String>) {
    fun single(name: String): String? = values[name]?.lastOrNull()
    fun many(name: String): List<String> = values[name] ?: emptyList()
    fun first(): String? = positionals.firstOrNull()
    fun requireSingle(name: String): String = single(name) ?: throw ToolError("missing required option --" + name)
}

private fun parseOptions(args: List<String>): Options {
    val values = LinkedHashMap<String, MutableList<String>>()
    val positionals = ArrayList<String>()
    var index = 0
    while (index < args.size) {
        val token = args[index]
        if (token.startsWith("--")) {
            val body = token.removePrefix("--")
            if ('=' in body) {
                values.getOrPut(body.substringBefore('=')) { ArrayList() }.add(body.substringAfter('='))
                index += 1
                continue
            }
            val next = args.getOrNull(index + 1)
            if (next != null && !next.startsWith("--")) {
                values.getOrPut(body) { ArrayList() }.add(next)
                index += 2
            } else {
                values.getOrPut(body) { ArrayList() }.add("true")
                index += 1
            }
        } else {
            positionals.add(token)
            index += 1
        }
    }
    return Options(values, positionals)
}

private fun usage() {
    println(
        listOf(
            "kairos - compiler driven style tooling",
            "",
            "usage:",
            "  kairos why <class> --map <kairos.map.json>",
            "  kairos report --map <kairos.map.json>",
            "  kairos merge --out <kairos.css> --map <a.map.json> [--map <b.map.json> ...]",
            "  kairos prune --css <in.css> [--out <out.css>] --unit <index.html> [--unit <app.js> ...]",
        ).joinToString(System.lineSeparator())
    )
}

// -- commands ----------------------------------------------------------------

private fun why(options: Options) {
    val className = options.first() ?: throw ToolError("missing class name")
    val model = loadMap(options.requireSingle("map"))
    val rule = model.rules.firstOrNull { it.className == className }
        ?: throw ToolError("class " + className + " is not in module " + model.module + " (try: kairos report)")

    println("class    " + rule.className)
    println("layer    " + rule.layer + "  (priority " + rule.layerIndex + ")")
    println("declares " + rule.declarations.size)
    rule.declarations.forEach { println("  " + it.property + ": " + it.value) }
    println("sources  " + rule.sources.size)
    rule.sources.forEach { println(renderSource(it)) }

    val related = model.conflicts.filter { it.winnerLayer == rule.layer || it.loserLayer == rule.layer }
    if (related.isEmpty()) {
        println()
        println("no layer conflicts involve this class")
    } else {
        println()
        println("layer conflicts:")
        related.forEach {
            println("  " + it.property + ": " + it.winnerLayer + " (" + it.winnerValue + ") beats " +
                it.loserLayer + " (" + it.loserValue + ")")
        }
    }
}

private fun renderSource(source: Source): String {
    val builder = StringBuilder("  " + source.file + ":" + source.line + ":" + source.column)
    val file = File(source.file)
    val line = if (file.exists() && source.line >= 1) file.readLines().getOrNull(source.line - 1) else null
    if (line == null) {
        builder.append(System.lineSeparator()).append("    (source unavailable: ").append(source.file).append(")")
        return builder.toString()
    }
    builder.append(System.lineSeparator()).append("    ").append(line.trim())
    builder.append(System.lineSeparator()).append("    ")
        .append(" ".repeat((source.column - 1).coerceAtLeast(0))).append("^")
    return builder.toString()
}

private fun report(options: Options) {
    val model = loadMap(options.requireSingle("map"))
    println("module   " + model.module)
    println("version  " + model.version)
    println("classes  " + model.rules.size)
    println("declarations " + model.rules.sumOf { it.declarations.size })
    model.layers.forEachIndexed { index, layer ->
        println("  " + index + "  " + layer.padEnd(16) + model.rules.count { it.layer == layer })
    }
    println("conflicts " + model.conflicts.size)
    model.conflicts.forEach {
        val where = it.where?.let { w -> "  at " + w.file.replace('\\', '/').substringAfterLast('/') + ":" + w.line } ?: ""
        println("  " + it.property + ": " + it.winnerLayer + " beats " + it.loserLayer + where)
    }
    println("errors " + model.errors.size)
    model.errors.forEach { println("  " + it) }
    if (model.notes.isNotEmpty()) {
        println("notes " + model.notes.size)
        model.notes.forEach { println("  " + it) }
    }
}

private fun merge(options: Options) {
    val out = File(options.requireSingle("out"))
    val maps = options.many("map")
    if (maps.isEmpty()) throw ToolError("merge needs at least one --map")
    val models = maps.map(::loadMap)

    val reference = models.first()
    models.drop(1).forEach { model ->
        if (model.version != reference.version) {
            throw ToolError("cannot merge a v" + model.version + " map with a v" + reference.version + " map (module " + model.module + ")")
        }
        if (model.layers != reference.layers) {
            throw ToolError("module " + model.module + " declares different layers than " + reference.module)
        }
        if (model.errors.isNotEmpty()) {
            throw ToolError("module " + model.module + " was compiled with errors; refusing to merge")
        }
    }

    val merged = LinkedHashMap<String, Rule>()
    models.forEach { model ->
        model.rules.forEach { rule ->
            val existing = merged[rule.className]
            if (existing == null) {
                merged[rule.className] = rule
            } else {
                if (existing.declarations != rule.declarations) {
                    throw ToolError("conflicting definitions for " + rule.className)
                }
                merged[rule.className] = existing.copy(sources = (existing.sources + rule.sources).distinctBy { it.file to it.line })
            }
        }
    }

    val layers = merged.values.map { it.layer }.distinct()
        .sortedBy { name -> models.flatMap { it.rules }.firstOrNull { it.layer == name }?.layerIndex ?: 99 }
    out.parentFile?.mkdirs()
    out.writeText(renderCss(merged.values.toList(), layers))
    println("merged " + models.size + " modules into " + out.absolutePath)
    println("  classes " + merged.size)
    layers.forEach { layer -> println("  " + layer.padEnd(16) + merged.values.count { it.layer == layer }) }
}

private fun prune(options: Options) {
    val cssFile = File(options.requireSingle("css"))
    if (!cssFile.exists()) throw ToolError("css not found: " + cssFile)
    val out = File(options.single("out") ?: cssFile.absolutePath)
    val units = options.many("unit").map(::File)
    if (units.isEmpty()) throw ToolError("prune needs at least one --unit (a file that actually ships)")
    units.forEach { if (!it.exists()) throw ToolError("unit not found: " + it) }

    val used = HashSet<String>()
    units.forEach { file -> CLASS_NAME.findAll(file.readText()).forEach { used.add(it.value) } }

    val css = cssFile.readText()
    val rulePattern = Regex("^\\s*\\.(" + CLASS_NAME.pattern + ") \\{.*$", RegexOption.MULTILINE)
    var kept = 0
    var dropped = 0
    val pruned = rulePattern.replace(css) { match ->
        if (match.groupValues[1] in used) {
            kept++
            match.value
        } else {
            dropped++
            ""
        }
    }
    if (kept + dropped == 0) {
        throw ToolError("no rules matched in " + cssFile.name + " (wrong file, or a class name format this tool does not know)")
    }
    if (used.isEmpty()) {
        throw ToolError("none of the --unit files mention a kairos class; refusing to prune everything")
    }
    out.parentFile?.mkdirs()
    out.writeText(pruned)
    println("pruned " + cssFile.name + " against " + units.size + " shipped files")
    println("  kept    " + kept)
    println("  dropped " + dropped)
    println("  written " + out.absolutePath)
}

// -- model -------------------------------------------------------------------

data class Source(val file: String, val line: Int, val column: Int)
data class Declaration(val property: String, val value: String)
data class Rule(
    val className: String,
    val layer: String,
    val layerIndex: Int,
    val declarations: List<Declaration>,
    val sources: List<Source>,
)
data class Conflict(
    val property: String,
    val winnerLayer: String,
    val winnerValue: String,
    val loserLayer: String,
    val loserValue: String,
    val where: Source?,
)
data class KairosMap(
    val version: Int,
    val module: String,
    val layers: List<String>,
    val rules: List<Rule>,
    val conflicts: List<Conflict>,
    val errors: List<String>,
    val notes: List<String>,
)

/** k + layer letter + 48 bits of hash. Keep in sync with the runtime's classNameFor. */
private val CLASS_NAME = Regex("k[robv][0-9a-f]{12}")

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun loadMap(path: String): KairosMap {
    val file = File(path)
    if (!file.exists()) throw ToolError("map not found: " + path)
    val root = json.parseToJsonElement(file.readText()).jsonObject
    val version = root.int("version")
    if (version != 3) throw ToolError("unsupported map version " + version + " in " + path + " (expected 3)")
    return KairosMap(
        version = version,
        module = root.string("module"),
        layers = root.array("layers").map { it.jsonPrimitive.content },
        rules = root.array("rules").map { element ->
            val rule = element.jsonObject
            Rule(
                className = rule.string("className"),
                layer = rule.string("layer"),
                layerIndex = rule.int("layerIndex"),
                declarations = rule.array("declarations").map { declaration ->
                    val d = declaration.jsonObject
                    Declaration(d.string("property"), d.string("value"))
                },
                sources = rule.array("sources").map { source ->
                    val s = source.jsonObject
                    Source(s.string("file"), s.int("line"), s.int("column"))
                },
            )
        },
        conflicts = root.array("conflicts").map { element ->
            val item = element.jsonObject
            Conflict(
                property = item.string("property"),
                winnerLayer = item.string("winnerLayer"),
                winnerValue = item.string("winnerValue"),
                loserLayer = item.string("loserLayer"),
                loserValue = item.string("loserValue"),
                where = item["where"]?.jsonObject?.let { w -> Source(w.string("file"), w.int("line"), w.int("column")) },
            )
        },
        errors = root.array("errors").map { it.jsonPrimitive.content },
        notes = root.array("notes").map { it.jsonPrimitive.content },
    )
}

private fun JsonObject.string(name: String): String = this[name]?.jsonPrimitive?.content ?: ""
private fun JsonObject.int(name: String): Int = this[name]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
private fun JsonObject.array(name: String): JsonArray = this[name]?.jsonArray ?: JsonArray(emptyList<JsonElement>())

private fun renderCss(rules: List<Rule>, layers: List<String>): String = buildString {
    appendLine("@layer " + layers.joinToString(", ") + ";")
    appendLine()
    layers.forEach { layer ->
        val inLayer = rules.filter { it.layer == layer }.sortedBy { it.className }
        if (inLayer.isEmpty()) return@forEach
        appendLine("@layer " + layer + " {")
        inLayer.forEach { rule ->
            appendLine("  ." + rule.className + " { " + rule.declarations.joinToString(" ") { it.property + ": " + it.value + ";" } + " }")
        }
        appendLine("}")
        appendLine()
    }
}
