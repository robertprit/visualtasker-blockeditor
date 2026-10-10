package de.visualtasker.workflow.semantics

import de.visualtasker.workflow.core.BlockId
import de.visualtasker.workflow.core.BlockNode
import de.visualtasker.workflow.core.Connection
import de.visualtasker.workflow.core.ConnectionId
import de.visualtasker.workflow.core.ConnectionKind
import de.visualtasker.workflow.core.FieldValue
import de.visualtasker.workflow.core.StatementInput
import de.visualtasker.workflow.core.ValueInput

object WorkflowElementTypes {
    const val EVENT_START = "event.start"
    const val ACTION_CLICK_TEXT = "action.clickText"
    const val ACTION_WAIT = "action.wait"
    const val ACTION_FIND_TEMPLATE = "action.findTemplate"
    const val FEEDBACK_BEEP = "feedback.beep"
    const val FEEDBACK_VIBRATE = "feedback.vibrate"
    const val DEBUG_LOG = "debug.log"
    const val CONTROL_REPEAT = "control.repeat"
    const val CONTROL_WHILE = "control.while"
    const val CONTROL_IF = "control.if"
    const val CONTROL_IF_ELSE = "control.ifElse"
    const val CONTROL_IF_ELSEIF_ELSE = "control.ifElseIfElse"
    const val LOGIC_SCREEN_CONTAINS = "logic.screenContains"
    const val LOGIC_BOOLEAN = "logic.boolean"
    const val LOGIC_AND = "logic.and"
    const val LOGIC_OR = "logic.or"
    const val LOGIC_OPERATE = "logic.operate"
    const val LOGIC_COMPARE = "logic.compare"
    const val LITERAL_NUMBER = "literal.number"
    const val LITERAL_STRING = "literal.string"
    const val LITERAL_REGION = "literal.region"
    const val LITERAL_LIST = "literal.list"
    const val LITERAL_BOOLEAN = "literal.boolean"
    const val VARIABLE_GET = "variable.get"
    const val VARIABLE_REPORTER = "variable.reporter"
    const val VARIABLES_GET = "variables.get"
    const val VARIABLE_VALUE = "variable.value"
    const val VARIABLE_SET = "variable.set"
    const val VARIABLE_REPORTER_PREFIX = "variable.reporter."
    const val EMSCRIPT_COMMAND_PREFIX = "emscript.command."
    const val REM_FLOW_NODE_PREFIX = "emscript.command.rem."
    const val CUSTOM_PREFIX = "custom."

    const val SLOT_DO = "DO"
    const val SLOT_THEN = "THEN"
    const val SLOT_ELIF = "ELIF"
    const val SLOT_ELSE = "ELSE"
    const val SLOT_BODY = "BODY"
}

object CommandCategories {
    const val EVENT = "event"
    const val ACTION = "action"
    const val FEEDBACK = "feedback"
    const val EMSCRIPT = "emscript"
    const val INPUT = "input"
    const val PERCEPTION = "perception"
    const val VISION = "vision"
    const val SCENE = "scene"
    const val TEXT = "text"
    const val FILE = "file"
    const val SYSTEM = "system"
    const val CHROME_TAB = "chromeTab"
    const val TASKER = "tasker"
    const val SHIZUKU = "shizuku"
    const val TERMUX = "termux"
    const val SCRCPY = "scrcpy"
    const val CHARTS = "charts"
    const val CONTROL = "control"
    const val LOGIC = "logic"
    const val VARIABLES = "variables"
    const val FLOW = "flow"
    const val REM_FLOW_NODES = "remFlowNodes"
    const val RUNTIME = "runtime"
    const val DEBUG = "debug"
    const val VARIABLE = "variable"
    const val CUSTOM = "custom"

    val all: Set<String> = setOf(
        EVENT, ACTION, FEEDBACK, EMSCRIPT, INPUT, PERCEPTION, VISION, SCENE,
        TEXT, FILE, SYSTEM, CHROME_TAB, TASKER, SHIZUKU, TERMUX, SCRCPY,
        CHARTS, CONTROL, LOGIC, VARIABLES, FLOW, REM_FLOW_NODES, RUNTIME,
        DEBUG, VARIABLE, CUSTOM,
    )
}

