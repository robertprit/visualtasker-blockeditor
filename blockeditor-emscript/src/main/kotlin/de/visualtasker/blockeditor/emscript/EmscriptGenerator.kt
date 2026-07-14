package de.visualtasker.blockeditor.emscript

import de.visualtasker.blockeditor.ir.IrExpression
import de.visualtasker.blockeditor.ir.IrGenerator
import de.visualtasker.blockeditor.ir.IrScript
import de.visualtasker.blockeditor.ir.IrStatement
import de.visualtasker.blockeditor.domain.WorkspaceDocument

class EmscriptGenerator(
    private val irGenerator: IrGenerator = IrGenerator(),
    private val indent: String = "  ",
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
            is IrStatement.ClickText -> appendLine(depth, "CLICK \"${escape(statement.text)}\"")
            is IrStatement.Wait -> appendLine(depth, "WAIT ${statement.milliseconds}")
            is IrStatement.Log -> appendLine(depth, "OUTPUT \"${escape(statement.message)}\"")
            is IrStatement.SetVariable -> {
                val name = sanitizeIdentifier(statement.name, "variable")
                require(isSafeScalarExpression(statement.value)) {
                    "Unsupported demo SET expression: ${statement.value}"
                }
                appendLine(depth, "SET $name = ${statement.value}")
            }
            is IrStatement.Repeat -> {
                require(statement.times >= 0) { "LOOP count must not be negative" }
                appendLine(depth, "LOOP ${statement.times}")
                statement.body.forEach { appendStatement(it, depth + 1) }
                appendLine(depth, "END LOOP")
            }
            is IrStatement.While -> {
                appendLine(depth, "WHILE ${emitExpression(statement.condition)}")
                statement.body.forEach { appendStatement(it, depth + 1) }
                appendLine(depth, "END WHILE")
            }
            is IrStatement.If -> {
                appendLine(depth, "IF ${emitExpression(statement.condition)}")
                statement.thenBranch.forEach { appendStatement(it, depth + 1) }
                statement.elseIfBranches.forEach { branch ->
                    appendLine(depth, "ELSEIF ${emitExpression(branch.condition)}")
                    branch.body.forEach { appendStatement(it, depth + 1) }
                }
                if (statement.elseBranch.isNotEmpty()) {
                    appendLine(depth, "ELSE")
                    statement.elseBranch.forEach { appendStatement(it, depth + 1) }
                }
                appendLine(depth, "END IF")
            }
        }
    }

    private fun emitExpression(expression: IrExpression): String = when (expression) {
        is IrExpression.ScreenContains -> unsupportedExpression("screenContains")
        is IrExpression.And -> "(${emitExpression(expression.left)} AND ${emitExpression(expression.right)})"
        is IrExpression.Or -> "(${emitExpression(expression.left)} OR ${emitExpression(expression.right)})"
        is IrExpression.GetVariable -> sanitizeIdentifier(expression.name, "variable reference")
        is IrExpression.LiteralBoolean -> if (expression.value) "TRUE" else "FALSE"
        is IrExpression.LiteralText -> unsupportedExpression("text condition")
        is IrExpression.Operate -> unsupportedExpression("operate:${expression.operator}")
    }

    private fun StringBuilder.appendLine(depth: Int, line: String) {
        append(indent.repeat(depth))
        append(line)
        append('\n')
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

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

    private fun isSafeScalarExpression(value: String): Boolean =
        value.matches(Regex("-?\\d+(?:\\.\\d+)?")) ||
            value.equals("true", ignoreCase = true) ||
            value.equals("false", ignoreCase = true) ||
            value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))

    private fun unsupportedExpression(kind: String): Nothing =
        error("Unsupported demo EMScript expression: $kind")
}
