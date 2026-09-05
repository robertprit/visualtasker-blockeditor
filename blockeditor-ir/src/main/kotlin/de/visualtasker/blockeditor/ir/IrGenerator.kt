package de.visualtasker.blockeditor.ir

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.asString
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry

class IrGenerator(
    private val registry: BlockRegistry = DefaultBlockRegistry,
) {
    fun generate(document: WorkspaceDocument, scriptName: String = document.id): IrScript {
        val startRoots = document.rootBlocks.mapNotNull { id ->
            document.blocks[id]?.takeIf { it.type == BlockTypes.EVENT_START }
        }
        val statements = startRoots.flatMap { start ->
            val next = WorkspaceGraph.nextChain(document, start.id)
            if (next != null) {
                emitChain(document, next)
            } else {
                emptyList()
            }
        }
        return IrScript(name = scriptName, statements = statements)
    }

    private fun emitChain(document: WorkspaceDocument, headId: BlockId): List<IrStatement> {
        val result = mutableListOf<IrStatement>()
        var current: BlockId? = headId
        while (current != null) {
            val block = document.blocks[current] ?: break
            result += emitBlock(document, block)
            current = WorkspaceGraph.nextChain(document, current)
        }
        return result
    }

    private fun emitBlock(document: WorkspaceDocument, block: BlockNode): IrStatement {
        return when (block.type) {
            BlockTypes.ACTION_CLICK_TEXT -> IrStatement.ClickText(block.fieldText("text"))
            BlockTypes.ACTION_WAIT -> IrStatement.Wait(block.fieldNumber("ms").toLong())
            BlockTypes.FEEDBACK_BEEP -> IrStatement.Beep(
                frequency = block.fieldNumber("frequency").toInt(),
                durationMs = block.fieldNumber("durationMs").toInt(),
                volume = block.fieldNumber("volume").toInt(),
            )
            BlockTypes.FEEDBACK_VIBRATE -> IrStatement.Vibrate(block.fieldLongList("pattern"))
            BlockTypes.DEBUG_LOG -> IrStatement.Log(block.fieldText("message"))
            BlockTypes.VARIABLE_SET -> IrStatement.SetVariable(
                block.fieldText("variable"),
                block.fieldText("value"),
            )
            BlockTypes.CONTROL_REPEAT -> IrStatement.Repeat(
                times = block.fieldNumber("times").toInt(),
                body = emitStatementSlot(document, block, BlockTypes.SLOT_DO),
            )
            BlockTypes.CONTROL_WHILE -> IrStatement.While(
                condition = emitValueInput(document, block, "CONDITION"),
                body = emitStatementSlot(document, block, BlockTypes.SLOT_BODY),
            )
            BlockTypes.CONTROL_IF -> IrStatement.If(
                condition = emitValueInput(document, block, "CONDITION"),
                thenBranch = emitStatementSlot(document, block, BlockTypes.SLOT_THEN),
            )
            BlockTypes.CONTROL_IF_ELSE -> IrStatement.If(
                condition = emitValueInput(document, block, "CONDITION"),
                thenBranch = emitStatementSlot(document, block, BlockTypes.SLOT_THEN),
                elseBranch = emitStatementSlot(document, block, BlockTypes.SLOT_ELSE),
            )
            BlockTypes.CONTROL_IF_ELSEIF_ELSE -> IrStatement.If(
                condition = emitValueInput(document, block, "CONDITION"),
                thenBranch = emitStatementSlot(document, block, BlockTypes.SLOT_THEN),
                elseIfBranches = block.elseIfSlots().map { (conditionSlot, statementSlot) ->
                    ElseIfBranch(
                        condition = emitValueInput(document, block, conditionSlot),
                        body = emitStatementSlot(document, block, statementSlot),
                    )
                },
                elseBranch = emitStatementSlot(document, block, BlockTypes.SLOT_ELSE),
            )
            else -> {
                if (block.type.startsWith(BlockTypes.EMSCRIPT_COMMAND_PREFIX)) {
                    return IrStatement.CommandCall(
                        command = block.fieldText("command"),
                        arguments = block.fieldText("args"),
                    )
                }
                val def = registry.getDefinition(block.type)
                if (block.type.startsWith(BlockTypes.CUSTOM_PREFIX)) {
                    val payload = block.fields["payload"]?.asString()
                        ?: block.fields.values.firstOrNull()?.asString()
                        ?: ""
                    IrStatement.Log("${def?.label ?: block.type}: $payload")
                } else {
                    IrStatement.Log("Unsupported block type: ${block.type}")
                }
            }
        }
    }

    private fun emitStatementSlot(
        document: WorkspaceDocument,
        block: BlockNode,
        slotName: String,
    ): List<IrStatement> {
        val head = WorkspaceGraph.statementStackHead(document, block.id, slotName) ?: return emptyList()
        return emitChain(document, head)
    }

    private fun BlockNode.elseIfSlots(): List<Pair<String, String>> =
        valueInputs
            .mapNotNull { input ->
                when (input.name) {
                    "ELIF_CONDITION" -> input.name to BlockTypes.SLOT_ELIF
                    else -> input.name
                        .removePrefix("ELIF_CONDITION_")
                        .takeIf { it != input.name && it.toIntOrNull() != null }
                        ?.let { input.name to "ELIF_$it" }
                }
            }
            .sortedBy { (conditionSlot, _) ->
                if (conditionSlot == "ELIF_CONDITION") {
                    0
                } else {
                    conditionSlot.removePrefix("ELIF_CONDITION_").toIntOrNull() ?: Int.MAX_VALUE
                }
            }

    private fun emitValueInput(
        document: WorkspaceDocument,
        block: BlockNode,
        inputName: String,
    ): IrExpression {
        val input = block.valueInputs.find { it.name == inputName }
        val connected = input?.connection?.connectedTo ?: return IrExpression.LiteralBoolean(false)
        val (valueBlockId, _) = WorkspaceGraph.findConnection(document, connected)
            ?: return IrExpression.LiteralBoolean(false)
        return emitExpression(document, document.blocks[valueBlockId] ?: return IrExpression.LiteralBoolean(false))
    }

    private fun emitExpression(document: WorkspaceDocument, block: BlockNode): IrExpression {
        return when (block.type) {
            BlockTypes.LOGIC_BOOLEAN -> IrExpression.LiteralBoolean(block.fieldBool("value"))
            BlockTypes.LITERAL_BOOLEAN -> IrExpression.LiteralBoolean(block.fieldBool("value"))
            BlockTypes.LITERAL_NUMBER -> IrExpression.LiteralNumber(block.fieldNumber("value"))
            BlockTypes.LITERAL_STRING -> IrExpression.LiteralString(block.fieldText("value"))
            BlockTypes.LOGIC_SCREEN_CONTAINS -> IrExpression.ScreenContains(block.fieldText("text"))
            BlockTypes.LOGIC_AND -> IrExpression.And(
                emitValueInput(document, block, "A"),
                emitValueInput(document, block, "B"),
            )
            BlockTypes.LOGIC_OR -> IrExpression.Or(
                emitValueInput(document, block, "A"),
                emitValueInput(document, block, "B"),
            )
            BlockTypes.LOGIC_COMPARE -> IrExpression.Compare(
                operator = block.fieldText("operator"),
                left = emitValueInput(document, block, "LEFT"),
                right = emitValueInput(document, block, "RIGHT"),
            )
            BlockTypes.LOGIC_OPERATE -> IrExpression.Operate(
                operator = block.fieldText("operator"),
                a = emitValueInput(document, block, "Input1"),
                b = emitValueInput(document, block, "Input2"),
                c = null,
            )
            BlockTypes.VARIABLE_GET -> IrExpression.GetVariable(block.fieldText("variable"))
            else -> when {
                block.type.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX) ->
                    IrExpression.GetVariable(block.fieldText("variable"))
                else -> IrExpression.LiteralText(block.type)
            }
        }
    }

    private fun BlockNode.fieldText(key: String): String =
        fields[key]?.asString() ?: registry.getDefinition(type)?.fields?.find { it.key == key }?.defaultValue ?: ""

    private fun BlockNode.fieldNumber(key: String): Double =
        when (val value = fields[key]) {
            is FieldValue.Number -> value.value
            is FieldValue.Text -> value.value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

    private fun BlockNode.fieldLongList(key: String): List<Long> =
        fieldText(key)
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .ifEmpty {
                listOf(fieldNumber(key).toLong())
            }

    private fun BlockNode.fieldBool(key: String): Boolean =
        when (val value = fields[key]) {
            is FieldValue.Bool -> value.value
            is FieldValue.Text -> value.value.equals("true", ignoreCase = true)
            else -> registry.getDefinition(type)?.fields?.find { it.key == key }
                ?.defaultValue?.equals("true", ignoreCase = true) ?: false
        }
}