data class SemanticValueInputDefinition(
    val name: String,
    val accepts: Set<String>,
    val required: Boolean = true,
)

data class WorkflowSemanticDefinition(
    val type: String,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val outputType: String? = null,
    val valueInputs: List<SemanticValueInputDefinition> = emptyList(),
    val statementInputs: Set<String> = emptySet(),
    val fieldDefaults: Map<String, String> = emptyMap(),
    val metadataDefaults: Map<String, String> = emptyMap(),
)

fun interface WorkflowSemanticDefinitionProvider {
    fun semanticDefinition(type: String): WorkflowSemanticDefinition?
}

object DefaultWorkflowSemanticDefinitions : WorkflowSemanticDefinitionProvider {
    private val builtIns = listOf(
        definition(WorkflowElementTypes.EVENT_START, defaults = mapOf("script" to "Script Start", "color" to "orange")),
        definition(WorkflowElementTypes.ACTION_CLICK_TEXT, defaults = mapOf("text" to "OK")),
        definition(WorkflowElementTypes.ACTION_WAIT, defaults = mapOf("ms" to "500")),
        definition(
            WorkflowElementTypes.ACTION_FIND_TEMPLATE,
            output = "ImageMatch?",
            defaults = mapOf("imagePath" to "", "threshold" to "0.82", "timeoutMs" to "3000", "retryCount" to "1", "searchRegion" to ""),
        ),
        definition(WorkflowElementTypes.FEEDBACK_BEEP, defaults = mapOf("frequency" to "440", "durationMs" to "120", "volume" to "100")),
        definition(
            WorkflowElementTypes.FEEDBACK_VIBRATE,
            inputs = listOf(SemanticValueInputDefinition("patternMs", setOf("Number"), required = false)),
            defaults = mapOf("pattern" to "80"),
        ),
        definition(WorkflowElementTypes.DEBUG_LOG, inputs = listOf(SemanticValueInputDefinition("value", setOf("Any")))),
        definition(WorkflowElementTypes.CONTROL_REPEAT, statements = setOf(WorkflowElementTypes.SLOT_DO), defaults = mapOf("times" to "3")),
        definition(WorkflowElementTypes.CONTROL_WHILE, inputs = boolInput("CONDITION"), statements = setOf(WorkflowElementTypes.SLOT_BODY)),
        definition(WorkflowElementTypes.CONTROL_IF, inputs = boolInput("CONDITION"), statements = setOf(WorkflowElementTypes.SLOT_THEN)),
        definition(
            WorkflowElementTypes.CONTROL_IF_ELSE,
            inputs = boolInput("CONDITION"),
            statements = setOf(WorkflowElementTypes.SLOT_THEN, WorkflowElementTypes.SLOT_ELSE),
        ),
        definition(
            WorkflowElementTypes.CONTROL_IF_ELSEIF_ELSE,
            inputs = boolInput("CONDITION") + boolInput("ELIF_CONDITION"),
            statements = setOf(WorkflowElementTypes.SLOT_THEN, WorkflowElementTypes.SLOT_ELIF, WorkflowElementTypes.SLOT_ELSE),
        ),
        definition(WorkflowElementTypes.LOGIC_SCREEN_CONTAINS, output = "Boolean", defaults = mapOf("text" to "OK")),
        definition(WorkflowElementTypes.LOGIC_BOOLEAN, output = "Boolean", defaults = mapOf("value" to "true")),
        definition(WorkflowElementTypes.LOGIC_AND, output = "Boolean", inputs = boolInput("A") + boolInput("B")),
        definition(WorkflowElementTypes.LOGIC_OR, output = "Boolean", inputs = boolInput("A") + boolInput("B")),
        definition(
            WorkflowElementTypes.LOGIC_OPERATE,
            output = "Any",
            inputs = listOf(
                SemanticValueInputDefinition("Input1", setOf("Any", "Number", "Boolean")),
                SemanticValueInputDefinition("Input2", setOf("Any", "Number", "Boolean")),
            ),
            defaults = mapOf("operator" to "add"),
        ),
        definition(
            WorkflowElementTypes.LOGIC_COMPARE,
            output = "Boolean",
            inputs = listOf(
                SemanticValueInputDefinition("LEFT", setOf("Any", "Number", "Boolean")),
                SemanticValueInputDefinition("RIGHT", setOf("Any", "Number", "Boolean")),
            ),
            defaults = mapOf("operator" to "greaterOrEqual"),
        ),
        definition(WorkflowElementTypes.LITERAL_NUMBER, output = "Number", defaults = mapOf("value" to "0")),
        definition(WorkflowElementTypes.LITERAL_STRING, output = "Text", defaults = mapOf("value" to "")),
        definition(
            WorkflowElementTypes.LITERAL_REGION,
            output = "Region",
            defaults = mapOf("x" to "0", "y" to "0", "width" to "1", "height" to "1"),
        ),
        definition(WorkflowElementTypes.LITERAL_LIST, output = "List<Any>"),
        definition(WorkflowElementTypes.LITERAL_BOOLEAN, output = "Boolean", defaults = mapOf("value" to "false")),
        definition(WorkflowElementTypes.VARIABLE_GET, output = "Any", defaults = mapOf("variable" to "")),
        definition(
            WorkflowElementTypes.VARIABLE_SET,
            inputs = listOf(SemanticValueInputDefinition(WorkspaceValueTypeSystem.VARIABLE_SET_VALUE_INPUT, setOf("Any"))),
            defaults = mapOf("assignmentKind" to "SET", "variable" to "", "value" to ""),
        ),
    ).associateBy(WorkflowSemanticDefinition::type)

