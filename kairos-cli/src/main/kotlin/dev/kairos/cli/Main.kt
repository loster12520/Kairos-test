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
            "init" -> initProject(options)
            "check" -> checkProject(options)
            "doctor" -> doctor(options)
            "coverage" -> coverage(options)
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

internal class ToolError(message: String) : RuntimeException(message)

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
            "  kairos merge --out <kairos.css> [--map-out <kairos.map.json>] --map <a.map.json> [--map <b.map.json> ...]",
            "  kairos prune --css <in.css> [--out <out.css>] [--map <kairos.map.json>] --unit <index.html> [--unit <app.js> ...]",
            "  kairos init <directory> [--template static|ktor-htmx|ktor-incremental]",
            "  kairos check --map <kairos.map.json> --css <kairos.css> [--strict]",
            "  kairos doctor",
            "  kairos coverage --map <kairos.map.json>",
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
    if (rule.conditions.isNotEmpty()) {
        rule.conditions.forEach { println("under    " + it.kind + " " + it.value) }
    }
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
    println("coverage " + model.coverage.rules + " rules, " + model.coverage.conditionalRules + " conditional, " + model.coverage.fallbackBlocks + " fallback blocks")
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

    // Keep the full layer declaration even when a layer has no rules: the merged sheet must be
    // shaped exactly like a single-module sheet.
    val layers = reference.layers
    out.parentFile?.mkdirs()
    out.writeText(renderCss(merged.values.toList(), layers))

    // The merged manifest travels with the merged stylesheet: debugging and library assets
    // both need to know which module a rule came from after the build.
    val mapOut = options.single("map-out")?.let(::File)
        ?: File(out.parentFile ?: File("."), "kairos.map.json")
    mapOut.parentFile?.mkdirs()
    val conflicts = mergeConflicts(models)
    val coverage = models.fold(Coverage.EMPTY) { total, model ->
        Coverage(
            total.rules + model.coverage.rules,
            total.conditionalRules + model.coverage.conditionalRules,
            total.fallbackBlocks + model.coverage.fallbackBlocks,
        )
    }
    mapOut.writeText(renderMap(merged.values.toList(), layers, conflicts, models.map { it.module }, coverage))
    println("merged " + models.size + " modules into " + out.absolutePath)
    println("  classes " + merged.size)
    layers.forEach { layer -> println("  " + layer.padEnd(16) + merged.values.count { it.layer == layer }) }
    println("  map        " + mapOut.absolutePath)
    println("  conflicts  " + conflicts.size)
}

private fun mergeConflicts(models: List<KairosMap>): List<Conflict> {
    val seen = LinkedHashSet<String>()
    val out = ArrayList<Conflict>()
    models.forEach { model ->
        model.conflicts.forEach { conflict ->
            val key = listOf(
                conflict.property,
                conflict.winnerLayer,
                conflict.winnerValue,
                conflict.loserLayer,
                conflict.loserValue,
                conflict.where?.file,
                conflict.where?.line,
            ).joinToString("|")
            if (seen.add(key)) out += conflict
        }
    }
    return out
}

private fun prune(options: Options) {
    val cssFile = File(options.requireSingle("css"))
    if (!cssFile.exists()) throw ToolError("css not found: " + cssFile)
    val out = File(options.single("out") ?: cssFile.absolutePath)
    val units = options.many("unit").map(::File)
    val mapFile = options.single("map")?.let(::File)
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
    if (mapFile != null && mapFile.exists()) {
        // Keep the merged manifest in step with the shipped stylesheet, otherwise `why` resolves
        // classes that no longer exist after pruning.
        val model = loadMap(mapFile.absolutePath)
        val shipped = model.rules.filter { it.className in used }
        val coverage = model.coverage.copy(
            rules = shipped.size,
            conditionalRules = shipped.count { it.conditions.isNotEmpty() },
        )
        mapFile.writeText(renderMap(shipped, model.layers, model.conflicts, model.modules, coverage))
        println("  map      " + mapFile.absolutePath + " (" + shipped.size + " rules)")
    }
}

// -- model -------------------------------------------------------------------

