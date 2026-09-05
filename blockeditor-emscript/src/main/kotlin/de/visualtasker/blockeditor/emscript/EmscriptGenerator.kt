package de.visualtasker.blockeditor.emscript

import de.visualtasker.blockeditor.domain.NormalizedOperator
import de.visualtasker.blockeditor.domain.OperatorNormalization
import de.visualtasker.blockeditor.ir.IrExpression
import de.visualtasker.blockeditor.ir.IrGenerator
import de.visualtasker.blockeditor.ir.IrScript
import de.visualtasker.blockeditor.ir.IrStatement
import de.visualtasker.blockeditor.domain.WorkspaceDocument

class EmscriptGenerator(
    private val irGenerator: IrGenerator = IrGenerator(),
    private val indent: String = "    ",
) : WorkspaceCodeGenerator {
    override fun generate(document: WorkspaceDocument): String =
        generate(document, document.id)

    fun generate(document: WorkspaceDocument, scriptName: String): String =
        generate(irGenerator.generate(document, scriptName))

    fun generate(script: IrScript): String = buildString {
        script.statements.forEach { appendStatement(it, 0) }
    }.trimEnd()

    private fun StringBuilder.appendStatement(statement: IrStatement, depth: Int) {
        when (statement) {
            is IrStatement.CommandCall -> {
                val command = sanitizeCommandName(statement.command)
                appendLine(depth, "$command(${statement.arguments});")
            }
            is IrStatement.ClickText -> appendLine(depth, "click(\"${escape(statement.text)}\");")
            is IrStatement.Wait -> appendLine(depth, "wait(${statement.milliseconds});")
            is IrStatement.Beep -> appendLine(depth, "${statement.toEmscript()};")
            is IrStatement.Vibrate -> appendLine(depth, "${statement.toEmscript()};")
            is IrStatement.Log -> appendLine(depth, "log(\"${escape(statement.message)}\");")
            is IrStatement.SetVariable -> {
                val name = sanitizeIdentifier(statement.name, "variable")
                require(isSafeScalarExpression(statement.value)) {
                    "Unsupported EMScript set expression: ${statement.value}"
                }
                appendLine(depth, "set $name = ${statement.value};")
            }
            is IrStatement.Repeat -> {
                require(statement.times >= 0) { "repeat count must not be negative" }
                appendLine(depth, "repeat (${statement.times}) {")
                statement.body.forEach { appendStatement(it, depth + 1) }
                appendLine(depth, "}")
            }
            is IrStatement.While -> {
                appendLine(depth, "while (${emitExpression(statement.condition)}) {")
                statement.body.forEach { appendStatement(it, depth + 1) }
                appendLine(depth, "}")
            }
            is IrStatement.If -> {
                appendLine(depth, "if (${emitExpression(statement.condition)}) {")
                statement.thenBranch.forEach { appendStatement(it, depth + 1) }
                statement.elseIfBranches.forEach { branch ->
                    appendLine(depth, "} else if (${emitExpression(branch.condition)}) {")
                    branch.body.forEach { appendStatement(it, depth + 1) }
                }
                if (statement.elseBranch.isNotEmpty()) {
                    appendLine(depth, "} else {")
                    statement.elseBranch.forEach { appendStatement(it, depth + 1) }
                }
                appendLine(depth, "}")
            }
        }
    }

    private fun emitExpression(expression: IrExpression): String = when (expression) {
        is IrExpression.ScreenContains -> "screenContains(\"${escape(expression.text)}\")"
        is IrExpression.And -> "(${emitExpression(expression.left)} && ${emitExpression(expression.right)})"
        is IrExpression.Or -> "(${emitExpression(expression.left)} || ${emitExpression(expression.right)})"
        is IrExpression.GetVariable -> sanitizeIdentifier(expression.name, "variable reference")
        is IrExpression.LiteralBoolean -> if (expression.value) "true" else "false"
        is IrExpression.LiteralNumber -> expression.value.toStableNumber()
        is IrExpression.LiteralString -> "\"${escape(expression.value)}\""
        is IrExpression.LiteralText -> "\"${escape(expression.value)}\""
        is IrExpression.Compare -> {
            val operator = when (val normalized = OperatorNormalization.normalize(expression.operator)) {
                is NormalizedOperator.Compare -> normalized.value.symbol
                else -> unsupportedExpression("compare:${expression.operator}")
            }
            "(${emitExpression(expression.left)} $operator ${emitExpression(expression.right)})"
        }
        is IrExpression.Operate -> {
            val operator = when (val normalized = OperatorNormalization.normalize(expression.operator)) {
                is NormalizedOperator.Arithmetic -> normalized.value.symbol
                else -> unsupportedExpression("operate:${expression.operator}")
            }
            "(${emitExpression(expression.a)} $operator ${emitExpression(expression.b)})"
        }
    }

    private fun StringBuilder.appendLine(depth: Int, line: String) {
        append(indent.repeat(depth))
        append(line)
        append('\n')
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun IrStatement.Beep.toEmscript(): String {
        val frequency = frequency.coerceIn(20, 20_000)
        val duration = durationMs.coerceIn(10, 10_000)
        val normalizedVolume = volume.coerceIn(0, 100)
        return when {
            frequency == 1000 && duration == 200 && normalizedVolume == 100 -> "beep()"
            normalizedVolume == 100 -> "beep($frequency, $duration)"
            else -> "beep($frequency, $duration, $normalizedVolume)"
        }
    }

    private fun IrStatement.Vibrate.toEmscript(): String {
        val normalizedPattern = pattern
            .map { it.coerceIn(0, 10_000) }
            .ifEmpty { listOf(80L) }
        return "vibrate(${normalizedPattern.joinToString()})"
    }

    private fun sanitizeIdentifier(value: String, role: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Invalid $role: $value" }
        val sanitized = buildString {
            trimmed.forEachIndexed { index, char ->
                val safe = when {
                    char == '_' || char.isLetterOrDigit() -> char
                    else -> '_'
                }
                if (index == 0 && safe.isDigit()) append('_')
                append(safe)
            }
        }
        require(sanitized.any { it == '_' || it.isLetter() }) { "Invalid $role: $value" }
        return sanitized
    }

    private fun sanitizeCommandName(value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Invalid command name: $value" }
        require(trimmed.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*"))) {
            "Invalid command name: $value"
        }
        return trimmed
    }

    private fun isSafeScalarExpression(value: String): Boolean =
        value.matches(Regex("-?\\d+(?:\\.\\d+)?")) ||
            value.equals("true", ignoreCase = true) ||
            value.equals("false", ignoreCase = true) ||
            value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) ||
            value.matches(Regex("[A-Za-z0-9_\\s().+\\-*/%<>=!]+"))

    private fun Double.toStableNumber(): String =
        if (isFinite() && this % 1.0 == 0.0) {
            toLong().toString()
        } else {
            toString()
        }

    private fun unsupportedExpression(kind: String): Nothing =
        error("Unsupported EMScript expression: $kind")
}
