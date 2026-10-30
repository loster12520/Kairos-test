package dev.kairos.compiler

import dev.kairos.runtime.Declaration
import dev.kairos.runtime.Layer
import dev.kairos.runtime.classNameFor
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrElseBranch
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

private const val CSS_FQN = "dev.kairos.runtime.css"
private const val STYLES_FQN = "dev.kairos.runtime.styles"
private const val DECLARATION_FQN = "dev.kairos.runtime.StyleScope.declaration"
private const val MAX_INLINE_DEPTH = 12
private const val MAX_COMBINATIONS = 8

/**
 * Folds style blocks into class lists.
 *
 * Compaction happens at two levels:
 *
 *  1. **A block compiles to one class** carrying its declarations.
 *  2. **Declarations repeated across blocks are lifted out** into a shared class, so the emitted
 *     stylesheet never contains the same property/value twice.
 *
 * A block may contain @@@if@@@ / @@@when@@@ expressions as long as every arm is a constant. The
 * compiler then emits **one class per combination** and a small conditional that selects between
 * class names - the style values themselves never reach the runtime.
 */
class KairosIrGenerationExtension(
    private val collector: KairosCollector,
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val hasSources = moduleFragment.files.any { it.fileEntry.name.endsWith(".kt") }
        collector.markSources(hasSources)
        if (!hasSources) return
        val resolver = StyleResolver(pluginContext, collector)

        // Pass 1: capture named styles and count declaration usage across every block, including
        // every combination of a block's choices.
        val counter = DeclarationCounter(resolver)
        moduleFragment.transformChildrenVoid(counter)
        val shared = counter.sharedDeclarations()
        collector.reportNote("shared declarations lifted out: " + shared.size)

        // Pass 2: fold with the extraction map.
        moduleFragment.transformChildrenVoid(KairosTransformer(pluginContext, collector, resolver, shared))
    }
}

// -- variant enumeration ------------------------------------------------------

/** One possible declaration set of a block, plus the arm index chosen for each choice. */
private class Variant(val arms: IntArray, val declarations: List<Declaration>)

/**
 * Expands a block into its possible declaration sets. The enumeration order (choices in source
 * order, arms in source order, unreachable arms skipped) is the same order the selection builder
 * walks, which is what lets the two sides agree without a lookup table.
 */
private fun variantsOf(entries: List<Pair<String, Eval?>>): List<Variant> {
    if (entries.any { it.second == null }) throw NotFoldable("a style value is not decidable")
    val statics = LinkedHashMap<String, String>()
    val choices = ArrayList<Pair<String, Eval.Choice>>()
    entries.forEach { (property, value) ->
        when (val evaluated = value) {
            is Eval.Lit -> statics[property] = evaluated.text
            is Eval.Choice -> choices += property to evaluated
            null -> throw NotFoldable("a style value is not decidable")
        }
    }

    var variants = listOf(Variant(IntArray(choices.size), toDeclarations(statics)))
    choices.forEachIndexed { choiceIndex, entry ->
        val options = entry.second.arms.withIndex().filter { it.value.value != null }
        if (options.isEmpty()) throw NotFoldable("choice for " + entry.first + " has no reachable arm")
        if (variants.size * options.size > MAX_COMBINATIONS) {
            throw NotFoldable(
                "too many conditional combinations in one block (" + (variants.size * options.size) +
                    "); split the block or use a layer instead"
            )
        }
        variants = variants.flatMap { variant ->
            options.map { option ->
                val arms = variant.arms.copyOf()
                arms[choiceIndex] = option.index
                val declarations = LinkedHashMap<String, Declaration>()
                variant.declarations.forEach { declarations[it.property] = it }
                declarations[entry.first] = Declaration(entry.first, option.value.value!!)
                Variant(arms, declarations.values.toList())
            }
        }
    }
    return variants
}

private fun toDeclarations(values: Map<String, String>): List<Declaration> =
    values.map { Declaration(it.key, it.value) }

// -- pass 1 ---------------------------------------------------------------

private class DeclarationCounter(private val resolver: StyleResolver) : IrElementTransformerVoid() {