    override fun semanticDefinition(type: String): WorkflowSemanticDefinition? {
        builtIns[type]?.let { return it }
        if (type.startsWith(WorkflowElementTypes.VARIABLE_REPORTER_PREFIX)) {
            return definition(type, output = "Any")
        }
        val entry = VisualTaskerCommandCatalog.findByBlockType(type) ?: return null
        return WorkflowSemanticDefinition(
            type = type,
            hasPrevious = !entry.canBeUsedAsExpression() && entry.kind != CommandCatalogKind.EVENT,
            hasNext = !entry.canBeUsedAsExpression(),
            outputType = entry.returnType?.takeUnless { it.equals("Void", ignoreCase = true) },
            valueInputs = entry.arguments
                .filter { argument ->
                    argument.type != CommandArgumentType.STATEMENT_BODY &&
                        (entry.canBeUsedAsExpression() || argument.acceptedTypes.isNotEmpty())
                }
                .map { argument ->
                    SemanticValueInputDefinition(
                        name = argument.name,
                        accepts = argument.acceptedTypes.ifEmpty { argument.type.workspaceAcceptedTypes() },
                        required = argument.required,
                    )
                },
            // Generated catalog blocks historically expose only value inputs;
            // native control definitions above own statement-slot structure.
            statementInputs = emptySet(),
            fieldDefaults = mapOf(
                "command" to entry.canonicalName,
                "args" to entry.arguments.joinToString { it.defaultValue.orEmpty() },
            ),
            metadataDefaults = mapOf(
                VisualTaskerCommandCatalog.METADATA_COMMAND_ID to entry.id,
                VisualTaskerCommandCatalog.METADATA_CANONICAL_NAME to entry.canonicalName,
                VisualTaskerCommandCatalog.METADATA_COMMAND_KIND to entry.kind.name,
                VisualTaskerCommandCatalog.METADATA_PLUGIN_OWNER to entry.pluginOwner,
                VisualTaskerCommandCatalog.METADATA_RUNTIME_CAPABILITIES to
                    entry.capabilities.joinToString(",") { capability -> capability.name },
                VisualTaskerCommandCatalog.METADATA_RUNTIME_STATUS to
                    (entry.runtime?.dryRunBehavior ?: "unknown"),
            ),
        )
    }

