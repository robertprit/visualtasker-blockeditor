package de.visualtasker.blockeditor.emscript

import de.visualtasker.blockeditor.ir.IrExpression
import de.visualtasker.blockeditor.ir.IrGenerator
import de.visualtasker.blockeditor.ir.IrScript
import de.visualtasker.blockeditor.ir.IrStatement
import de.visualtasker.blockeditor.domain.WorkspaceDocument

class EmscriptGenerator(
    private val irGenerator: IrGenerator = IrGenerator(),
    private val indent: String = "  ",
) {
    fun generate(document: WorkspaceDocument, scriptName: String = document.id): String =
        generate(irGenerator.generate(document, scriptName))

    fun generate(script: IrScript): String = buildString {
        appendLine("# Script: ${script.name}")
        script.statements.forEach { appendStatement(it, 0) }
    }.trimEnd()

    private fun StringBuilder.appendStatement(statement: IrStatement, depth: Int) {
        when (statement) {
            is IrStatement.ClickText -> appendLine(depth, "CLICK \"${escape(statement.text)}\"")
            is IrStatement.Wait -> appendLine(depth, "WAIT ${statement.milliseconds}")
            is IrStatement.Log -> appendLine(depth, "LOG \"${escape(statement.message)}\"")
            is IrStatement.SetVariable -> appendLine(
                depth,
                "SET \"${escape(statement.name)}\" \"${escape(statement.value)}\"",
            )
            is IrStatement.Repeat -> {
                appendLine(depth, "REPEAT ${statement.times}")
                statement.body.forEach { appendStatement(it, depth + 1) }
                appendLine(depth, "END")
            }
            is IrStatement.While -> {
                appendLine(depth, "WHILE ${emitExpression(statement.condition)}")
                statement.body.forEach { appendStatement(it, depth + 1) }
                appendLine(depth, "END")
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
                appendLine(depth, "END")
            }
        }
    }

    private fun emitExpression(expression: IrExpression): String = when (expression) {
        is IrExpression.ScreenContains -> "SCREEN_CONTAINS \"${escape(expression.text)}\""
        is IrExpression.And -> "(${emitExpression(expression.left)} AND ${emitExpression(expression.right)})"
        is IrExpression.Or -> "(${emitExpression(expression.left)} OR ${emitExpression(expression.right)})"
        is IrExpression.GetVariable -> "VAR \"${escape(expression.name)}\""
        is IrExpression.LiteralBoolean -> if (expression.value) "TRUE" else "FALSE"
        is IrExpression.LiteralText -> "\"${escape(expression.value)}\""
        is IrExpression.Operate -> {
            val op = expression.operator.lowercase()
            val a = emitExpression(expression.a)
            val b = emitExpression(expression.b)
            val c = expression.c?.let { emitExpression(it) }
            if (c != null) {
                "OP $op $a $b $c"
            } else {
                "OP $op $a $b"
            }
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
}