    private val increments = LinkedHashMap<String, Int>()
    private var currentFile: String = "<unknown>"

    override fun visitFile(declaration: IrFile): IrFile {
        currentFile = declaration.fileEntry.name
        resolver.file = currentFile
        return super.visitFile(declaration)
    }

    override fun visitProperty(declaration: IrProperty): IrProperty {
        resolver.capture(StyleSource(declaration, declaration.backingField?.initializer?.expression))
        declaration.transformChildrenVoid(this)
        return declaration
    }

    override fun visitVariable(declaration: IrVariable): IrVariable {
        resolver.capture(StyleSource(declaration, declaration.initializer))
        declaration.transformChildrenVoid(this)
        return declaration
    }

    override fun visitCall(expression: IrCall): IrExpression {
        if (expression.symbol.owner.fqNameWhenAvailable?.asString() == CSS_FQN) {
            runCatching {
                val (layer, lambda) = cssArguments(expression)
                val entries = resolver.interpretBlock(lambda.function.body, Bindings.EMPTY)
                variantsOf(entries).forEach { variant ->
                    variant.declarations.forEach { declaration ->
                        val key = keyOf(layer, declaration)
                        increments[key] = (increments[key] ?: 0) + 1
                    }
                }
            }
        }
        expression.transformChildrenVoid(this)
        return expression
    }

    fun sharedDeclarations(): Set<String> = increments.filterValues { it >= 2 }.keys.toSet()

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun cssArguments(call: IrCall): Pair<Layer, IrFunctionExpression> {
        val lambda = call.arguments.lastOrNull { it is IrFunctionExpression } as? IrFunctionExpression
            ?: throw NotFoldable("css(...) without a lambda argument")
        val callee = call.symbol.owner
        val layerIndex = callee.parameters.indexOfFirst { it.name.asString() == "layer" }
        val layerExpression = if (layerIndex >= 0) call.arguments.getOrNull(layerIndex) else null
        val layer = if (layerExpression == null) Layer.Base else resolver.resolveLayer(layerExpression)
            ?: throw NotFoldable("cannot resolve the layer argument")
        return layer to lambda
    }
}

internal fun keyOf(layer: Layer, declaration: Declaration): String =
    layer.ordinal.toString() + "|" + declaration.property + ":" + declaration.value

// -- pass 2 ---------------------------------------------------------------