    private fun definition(
        type: String,
        output: String? = null,
        inputs: List<SemanticValueInputDefinition> = emptyList(),
        statements: Set<String> = emptySet(),
        defaults: Map<String, String> = emptyMap(),
    ) = WorkflowSemanticDefinition(
        type = type,
        hasPrevious = type != WorkflowElementTypes.EVENT_START && output == null,
        hasNext = output == null,
        outputType = output,
        valueInputs = inputs,
        statementInputs = statements,
        fieldDefaults = defaults,
    )

    private fun boolInput(name: String) =
        listOf(SemanticValueInputDefinition(name, setOf("Boolean")))

    private fun CommandArgumentType.workspaceAcceptedTypes(): Set<String> = when (this) {
        CommandArgumentType.BOOLEAN -> setOf("Boolean")
        CommandArgumentType.NUMBER,
        CommandArgumentType.DURATION_MS,
        CommandArgumentType.FREQUENCY_HZ,
        CommandArgumentType.PERCENT,
        -> setOf("Number")
        CommandArgumentType.TEXT,
        CommandArgumentType.IMAGE_TEMPLATE,
        CommandArgumentType.VARIABLE_REF,
        -> setOf("String")
        CommandArgumentType.REGION -> setOf("Region")
        CommandArgumentType.ANY -> setOf("Any")
        CommandArgumentType.STATEMENT_BODY -> emptySet()
    }
}

fun WorkflowSemanticDefinition.createNode(
    id: BlockId,
    outputTypeOverride: String? = null,
): BlockNode {
    val resolvedOutputType = outputTypeOverride ?: outputType
    return BlockNode(
        id = id,
        type = type,
        fields = fieldDefaults.mapValues { (key, default) -> default.toFieldValue(type, key) },
        previous = connectionIf(hasPrevious, id, "previous", ConnectionKind.Previous),
        next = connectionIf(hasNext, id, "next", ConnectionKind.Next),
        output = resolvedOutputType?.let { output ->
            Connection(
                id = ConnectionId("${id.value}:output"),
                owner = id,
                kind = ConnectionKind.Output,
                provides = output,
                accepts = setOf(output),
            )
        },
        valueInputs = valueInputs.map { input ->
            ValueInput(
                name = input.name,
                connection = Connection(
                    id = ConnectionId("${id.value}:${input.name}"),
                    owner = id,
                    kind = ConnectionKind.ValueInput,
                    accepts = input.accepts,
                    slotName = input.name,
                ),
            )
        },
        statementInputs = statementInputs.map { name ->
            StatementInput(
                name = name,
                connection = Connection(
                    id = ConnectionId("${id.value}:$name:stmt"),
                    owner = id,
                    kind = ConnectionKind.StatementInput,
                    slotName = name,
                ),
            )
        },
        metadata = metadataDefaults,
    )
}

private fun connectionIf(
    enabled: Boolean,
    owner: BlockId,
    suffix: String,
    kind: ConnectionKind,
): Connection? = if (enabled) {
    Connection(ConnectionId("${owner.value}:$suffix"), owner, kind)
} else {
    null
}

private fun String.toFieldValue(type: String, key: String): FieldValue {
    val isBoolean = key == "value" && type in setOf(
        WorkflowElementTypes.LOGIC_BOOLEAN,
        WorkflowElementTypes.LITERAL_BOOLEAN,
    )
    if (isBoolean) return FieldValue.Bool(equals("true", ignoreCase = true))
    val numericKeys = setOf("ms", "frequency", "durationMs", "volume", "times", "threshold", "timeoutMs", "retryCount", "x", "y", "width", "height")
    val isNumber = key in numericKeys || (key == "value" && type == WorkflowElementTypes.LITERAL_NUMBER)
    return if (isNumber) FieldValue.Number(toDoubleOrNull() ?: 0.0) else FieldValue.Text(this)
}

internal fun BlockNode.hasDynamicElseIfInput(name: String): Boolean =
    type == WorkflowElementTypes.CONTROL_IF_ELSEIF_ELSE && name.startsWith("ELIF_CONDITION_")
