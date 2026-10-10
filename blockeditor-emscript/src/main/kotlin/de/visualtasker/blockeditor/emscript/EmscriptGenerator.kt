package de.visualtasker.blockeditor.emscript

import de.visualtasker.workflow.core.NormalizedOperator
import de.visualtasker.workflow.core.OperatorNormalization
import de.visualtasker.workflow.core.canonicalSymbol
import de.visualtasker.workflow.semantics.ir.IrExpression
import de.visualtasker.workflow.semantics.ir.IrGenerator
import de.visualtasker.workflow.semantics.ir.IrScript
import de.visualtasker.workflow.semantics.ir.IrStatement
import de.visualtasker.workflow.core.WorkspaceDocument
import de.visualtasker.workflow.core.SemanticRelation

class EmscriptGenerator(
    private val irGenerator: IrGenerator = IrGenerator(),
    private val indent: String = "    ",
) : WorkspaceCodeGenerator {
    override fun generate(document: WorkspaceDocument): String =
        generate(document, document.id)

    fun generate(document: WorkspaceDocument, scriptName: String): String {
        val script = irGenerator.generate(document, scriptName)
        val projection = WorkspaceSourceAnchorProjector.project(document)
        return projection?.let { generate(script, it) } ?: generate(script)
    }

    fun generate(script: IrScript): String = buildString {
        script.statements.forEach { appendStatement(it, 0, null) }
    }.trimEnd()

    private fun generate(script: IrScript, projection: WorkspaceSourceAnchorProjection): String = buildString {
        projection.relations.forEach { appendRelationAnchor(it.relation) }
        if (projection.relations.isNotEmpty() && script.statements.isNotEmpty()) append('\n')
        val anchors = projection.statements.iterator()
        script.statements.forEach { appendStatement(it, 0, anchors) }
        check(!anchors.hasNext()) { "Source-anchor projection contains more statements than EMScript IR." }
    }.trimEnd()

    private fun StringBuilder.appendStatement(
        statement: IrStatement,
        depth: Int,
        anchors: Iterator<ProjectedStatementSourceAnchors>?,
    ) {
        anchors?.let {
            check(it.hasNext()) { "EMScript IR contains more statements than the source-anchor projection." }
            it.next().entities.forEach { anchor ->
                appendLine(
                    depth,
                    "@source.entity(\"${escape(anchor.locator)}\", \"${escape(anchor.entityId)}\", \"${anchor.entityKind.name}\")",
                )
            }
        }
        when (statement) {
            is IrStatement.CommandCall -> {
                val command = sanitizeCommandName(statement.command)
                val arguments = statement.expressionArguments
                    .takeIf { it.isNotEmpty() }
                    ?.mapIndexed { index, expression ->
                        statement.expressionArgumentNames.getOrNull(index)
                            ?.let { name -> "$name=${emitExpression(expression)}" }
                            ?: emitExpression(expression)
                    }
                    ?.joinToString(", ")
                    ?: statement.arguments
                appendLine(depth, "$command($arguments);")
            }
            is IrStatement.ClickText -> appendLine(depth, "click(\"${escape(statement.text)}\");")
            is IrStatement.Wait -> appendLine(depth, "wait(${statement.milliseconds});")
            is IrStatement.Beep -> appendLine(depth, "${statement.toEmscript()};")
            is IrStatement.Vibrate -> appendLine(depth, "${statement.toEmscript()};")
            is IrStatement.Log -> appendLine(depth, "log(${emitExpression(statement.value)});")
            is IrStatement.SetVariable -> {
                val name = sanitizeIdentifier(statement.name, "variable")
                val value = statement.expression?.let(::emitExpression) ?: statement.value
                require(statement.expression != null || isSafeScalarExpression(value)) {
                    "Unsupported EMScript set expression: $value"
                }
                val keyword = if (statement.assignmentKind.equals("LET", ignoreCase = true)) "let" else "set"
                val declaredType = statement.declaredType
                    ?.takeIf { keyword == "let" }
                    ?.let { ":$it" }
                    .orEmpty()
                appendLine(depth, "$keyword $name$declaredType = $value;")
            }
            is IrStatement.Repeat -> {
                require(statement.times >= 0) { "repeat count must not be negative" }
                appendLine(depth, "repeat (${statement.times}) {")
                statement.body.forEach { appendStatement(it, depth + 1, anchors) }
                appendLine(depth, "}")
            }
            is IrStatement.While -> {
                appendLine(depth, "while (${emitExpression(statement.condition)}) {")
                statement.body.forEach { appendStatement(it, depth + 1, anchors) }
                appendLine(depth, "}")
            }
            is IrStatement.If -> {
                appendLine(depth, "if (${emitExpression(statement.condition)}) {")
                statement.thenBranch.forEach { appendStatement(it, depth + 1, anchors) }
                statement.elseIfBranches.forEach { branch ->
                    appendLine(depth, "} else if (${emitExpression(branch.condition)}) {")
                    branch.body.forEach { appendStatement(it, depth + 1, anchors) }
                }
                if (statement.elseBranch.isNotEmpty()) {
                    appendLine(depth, "} else {")
                    statement.elseBranch.forEach { appendStatement(it, depth + 1, anchors) }
                }
                appendLine(depth, "}")
            }
        }
    }

    private fun StringBuilder.appendRelationAnchor(relation: SemanticRelation) {
        append("@source.relation(")
        append(
            listOf(
                relation.id.value,
                relation.kind.name,
                relation.source.id.value,
                relation.target.id.value,
                relation.role.kind.name,
                relation.role.name.orEmpty(),
                relation.role.branchRole?.name.orEmpty(),
                relation.order?.toString().orEmpty(),
            ).joinToString(", ") { "\"${escape(it)}\"" },
        )
        append(")\n")
    }

    private fun emitExpression(expression: IrExpression): String = when (expression) {
        is IrExpression.CommandCall -> {
            val arguments = expression.arguments.mapIndexed { index, argument ->
                expression.argumentNames.getOrNull(index)
                    ?.let { name -> "$name=${emitExpression(argument)}" }
                    ?: emitExpression(argument)
            }
            "${sanitizeCommandName(expression.command)}(${arguments.joinToString(", ")})"
        }
        is IrExpression.ScreenContains -> "screenContains(\"${escape(expression.text)}\")"
        is IrExpression.And -> "(${emitExpression(expression.left)} && ${emitExpression(expression.right)})"
        is IrExpression.Or -> "(${emitExpression(expression.left)} || ${emitExpression(expression.right)})"
        is IrExpression.GetVariable -> sanitizeIdentifier(expression.name, "variable reference")
        is IrExpression.LiteralBoolean -> if (expression.value) "true" else "false"
        is IrExpression.LiteralNumber -> expression.value.toStableNumber()
        is IrExpression.LiteralString -> "\"${escape(expression.value)}\""
        is IrExpression.LiteralText -> "\"${escape(expression.value)}\""
        is IrExpression.ListLiteral -> "[${expression.values.joinToString(", ") { emitExpression(it) }}]"
        is IrExpression.Compare -> {
            val operator = when (val normalized = OperatorNormalization.normalize(expression.operator)) {
                is NormalizedOperator.Compare -> normalized.canonicalSymbol
                else -> unsupportedExpression("compare:${expression.operator}")
            }
            "(${emitExpression(expression.left)} $operator ${emitExpression(expression.right)})"
        }
        is IrExpression.Operate -> {
            val operator = when (val normalized = OperatorNormalization.normalize(expression.operator)) {
                is NormalizedOperator.Arithmetic -> normalized.canonicalSymbol
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