private class KairosTransformer(
    private val pluginContext: IrPluginContext,
    private val collector: KairosCollector,
    private val resolver: StyleResolver,
    private val shared: Set<String>,
) : IrElementTransformerVoid() {

    private var currentFile: String = "<unknown>"

    override fun visitFile(declaration: IrFile): IrFile {
        val previous = currentFile
        currentFile = declaration.fileEntry.name
        resolver.file = currentFile
        val transformed = super.visitFile(declaration)
        currentFile = previous
        resolver.file = previous
        return transformed
    }

    override fun visitProperty(declaration: IrProperty): IrProperty {
        resolver.capture(StyleSource(declaration, declaration.backingField?.initializer?.expression))
        declaration.transformChildrenVoid(this)
        return declaration
    }

    override fun visitVariable(declaration: IrVariable): IrVariable {
        resolver.capture(StyleSource(declaration, declaration.initializer))
        declaration.transformChildrenVoid(this)
        return declaration
    }

    override fun visitCall(expression: IrCall): IrExpression {
        when (expression.symbol.owner.fqNameWhenAvailable?.asString()) {
            CSS_FQN -> foldCss(expression)?.let { return it }
            STYLES_FQN -> foldStyles(expression)?.let { return it }
        }
        expression.transformChildrenVoid(this)
        return expression
    }

    private fun foldCss(call: IrCall): IrExpression? = try {
        val (layer, lambda) = cssArguments(call)
        val entries = resolver.interpretBlock(lambda.function.body, Bindings.EMPTY)
        val variants = variantsOf(entries)
        if (variants.size == 1) {
            stringConst(call, classListFor(layer, variants[0].declarations, currentFile, call.startOffset))
        } else {
            val choices = entries.mapNotNull { entry -> (entry.second as? Eval.Choice)?.let { entry.first to it } }
            val classByArms = LinkedHashMap<String, String>()
            variants.forEach { variant ->
                classByArms[variant.arms.joinToString(",")] =
                    classListFor(layer, variant.declarations, currentFile, call.startOffset)
            }
            // Conditions must not read anything declared inside the style lambda: the emitted
            // selection lives at the call site, outside that scope.
            choices.forEach { (_, choice) ->
                choice.arms.forEach { arm -> arm.condition?.let { condition ->
                    if (resolver.referencesLambdaScope(condition, lambda)) {
                        throw NotFoldable("the condition of a style choice reads a value declared inside the style block")
                    }
                } }
            }
            buildSelection(choices, call) { arms ->
                classByArms[arms.joinToString(",")] ?: throw NotFoldable("missing variant for " + arms.joinToString(","))
            }
        }
    } catch (failure: Error) {
        throw failure
    } catch (failure: NotFoldable) {
        collector.reportError(currentFile, call.startOffset, "cannot resolve style block: " + failure.message)
        null
    } catch (failure: Throwable) {
        collector.reportError(currentFile, call.startOffset, "internal error while folding: " + (failure.message ?: failure::class.simpleName))
        null
    }

    private fun foldStyles(call: IrCall): IrExpression? = try {
        val parts = resolver.resolveStyles(varargArguments(call))
        resolver.checkComposition(parts, currentFile, call.startOffset)
        val classNames = parts.map { part -> classListFor(part.layer, part.declarations, currentFile, call.startOffset) }
        stringConst(call, classNames.joinToString(" ").split(" ").filter { it.isNotEmpty() }.distinct().sorted().joinToString(" "))
    } catch (failure: Error) {
        throw failure
    } catch (failure: NotFoldable) {
        // Composition is a plain concatenation in both paths, so an operand the compiler cannot
        // see through is not an error: the call is left in place and the runtime concatenates.
        // Only the ambiguity check is skipped in that case.
        null
    } catch (failure: Throwable) {
        collector.reportError(currentFile, call.startOffset, "internal error while folding styles: " + (failure.message ?: failure::class.simpleName))
        null
    }

    /**
     * One class for the residual declarations plus one shared class per declaration that also
     * appears somewhere else. Sorted so the emitted constant is deterministic.
     */
    private fun classListFor(layer: Layer, declarations: List<Declaration>, file: String, offset: Int): String {
        if (declarations.isEmpty()) return ""
        val sharedHere = declarations.filter { keyOf(layer, it) in shared }
        val residual = declarations.filterNot { keyOf(layer, it) in shared }
        val classes = ArrayList<String>(sharedHere.size + 1)
        sharedHere.forEach { classes += collector.register(layer, listOf(it), file, offset) }
        if (residual.isNotEmpty()) classes += collector.register(layer, residual, file, offset)
        return classes.distinct().sorted().joinToString(" ")
    }

    // -- conditional selection -----------------------------------------------

    private class StyleBuilder(context: IrPluginContext, start: Int, end: Int) : IrBuilder(context, start, end)

    private fun buildSelection(
        choices: List<Pair<String, Eval.Choice>>,
        call: IrCall,
        classOf: (IntArray) -> String,
    ): IrExpression {
        val builder = StyleBuilder(pluginContext, call.startOffset, call.endOffset)
        val assignment = IntArray(choices.size) { 0 }
        return level(builder, choices, 0, assignment, classOf)
    }

    private fun level(
        builder: StyleBuilder,
        choices: List<Pair<String, Eval.Choice>>,
        depth: Int,
        assignment: IntArray,
        classOf: (IntArray) -> String,
    ): IrExpression {
        if (depth == choices.size) return builder.irString(classOf(assignment))
        return chain(builder, choices, depth, 0, assignment, classOf)
    }

    private fun chain(
        builder: StyleBuilder,
        choices: List<Pair<String, Eval.Choice>>,
        depth: Int,
        index: Int,
        assignment: IntArray,
        classOf: (IntArray) -> String,
    ): IrExpression {
        val arms = choices[depth].second.arms
        val arm = arms[index]
        assignment[depth] = index
        val isLast = index == arms.lastIndex
        if (isLast) {
            if (arm.value == null) return arm.rawResult
            return level(builder, choices, depth + 1, assignment, classOf)
        }
        val thenExpr = if (arm.value == null) arm.rawResult else level(builder, choices, depth + 1, assignment, classOf)
        val condition = arm.condition ?: return thenExpr
        val elseExpr = chain(builder, choices, depth, index + 1, assignment, classOf)
        return builder.irIfThenElse(pluginContext.irBuiltIns.stringType, condition, thenExpr, elseExpr)
    }

    // -- helpers --------------------------------------------------------------

    private fun stringConst(call: IrCall, value: String): IrExpression =
        IrConstImpl.string(call.startOffset, call.endOffset, pluginContext.irBuiltIns.stringType, value)

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun cssArguments(call: IrCall): Pair<Layer, IrFunctionExpression> {
        val lambda = call.arguments.lastOrNull { it is IrFunctionExpression } as? IrFunctionExpression
            ?: throw NotFoldable("css(...) without a lambda argument")
        val callee = call.symbol.owner
        val layerIndex = callee.parameters.indexOfFirst { it.name.asString() == "layer" }
        val layerExpression = if (layerIndex >= 0) call.arguments.getOrNull(layerIndex) else null
        val layer = if (layerExpression == null) Layer.Base else resolver.resolveLayer(layerExpression)
            ?: throw NotFoldable("cannot resolve the layer argument; use a literal Layer constant")
        return layer to lambda
    }

    private fun varargArguments(call: IrCall): List<IrExpression> {
        val vararg = call.arguments.filterIsInstance<IrVararg>().firstOrNull()
            ?: throw NotFoldable("styles(...) without a vararg argument")
        return vararg.elements.map { it as? IrExpression ?: throw NotFoldable("spread arguments are not foldable") }
    }
}