internal data class Source(val file: String, val line: Int, val column: Int)
internal data class Declaration(val property: String, val value: String)
internal data class Condition(val kind: String, val value: String)
internal data class Coverage(val rules: Int, val conditionalRules: Int, val fallbackBlocks: Int) {
    companion object {
        val EMPTY = Coverage(0, 0, 0)
    }
}
internal data class Rule(
    val className: String,
    val layer: String,
    val layerIndex: Int,
    val conditions: List<Condition>,
    val declarations: List<Declaration>,
    val sources: List<Source>,
)
internal data class Conflict(
    val property: String,
    val winnerLayer: String,
    val winnerValue: String,
    val loserLayer: String,
    val loserValue: String,
    val where: Source?,
)
internal data class KairosMap(
    val version: Int,
    val module: String,
    val modules: List<String>,
    val layers: List<String>,
    val coverage: Coverage,
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
    if (version != 3 && version != 4) {
        throw ToolError("unsupported map version " + version + " in " + path + " (expected 3 or 4)")
    }
    val module = root.string("module")
    val modules = root.array("modules").map { it.jsonPrimitive.content }.ifEmpty { listOf(module) }
    val coverage = root["coverage"]?.jsonObject?.let { element ->
        Coverage(element.int("rules"), element.int("conditionalRules"), element.int("fallbackBlocks"))
    } ?: Coverage.EMPTY
    return KairosMap(
        version = version,
        module = module,
        modules = modules,
        layers = root.array("layers").map { it.jsonPrimitive.content },
        coverage = coverage,
        rules = root.array("rules").map { element ->
            val rule = element.jsonObject
            Rule(
                className = rule.string("className"),
                layer = rule.string("layer"),
                layerIndex = rule.int("layerIndex"),
                conditions = rule.array("conditions").map { condition ->
                    val c = condition.jsonObject
                    Condition(c.string("kind"), c.string("value"))
                },
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

private fun renderMap(
    rules: List<Rule>,
    layers: List<String>,
    conflicts: List<Conflict>,
    modules: List<String>,
    coverage: Coverage,
): String = buildString {
    appendLine("{")
    appendLine("  \"version\": 4,")
    appendLine("  \"module\": \"merged\",")
    appendLine("  \"modules\": [" + modules.joinToString(", ") { "\"" + escape(it) + "\"" } + "],")
    appendLine("  \"layers\": [" + layers.joinToString(", ") { "\"" + escape(it) + "\"" } + "],")
    appendLine(
        "  \"coverage\": { \"rules\": " + coverage.rules +
            ", \"conditionalRules\": " + coverage.conditionalRules +
            ", \"fallbackBlocks\": " + coverage.fallbackBlocks + " },"
    )
    appendLine("  \"rules\": [")
    val sorted = rules.sortedBy { it.className }
    sorted.forEachIndexed { index, rule ->
        append("    { \"className\": \"").append(escape(rule.className)).append("\"")
        append(", \"layer\": \"").append(escape(rule.layer)).append("\"")
        append(", \"layerIndex\": ").append(rule.layerIndex)
        append(", \"conditions\": [")
        append(rule.conditions.joinToString(", ") {
            "{ \"kind\": \"" + escape(it.kind) + "\", \"value\": \"" + escape(it.value) + "\" }"
        })
        append("], \"declarations\": [")
        append(rule.declarations.joinToString(", ") {
            "{ \"property\": \"" + escape(it.property) + "\", \"value\": \"" + escape(it.value) + "\" }"
        })
        append("], \"sources\": [")
        append(rule.sources.joinToString(", ") {
            "{ \"file\": \"" + escape(it.file) + "\", \"line\": " + it.line + ", \"column\": " + it.column + " }"
        })
        append("] }")
        if (index != sorted.lastIndex) append(',')
        appendLine()
    }
    appendLine("  ],")
    appendLine("  \"conflicts\": [")
    conflicts.forEachIndexed { index, conflict ->
        append("    { \"property\": \"").append(escape(conflict.property)).append("\"")
        append(", \"winnerLayer\": \"").append(escape(conflict.winnerLayer)).append("\"")
        append(", \"winnerValue\": \"").append(escape(conflict.winnerValue)).append("\"")
        append(", \"loserLayer\": \"").append(escape(conflict.loserLayer)).append("\"")
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
    appendLine("  \"errors\": [],")
    appendLine("  \"notes\": []")
    appendLine("}")
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

private fun renderCss(rules: List<Rule>, layers: List<String>): String = buildString {
    appendLine("@layer " + layers.joinToString(", ") + ";")
    appendLine()
    layers.forEach { layer ->
        val inLayer = rules.filter { it.layer == layer }
        if (inLayer.isEmpty()) return@forEach
        appendLine("@layer " + layer + " {")
        // Base and pseudo-class/pseudo-element rules first.
        inLayer.filter { rule -> rule.conditions.none { it.kind in AT_RULE_KINDS } }
            .sortedBy { it.className }
            .forEach { rule -> appendLine("  " + selectorOf(rule) + " { " + bodyOf(rule) + " }") }
        // At-rule groups, sorted for deterministic merged output.
        inLayer.filter { rule -> rule.conditions.any { it.kind in AT_RULE_KINDS } }
            .groupBy { rule -> rule.conditions.filter { it.kind in AT_RULE_KINDS }.joinToString(" | ") { atRuleOf(it) } }
            .toSortedMap()
            .forEach { (wrappersKey, group) ->
                var indent = "  "
                val wrappers = wrappersKey.split(" | ")
                wrappers.forEach { wrapper ->
                    appendLine(indent + wrapper + " {")
                    indent += "  "
                }
                group.sortedBy { it.className }.forEach { rule ->
                    appendLine(indent + selectorOf(rule) + " { " + bodyOf(rule) + " }")
                }
                wrappers.forEach { _ ->
                    indent = indent.dropLast(2)
                    appendLine(indent + "}")
                }
            }
        appendLine("}")
        appendLine()
    }
}

private val AT_RULE_KINDS = setOf("media", "container", "supports")

private fun selectorOf(rule: Rule): String {
    val suffix = rule.conditions
        .filter { it.kind == "pseudoClass" || it.kind == "pseudoElement" }
        .joinToString("") { it.value }
    return "." + rule.className + suffix
}

private fun bodyOf(rule: Rule): String =
    rule.declarations.joinToString(" ") { it.property + ": " + it.value + ";" }

private fun atRuleOf(condition: Condition): String = when (condition.kind) {
    "media" -> "@media " + condition.value
    "container" -> "@container " + condition.value
    "supports" -> "@supports " + condition.value
    else -> ""
}


// -- tooling commands ---------------------------------------------------------

private fun initProject(options: Options) {
    val directory = options.first()?.let(::File) ?: throw ToolError("missing target directory")
    val template = options.single("template") ?: "static"
    if (template != "static" && template != "ktor-htmx" && template != "ktor-incremental") {
        throw ToolError("unknown template " + template + " (expected static, ktor-htmx or ktor-incremental)")
    }
    if (directory.exists() && directory.listFiles()?.isNotEmpty() == true) {
        throw ToolError("directory is not empty: " + directory.absolutePath)
    }
    directory.mkdirs()
    writeStarter(directory, template)
    println("created " + template + " starter in " + directory.absolutePath)
    println("next: cd " + directory.name + " && gradle run")
}

private const val KAIROS_VERSION = "0.1.0"
private const val KOTLIN_VERSION = "2.2.20"

private fun writeStarter(directory: File, template: String) {
    val resources = listOf(
        "settings.gradle.kts",
        "build.gradle.kts",
        "README.md",
        "src/main/kotlin/Main.kt",
    )
    resources.forEach { relative ->
        val stream = Thread.currentThread().contextClassLoader
            .getResourceAsStream("templates/" + template + "/" + relative)
            ?: throw ToolError("starter template is missing: " + relative)
        val content = stream.bufferedReader().use { it.readText() }
            .replace("__KAIROS__", KAIROS_VERSION)
            .replace("__KOTLIN__", KOTLIN_VERSION)
        val file = File(directory, relative)
        file.parentFile.mkdirs()
        file.writeText(content)
    }
}

private fun checkProject(options: Options) {
    val mapFile = File(options.requireSingle("map"))
    val cssFile = File(options.requireSingle("css"))
    if (!mapFile.exists()) throw ToolError("map not found: " + mapFile)
    if (!cssFile.exists()) throw ToolError("css not found: " + cssFile)
    val model = loadMap(mapFile.absolutePath)
    val css = cssFile.readText()
    val problems = ArrayList<String>()

    val cssClasses = Regex("\\.(k[robv][0-9a-f]{12})").findAll(css).map { it.groupValues[1] }.toSet()
    val mapClasses = model.rules.map { it.className }.toSet()
    model.rules.forEach { rule ->
        if (rule.className !in cssClasses) {
            problems += "manifest rule " + rule.className + " is missing from the stylesheet"
        } else if (!css.contains("." + rule.className + pseudoSuffix(rule) + " {")) {
            problems += "stylesheet selector for " + rule.className + " does not match its conditions"
        }
    }
    cssClasses.filter { it !in mapClasses }.forEach { orphan ->
        problems += "stylesheet class " + orphan + " is not in the manifest"
    }
    if (model.errors.isNotEmpty()) {
        problems += "manifest records " + model.errors.size + " build error(s)"
    }
    val warnings = ArrayList<String>()
    if (model.coverage.fallbackBlocks > 0) {
        val message = "manifest records " + model.coverage.fallbackBlocks +
            " fallback block(s) (runtime concatenation)"
        if (options.single("strict") == "true") problems += message else warnings += message
    }

    if (problems.isEmpty()) {
        println("check passed: " + model.rules.size + " rules, " + model.coverage.conditionalRules + " conditional")
        warnings.forEach { println("  warn " + it) }
        return
    }
    println("check failed: " + problems.size + " problem(s)")
    problems.forEach { println("  - " + it) }
    exitProcess(1)
}

private fun pseudoSuffix(rule: Rule): String = rule.conditions
    .filter { it.kind == "pseudoClass" || it.kind == "pseudoElement" }
    .joinToString("") { it.value }

private fun doctor(options: Options) {
    println("kairos doctor")
    var warnings = 0
    val javaVersion = Runtime.version().feature()
    if (javaVersion >= 21) println("ok   java        " + System.getProperty("java.version"))
    else {
        println("warn java        " + System.getProperty("java.version") + " (21+ required)")
        warnings++
    }
    val node = runCatching { executeCommand("node", "--version") }.getOrNull()
    if (node != null) println("ok   node        " + node)
    else {
        println("warn node        not found (Kotlin/JS targets need Node)")
        warnings++
    }
    val gradle = runCatching { executeCommand("gradle", "--version") }.getOrNull()?.lines()?.firstOrNull { it.contains("Gradle") }
    if (gradle != null) println("ok   gradle      " + gradle.trim())
    else {
        println("warn gradle      not on PATH (use ./gradlew)")
        warnings++
    }
    val mavenLocal = File(System.getProperty("user.home"), ".m2/repository/dev/kairos")
    if (mavenLocal.exists()) println("ok   mavenLocal  " + mavenLocal.absolutePath)
    else {
        println("warn mavenLocal  no dev.kairos artifacts (run ./gradlew -PframeworkOnly=true publishToMavenLocal)")
        warnings++
    }
    val map = options.single("map")?.let(::File)
    if (map != null && map.exists()) {
        val model = loadMap(map.absolutePath)
        println("ok   manifest    " + model.module + " (" + model.rules.size + " rules)")
    }
    println(if (warnings == 0) "all checks passed" else warnings.toString() + " warning(s)")
}

private fun executeCommand(vararg command: String): String {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    return output
}

private fun coverage(options: Options) {
    val model = loadMap(options.requireSingle("map"))
    println("module          " + model.module)
    println("rules           " + model.coverage.rules)
    println("conditional     " + model.coverage.conditionalRules)
    println("fallback blocks " + model.coverage.fallbackBlocks)
    println("errors          " + model.errors.size)
    val byKind = model.rules.flatMap { it.conditions }.groupingBy { it.kind }.eachCount().toSortedMap()
    byKind.forEach { (kind, count) -> println("  " + kind.padEnd(14) + count) }
    if (model.coverage.fallbackBlocks > 0) {
        println()
        println("warning: " + model.coverage.fallbackBlocks + " style call(s) fell back to runtime concatenation")
    }
}
