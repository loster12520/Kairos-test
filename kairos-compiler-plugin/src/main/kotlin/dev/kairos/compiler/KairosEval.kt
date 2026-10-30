package dev.kairos.compiler

import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.util.render

/**
 * A value the compiler understands.
 *
 * [Lit] is a plain constant. [Choice] is a value whose every arm is constant but whose selection
 * depends on runtime input - the ternary / enum-switch case. A block containing a choice compiles
 * to one class per combination, and the emitted code selects between class names; no style value
 * is computed at runtime.
 */
sealed interface Eval {
    data class Lit(val text: String) : Eval

    data class Choice(val arms: List<ChoiceArm>) : Eval
}

/**
 * One arm of a choice. [condition] is null for the unconditional fallback; [value] is null when
 * the arm can never be taken (the compiler generated "no branch matched" throw).
 */
data class ChoiceArm(
    val condition: IrExpression?,
    val value: String?,
    val rawResult: IrExpression,
)

/** Parameter bindings while expanding inline helpers. */
class Bindings(private val values: Map<IrValueDeclaration, String?>) {
    fun get(parameter: IrValueDeclaration): String? = values[parameter]
    fun get(parameter: IrValueParameter): String? = values[parameter]

    companion object {
        val EMPTY = Bindings(emptyMap())
    }
}

/** Thrown internally when a construct leaves the decidable subset. */
class NotFoldable(message: String) : RuntimeException(message)

internal fun IrExpression.unwrapCasts(): IrExpression {
    var current = this
    while (current is IrTypeOperatorCall) current = current.argument
    return current
}

internal fun IrExpression.renderShort(): String = try {
    (this as org.jetbrains.kotlin.ir.IrElement).render()
} catch (failure: Throwable) {
    this::class.simpleName ?: "<expr>"
}

internal fun IrConst.asText(): String = when (val raw = value) {
    is String -> raw
    is Char -> raw.toString()
    is Boolean -> raw.toString()
    is Int, is Long, is Short, is Byte -> raw.toString()
    is Float, is Double -> raw.toString()
    is Unit -> "Unit"
    null -> "null"
    else -> raw.toString()
}

internal fun IrBody.singleResult(): IrExpression? = when (this) {
    is IrExpressionBody -> expression
    is IrBlockBody -> {
        val last = statements.lastOrNull() as? IrExpression
        when (val unwrapped = last?.unwrapCasts()) {
            is IrReturn -> unwrapped.value
            else -> unwrapped
        }
    }
    else -> null
}