internal class StyleSource(val symbol: Any, val initializer: IrExpression?)

internal data class ResolvedStyle(val layer: Layer, val declarations: List<Declaration>)

internal class StyleResolver(
    private val pluginContext: IrPluginContext,
    private val collector: KairosCollector,
) {
    var file: String = "<unknown>"

    private val capturedStyles = LinkedHashMap<Any, List<ResolvedStyle>?>()
    private val inProgress = LinkedHashSet<Any>()
    private var evalDepth = 0

    fun interpretBlock(body: IrBody?, bindings: Bindings): List<Pair<String, Eval?>> {
        val collected = LinkedHashMap<String, Eval?>()
        runBody(body, bindings, 0, collected)
        return collected.entries.map { it.key to it.value }
    }

    private fun runBody(body: IrBody?, bindings: Bindings, depth: Int, out: MutableMap<String, Eval?>) {
        if (depth > MAX_INLINE_DEPTH) throw NotFoldable("inline expansion too deep")
        val block = body as? IrBlockBody
            ?: throw NotFoldable("style body is " + (body?.let { it::class.simpleName } ?: "null"))
        block.statements.forEach { statement ->
            val expression = statement as? IrExpression
                ?: throw NotFoldable("non-expression statement " + statement::class.simpleName)
            runStatement(expression, bindings, depth, out)
        }
    }

    private fun runStatement(raw: IrExpression, bindings: Bindings, depth: Int, out: MutableMap<String, Eval?>) {
        when (val expression = raw.unwrapCasts()) {
            is IrReturn -> runStatement(expression.value, bindings, depth, out)
            is IrBlock -> expression.statements.forEach { inner ->
                val child = inner as? IrExpression ?: throw NotFoldable("non-expression block statement")
                runStatement(child, bindings, depth, out)
            }
            is IrConst -> if (expression.value != Unit) throw NotFoldable("stray constant in a style body")
            is IrCall -> runCall(expression, bindings, depth, out)
            is IrWhen -> {
                val selected = selectConstantBranch(expression)
                    ?: throw NotFoldable("a style block contains a conditional statement")
                runStatement(selected, bindings, depth, out)
            }
            else -> throw NotFoldable("unsupported style statement " + expression::class.simpleName)
        }
    }

    private fun runCall(call: IrCall, bindings: Bindings, depth: Int, out: MutableMap<String, Eval?>) {
        val callee = call.symbol.owner
        if (callee.fqNameWhenAvailable?.asString() == DECLARATION_FQN) {
            val propertyExpression = call.arguments.getOrNull(call.arguments.size - 2)
                ?: throw NotFoldable("declaration() without a property")
            val valueExpression = call.arguments.getOrNull(call.arguments.size - 1)
                ?: throw NotFoldable("declaration() without a value")
            val property = (evaluate(propertyExpression, bindings) as? Eval.Lit)?.text
                ?: throw NotFoldable("property is not a constant: " + propertyExpression.renderShort())
            val value = evaluate(valueExpression, bindings)
                ?: throw NotFoldable("value is not decidable: " + valueExpression.renderShort())
            out[property] = value
            return
        }
        val frameworkProperty = frameworkProperty(callee)
        if (frameworkProperty != null && callee.parameters.count { it.kind == IrParameterKind.Regular } == 1) {
            val valueExpression = call.arguments.getOrNull(call.arguments.size - 1)
                ?: throw NotFoldable("helper without an argument: " + callee.name)
            val value = evaluate(valueExpression, bindings)
                ?: throw NotFoldable("helper argument is not decidable: " + valueExpression.renderShort())
            out[frameworkProperty] = value
            return
        }
        val body = callee.body ?: throw NotFoldable("helper has no IR body: " + callee.fqNameWhenAvailable)
        val inner = bindArguments(callee, call, bindings)
        runBody(body, inner, depth + 1, out)
    }

    private fun selectConstantBranch(whenExpression: IrWhen): IrExpression? {
        var seenUndecidable = false
        var elseResult: IrExpression? = null
        whenExpression.branches.forEach { branch ->
            if (branch is IrElseBranch) {
                elseResult = branch.result
                return@forEach
            }
            val constant = (branch.condition.unwrapCasts() as? IrConst)?.value as? Boolean
            when (constant) {
                true -> return if (seenUndecidable) null else branch.result
                false -> Unit
                null -> seenUndecidable = true
            }
        }
        return if (seenUndecidable) null else elseResult
    }

    /** True when [expression] reads a parameter or local declared inside the style lambda. */
    fun referencesLambdaScope(expression: IrExpression, lambda: IrFunctionExpression): Boolean {
        val locals = HashSet<IrValueDeclaration>()
        locals += lambda.function.parameters
        val localCollector = object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitVariable(declaration: IrVariable) {
                locals += declaration
                declaration.acceptChildrenVoid(this)
            }
        }
        lambda.function.body?.acceptChildrenVoid(localCollector)
        if (locals.isEmpty()) return false
        var found = false
        val reader = object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitGetValue(expression: IrGetValue) {
                if (expression.symbol.owner in locals) found = true
                expression.acceptChildrenVoid(this)
            }
        }
        expression.acceptChildrenVoid(reader)
        return found
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    fun evaluate(raw: IrExpression, bindings: Bindings): Eval? {
        val expression = raw.unwrapCasts()
        return when (expression) {
            is IrConst -> Eval.Lit(expression.asText())
            is IrGetValue -> bindings.get(expression.symbol.owner)?.let { Eval.Lit(it) }
            is IrStringConcatenation -> {
                val texts = expression.arguments.map { part ->
                    val value = evaluate(part, bindings) as? Eval.Lit ?: return null
                    value.text
                }
                Eval.Lit(texts.joinToString(""))
            }
            is IrGetField -> {
                if (!expression.symbol.owner.isFinal) return null
                val initializer = expression.symbol.owner.initializer?.expression ?: return null
                evaluate(initializer, Bindings.EMPTY)
            }
            is IrWhen -> {
                val arms = expression.branches.map { branch ->
                    val value = evaluate(branch.result, bindings) as? Eval.Lit
                    val condition = if (branch is IrElseBranch) null else branch.condition
                    ChoiceArm(condition, value?.text, branch.result)
                }
                if (arms.all { it.value == null }) return null
                Eval.Choice(arms)
            }
            is IrCall -> evaluateCall(expression, bindings)
            else -> null
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun evaluateCall(call: IrCall, bindings: Bindings): Eval? {
        if (evalDepth > MAX_INLINE_DEPTH) throw NotFoldable("inline expansion too deep")
        evalDepth++
        try {
            val callee = call.symbol.owner
            val name = callee.name.asString()
            val parameters = callee.parameters
            val propertyName = callee.correspondingPropertySymbol?.owner?.name?.asString()
            if (propertyName != null && propertyName in unitPostfixes && parameters.size == 1) {
                val receiver = call.arguments.getOrNull(0)?.let { evaluate(it, bindings) } as? Eval.Lit ?: return null
                return Eval.Lit(receiver.text + propertyName)
            }
            if (name == "toString" && parameters.size == 1) {
                return call.arguments.getOrNull(0)?.let { evaluate(it, bindings) }
            }
            if (name == "plus" && parameters.size == 2) {
                val receiver = call.arguments.getOrNull(0) ?: return null
                if (!receiver.type.isString()) return null
                val left = evaluate(receiver, bindings) as? Eval.Lit ?: return null
                val right = call.arguments.getOrNull(1)?.let { evaluate(it, bindings) } as? Eval.Lit ?: return null
                return Eval.Lit(left.text + right.text)
            }
            val body = callee.body ?: return null
            val result = body.singleResult() ?: return null
            return evaluate(result, bindArguments(callee, call, bindings))
        } finally {
            evalDepth--
        }
    }

    private fun bindArguments(callee: IrFunction, call: IrCall, outer: Bindings): Bindings {
        val values = HashMap<IrValueDeclaration, String?>()
        val offset = call.arguments.size - callee.parameters.size
        callee.parameters.forEachIndexed { index, parameter ->
            val argument = call.arguments.getOrNull(offset + index)
            values[parameter] = (argument?.let { evaluate(it, outer) } as? Eval.Lit)?.text
        }
        return Bindings(values)
    }

    private val unitPostfixes = setOf("px", "pct", "em", "rem", "deg", "ms", "vh", "vw")

    private fun frameworkProperty(callee: IrFunction): String? {
        val fqName = callee.fqNameWhenAvailable?.asString() ?: return null
        if (!fqName.startsWith("dev.kairos.runtime.")) return null
        val name = callee.name.asString()
        if (name.isEmpty() || name[0].isUpperCase()) return null
        if (name in setOf("css", "styles", "declaration", "classNameFor")) return null
        return name.mapIndexed { index, ch -> if (ch.isUpperCase()) "-" + ch.lowercaseChar() else ch }.joinToString("")
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    fun resolveLayer(expression: IrExpression): Layer? {
        val unwrapped = expression.unwrapCasts()
        val name = when (unwrapped) {
            is IrGetEnumValue -> unwrapped.symbol.owner.name.asString()
            is IrCall -> unwrapped.symbol.owner.name.asString()
            else -> return null
        }
        return Layer.entries.firstOrNull { it.name == name }
    }

    fun capture(source: StyleSource) {
        val initializer = source.initializer ?: return
        if (capturedStyles.containsKey(source.symbol)) return
        if (!isStyleCall(initializer)) return
        capturedStyles[source.symbol] = try {
            resolveStyles(listOf(initializer))
        } catch (failure: Throwable) {
            collector.reportNote("capture failed: " + (failure.message ?: failure::class.simpleName))
            null
        }
    }

    private fun isStyleCall(expression: IrExpression): Boolean {
        var current = expression.unwrapCasts()
        if (current is IrBlock) current = current.statements.lastOrNull() as? IrExpression ?: return false
        return current is IrCall && current.symbol.owner.fqNameWhenAvailable?.asString() in setOf(CSS_FQN, STYLES_FQN)
    }

    fun resolveStyles(expressions: List<IrExpression>): List<ResolvedStyle> {
        val parts = ArrayList<ResolvedStyle>()
        expressions.forEach { parts += resolveStyles(expression = it) }
        return parts
    }

    fun resolveStyles(expression: IrExpression): List<ResolvedStyle> {
        val unwrapped = expression.unwrapCasts()
        if (unwrapped is IrBlock) {
            val last = unwrapped.statements.lastOrNull() as? IrExpression ?: throw NotFoldable("empty block")
            return resolveStyles(last)
        }
        if (unwrapped is IrCall) {
            when (unwrapped.symbol.owner.fqNameWhenAvailable?.asString()) {
                CSS_FQN -> return listOf(resolveCssCall(unwrapped))
                STYLES_FQN -> {
                    val vararg = unwrapped.arguments.filterIsInstance<IrVararg>().firstOrNull()
                        ?: throw NotFoldable("styles(...) without arguments")
                    return resolveStyles(vararg.elements.map { it as? IrExpression ?: throw NotFoldable("spread argument") })
                }
            }
            val property = unwrapped.symbol.owner.correspondingPropertySymbol?.owner
            if (property != null) return resolveNamed(property)
            throw NotFoldable("not a style value: " + unwrapped.renderShort())
        }
        if (unwrapped is IrGetField) {
            val property = unwrapped.symbol.owner.correspondingPropertySymbol?.owner
                ?: throw NotFoldable("not a style value: " + unwrapped.renderShort())
            return resolveNamed(property)
        }
        if (unwrapped is IrGetValue) return resolveNamed(unwrapped.symbol.owner)
        throw NotFoldable("not a style value: " + unwrapped.renderShort())
    }

    private fun resolveNamed(declaration: Any): List<ResolvedStyle> {
        capturedStyles[declaration]?.let { return it }
        val initializer = when (declaration) {
            is IrProperty -> declaration.backingField?.initializer?.expression ?: declaration.getter?.body?.singleResult()
            is IrVariable -> declaration.initializer
            else -> null
        } ?: throw NotFoldable("style value has no initializer")
        if (!inProgress.add(declaration)) throw NotFoldable("recursive style value")
        return try {
            val resolved = resolveStyles(initializer)
            capturedStyles[declaration] = resolved
            resolved
        } finally {
            inProgress.remove(declaration)
        }
    }

    /** Composition needs a single declaration set per operand: a choice is not a style value. */
    private fun resolveCssCall(call: IrCall): ResolvedStyle {
        val lambda = call.arguments.lastOrNull { it is IrFunctionExpression } as? IrFunctionExpression
            ?: throw NotFoldable("css(...) without a lambda")
        val callee = call.symbol.owner
        val layerIndex = callee.parameters.indexOfFirst { it.name.asString() == "layer" }
        val layerExpression = if (layerIndex >= 0) call.arguments.getOrNull(layerIndex) else null
        val layer = if (layerExpression == null) Layer.Base else resolveLayer(layerExpression)
            ?: throw NotFoldable("cannot resolve layer")
        val entries = interpretBlock(lambda.function.body, Bindings.EMPTY)
        val declarations = entries.map { entry ->
            val literal = entry.second as? Eval.Lit
                ?: throw NotFoldable("styles(...) cannot compose a block with a conditional value")
            Declaration(entry.first, literal.text)
        }
        return ResolvedStyle(layer, declarations)
    }

    fun checkComposition(parts: List<ResolvedStyle>, file: String, offset: Int) {
        val byProperty = LinkedHashMap<String, MutableList<Pair<Layer, Declaration>>>()
        parts.forEach { part ->
            part.declarations.forEach { declaration ->
                byProperty.getOrPut(declaration.property) { ArrayList() } += part.layer to declaration
            }
        }
        byProperty.forEach { (property, entries) ->
            for (i in 0 until entries.size) {
                for (j in i + 1 until entries.size) {
                    val (leftLayer, left) = entries[i]
                    val (rightLayer, right) = entries[j]
                    if (left == right) continue
                    if (leftLayer == rightLayer) {
                        collector.reportError(
                            file, offset,
                            "ambiguous composition: " + property + " set twice in " + leftLayer.cssName +
                                " (" + left.value + " vs " + right.value + "); put one of them in a different layer",
                        )
                    } else {
                        collector.recordConflict(property, entries[i], entries[j], file, offset)
                    }
                }
            }
        }
    }
}
