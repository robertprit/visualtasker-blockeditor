package de.visualtasker.blockeditor.registry

import de.visualtasker.workflow.semantics.WorkflowElementTypes
import de.visualtasker.workflow.semantics.VisualTaskerCommandCatalog
import de.visualtasker.workflow.semantics.WorkspaceValueTypeSystem
import de.visualtasker.workflow.semantics.canBeUsedAsExpression
import de.visualtasker.workflow.semantics.shortDisplayName
import de.visualtasker.workflow.semantics.CommandArgument
import de.visualtasker.workflow.semantics.CommandArgumentType
import de.visualtasker.workflow.semantics.CommandCatalogEntry

object BlockTypes {
    const val EVENT_START = WorkflowElementTypes.EVENT_START
    const val ACTION_CLICK_TEXT = WorkflowElementTypes.ACTION_CLICK_TEXT
    const val ACTION_WAIT = WorkflowElementTypes.ACTION_WAIT
    const val ACTION_FIND_TEMPLATE = WorkflowElementTypes.ACTION_FIND_TEMPLATE
    const val FEEDBACK_BEEP = WorkflowElementTypes.FEEDBACK_BEEP
    const val FEEDBACK_VIBRATE = WorkflowElementTypes.FEEDBACK_VIBRATE
    const val DEBUG_LOG = WorkflowElementTypes.DEBUG_LOG
    const val CONTROL_REPEAT = WorkflowElementTypes.CONTROL_REPEAT
    const val CONTROL_WHILE = WorkflowElementTypes.CONTROL_WHILE
    const val CONTROL_IF = WorkflowElementTypes.CONTROL_IF
    const val CONTROL_IF_ELSE = WorkflowElementTypes.CONTROL_IF_ELSE
    const val CONTROL_IF_ELSEIF_ELSE = WorkflowElementTypes.CONTROL_IF_ELSEIF_ELSE
    const val LOGIC_SCREEN_CONTAINS = WorkflowElementTypes.LOGIC_SCREEN_CONTAINS
    const val LOGIC_BOOLEAN = WorkflowElementTypes.LOGIC_BOOLEAN
    const val LOGIC_AND = WorkflowElementTypes.LOGIC_AND
    const val LOGIC_OR = WorkflowElementTypes.LOGIC_OR
    const val LOGIC_OPERATE = WorkflowElementTypes.LOGIC_OPERATE
    const val LOGIC_COMPARE = WorkflowElementTypes.LOGIC_COMPARE
    const val LITERAL_NUMBER = WorkflowElementTypes.LITERAL_NUMBER
    const val LITERAL_STRING = WorkflowElementTypes.LITERAL_STRING
    const val LITERAL_REGION = WorkflowElementTypes.LITERAL_REGION
    const val LITERAL_BOOLEAN = WorkflowElementTypes.LITERAL_BOOLEAN
    const val VARIABLE_GET = WorkflowElementTypes.VARIABLE_GET
    const val VARIABLE_REPORTER = WorkflowElementTypes.VARIABLE_REPORTER
    const val VARIABLES_GET = WorkflowElementTypes.VARIABLES_GET
    const val VARIABLE_VALUE = WorkflowElementTypes.VARIABLE_VALUE
    const val VARIABLE_SET = WorkflowElementTypes.VARIABLE_SET
    const val VARIABLE_REPORTER_PREFIX = WorkflowElementTypes.VARIABLE_REPORTER_PREFIX
    const val EMSCRIPT_COMMAND_PREFIX = WorkflowElementTypes.EMSCRIPT_COMMAND_PREFIX
    const val REM_FLOW_NODE_PREFIX = WorkflowElementTypes.REM_FLOW_NODE_PREFIX
    const val CUSTOM_PREFIX = WorkflowElementTypes.CUSTOM_PREFIX

    const val SLOT_DO = WorkflowElementTypes.SLOT_DO
    const val SLOT_THEN = WorkflowElementTypes.SLOT_THEN
    const val SLOT_ELIF = WorkflowElementTypes.SLOT_ELIF
    const val SLOT_ELSE = WorkflowElementTypes.SLOT_ELSE
    const val SLOT_BODY = WorkflowElementTypes.SLOT_BODY
}

object DefaultBlockRegistry : BlockRegistry {
    private val baseDefinitions: List<BlockDefinition> = listOf(
        BlockDefinition(
            id = BlockTypes.EVENT_START,
            label = "Script Start",
            category = "event",
            hasPrevious = false,
            hasNext = true,
            fields = listOf(
                FieldDefinition("script", "Script", defaultValue = "Script Start"),
                FieldDefinition(
                    key = "color",
                    label = "Farbe",
                    kind = FieldKind.CHOICE,
                    defaultValue = "orange",
                    options = listOf(
                        FieldOption("blue", "Blau"),
                        FieldOption("green", "Grün"),
                        FieldOption("violet", "Violett"),
                        FieldOption("orange", "Orange"),
                        FieldOption("red", "Rot"),
                        FieldOption("gray", "Grau"),
                    ),
                ),
            ),
        ),
        BlockDefinition(
            id = BlockTypes.ACTION_CLICK_TEXT,
            label = "Click Text",
            category = "action",
            hasPrevious = true,
            hasNext = true,
            fields = listOf(FieldDefinition("text", "Text", defaultValue = "OK")),
        ),
        BlockDefinition(
            id = BlockTypes.ACTION_WAIT,
            label = "Wait",
            category = "action",
            hasPrevious = true,
            hasNext = true,
            fields = listOf(
                FieldDefinition(
                    key = "ms",
                    label = "ms",
                    kind = FieldKind.TIMEOUT_MS,
                    defaultValue = "500",
                    required = true,
                    minValue = 0.0,
                ),
            ),
        ),
        BlockDefinition(
            id = BlockTypes.ACTION_FIND_TEMPLATE,
            label = "Find Template",
            category = BlockCategories.PERCEPTION,
            hasPrevious = true,
            hasNext = true,
            paletteVisible = false,
            deprecated = true,
            fields = listOf(
                FieldDefinition(
                    key = "imagePath",
                    label = "image",
                    kind = FieldKind.IMAGE_TEMPLATE,
                    defaultValue = "",
                    required = true,
                    sourceOptions = listOf(
                        ParameterSourceKind.MANUAL,
                        ParameterSourceKind.FILE,
                        ParameterSourceKind.VARIABLE,
                    ),
                    defaultSource = ParameterSourceKind.FILE,
                ),
                FieldDefinition(
                    key = "threshold",
                    label = "threshold",
                    kind = FieldKind.THRESHOLD,
                    defaultValue = "0.82",
                    required = true,
                    minValue = 0.0,
                    maxValue = 1.0,
                ),
                FieldDefinition(
                    key = "timeoutMs",
                    label = "timeout",
                    kind = FieldKind.TIMEOUT_MS,
                    defaultValue = "3000",
                    required = true,
                    minValue = 0.0,
                ),
                FieldDefinition(
                    key = "retryCount",
                    label = "retry",
                    kind = FieldKind.RETRY_COUNT,
                    defaultValue = "1",
                    required = true,
                    minValue = 0.0,
                ),
                FieldDefinition(
                    key = "searchRegion",
                    label = "region",
                    kind = FieldKind.REGION,
                    defaultValue = "",
                    sourceOptions = listOf(
                        ParameterSourceKind.REGION_MANUAL,
                        ParameterSourceKind.REGION_REPORTER,
                        ParameterSourceKind.VARIABLE,
                    ),
                    defaultSource = ParameterSourceKind.REGION_MANUAL,
                ),
            ),
        ),
        BlockDefinition(
            id = BlockTypes.FEEDBACK_BEEP,
            label = "Beep",
            category = BlockCategories.FEEDBACK,
            hasPrevious = true,
            hasNext = true,
            fields = listOf(
                FieldDefinition(
                    key = "frequency",
                    label = "Hz",
                    kind = FieldKind.NUMBER,
                    defaultValue = "1000",
                    required = true,
                    minValue = 20.0,
                    maxValue = 20_000.0,
                ),
                FieldDefinition(
                    key = "durationMs",
                    label = "ms",
                    kind = FieldKind.TIMEOUT_MS,
                    defaultValue = "200",
                    required = true,
                    minValue = 10.0,
                    maxValue = 10_000.0,
                ),
                FieldDefinition(
                    key = "volume",
                    label = "vol",
                    kind = FieldKind.NUMBER,
                    defaultValue = "100",
                    required = true,
                    minValue = 0.0,
                    maxValue = 100.0,
                ),
            ),
        ),
        BlockDefinition(
            id = BlockTypes.FEEDBACK_VIBRATE,
            label = "Vibrate",
            category = BlockCategories.FEEDBACK,
            hasPrevious = true,
            hasNext = true,
            fields = listOf(
                FieldDefinition(
                    key = "pattern",
                    label = "ms",
                    defaultValue = "80",
                    required = true,
                ),
            ),
            valueInputs = listOf(
                ValueInputDefinition(
                    name = "patternMs",
                    label = "patternMs",
                    accepts = setOf("Number"),
                    required = false,
                ),
            ),
        ),
        BlockDefinition(
            id = BlockTypes.DEBUG_LOG,
            label = "Log",
            category = "debug",
            hasPrevious = true,
            hasNext = true,
            valueInputs = listOf(ValueInputDefinition("value", "value", setOf("Any"))),
        ),
        BlockDefinition(
            id = BlockTypes.CONTROL_REPEAT,
            label = "Repeat",
            category = "control",
            hasPrevious = true,
            hasNext = true,
            fields = listOf(FieldDefinition("times", "times", FieldKind.NUMBER, "3")),
            statementInputs = listOf(StatementInputDefinition(BlockTypes.SLOT_DO, "do")),
        ),
        BlockDefinition(
            id = BlockTypes.CONTROL_WHILE,
            label = "While",
            category = "control",
            hasPrevious = true,
            hasNext = true,
            valueInputs = listOf(ValueInputDefinition("CONDITION", "while", setOf("Boolean"))),
            statementInputs = listOf(StatementInputDefinition(BlockTypes.SLOT_BODY, "body")),
        ),
        BlockDefinition(
            id = BlockTypes.CONTROL_IF,
            label = "If",
            category = "control",
            hasPrevious = true,
            hasNext = true,
            valueInputs = listOf(ValueInputDefinition("CONDITION", "if", setOf("Boolean"))),
            statementInputs = listOf(StatementInputDefinition(BlockTypes.SLOT_THEN, "then")),
        ),
        BlockDefinition(
            id = BlockTypes.CONTROL_IF_ELSE,
            label = "If / Else",
            category = "control",
            hasPrevious = true,
            hasNext = true,
            valueInputs = listOf(ValueInputDefinition("CONDITION", "if", setOf("Boolean"))),
            statementInputs = listOf(
                StatementInputDefinition(BlockTypes.SLOT_THEN, "then"),
                StatementInputDefinition(BlockTypes.SLOT_ELSE, "else"),
            ),
        ),
        BlockDefinition(
            id = BlockTypes.CONTROL_IF_ELSEIF_ELSE,
            label = "If / Else If / Else",
            category = "control",
            hasPrevious = true,
            hasNext = true,
            valueInputs = listOf(
                ValueInputDefinition("CONDITION", "if", setOf("Boolean")),
                ValueInputDefinition("ELIF_CONDITION", "elseif", setOf("Boolean")),
            ),
            statementInputs = listOf(
                StatementInputDefinition(BlockTypes.SLOT_THEN, "then"),
                StatementInputDefinition(BlockTypes.SLOT_ELIF, "elseif"),
                StatementInputDefinition(BlockTypes.SLOT_ELSE, "else"),
            ),
        ),
        BlockDefinition(
            id = BlockTypes.LOGIC_SCREEN_CONTAINS,
            label = "Screen contains",
            category = "logic",
            hasPrevious = false,
            hasNext = false,
            outputType = "Boolean",
            isReporter = true,
            fields = listOf(FieldDefinition("text", "Text", defaultValue = "OK")),
        ),
        BlockDefinition(
            id = BlockTypes.LOGIC_BOOLEAN,
            label = "Boolean",
            category = "logic",
            hasPrevious = false,
            hasNext = false,
            outputType = "Boolean",
            isReporter = true,
            fields = listOf(FieldDefinition("value", "value", FieldKind.BOOLEAN, "true")),
        ),
        BlockDefinition(
            id = BlockTypes.LOGIC_AND,
            label = "And",
            category = "logic",
            hasPrevious = false,
            hasNext = false,
            outputType = "Boolean",
            isReporter = true,
            valueInputs = listOf(
                ValueInputDefinition("A", "A", setOf("Boolean")),
                ValueInputDefinition("B", "B", setOf("Boolean")),
            ),
        ),
        BlockDefinition(
            id = BlockTypes.LOGIC_OR,
            label = "Or",
            category = "logic",
            hasPrevious = false,
            hasNext = false,
            outputType = "Boolean",
            isReporter = true,
            valueInputs = listOf(
                ValueInputDefinition("A", "A", setOf("Boolean")),
                ValueInputDefinition("B", "B", setOf("Boolean")),
            ),
        ),
        BlockDefinition(
            id = BlockTypes.LOGIC_OPERATE,
            label = "Operate",
            category = "logic",
            hasPrevious = false,
            hasNext = false,
            outputType = "Any",
            isReporter = true,
            inputsInline = true,
            fields = listOf(
                FieldDefinition(
                    key = "operator",
                    label = "operator",
                    kind = FieldKind.CHOICE,
                    defaultValue = "add",
                    options = listOf(
                        FieldOption("add", "+"),
                        FieldOption("subtract", "-"),
                        FieldOption("multiply", "*"),
                        FieldOption("divide", "/"),
                        FieldOption("modulo", "%"),
                    ),
                ),
            ),
            valueInputs = listOf(
                ValueInputDefinition("Input1", "A", setOf("Any", "Number", "Boolean")),
                ValueInputDefinition("Input2", "B", setOf("Any", "Number", "Boolean")),
            ),
        ),
        BlockDefinition(
            id = BlockTypes.LOGIC_COMPARE,
            label = "Compare",
            category = "logic",
            hasPrevious = false,
            hasNext = false,
            outputType = "Boolean",
            isReporter = true,
            inputsInline = true,
            fields = listOf(
                FieldDefinition(
                    key = "operator",
                    label = "operator",
                    kind = FieldKind.CHOICE,
                    defaultValue = "greaterOrEqual",
                    options = listOf(
                        FieldOption("equal", "=="),
                        FieldOption("notEqual", "!="),
                        FieldOption("less", "<"),
                        FieldOption("lessOrEqual", "<="),
                        FieldOption("greater", ">"),
                        FieldOption("greaterOrEqual", ">="),
                    ),
                ),
            ),
            valueInputs = listOf(
                ValueInputDefinition("LEFT", "left", setOf("Any", "Number", "Boolean")),
                ValueInputDefinition("RIGHT", "right", setOf("Any", "Number", "Boolean")),
            ),
        ),
        BlockDefinition(
            id = BlockTypes.LITERAL_NUMBER,
            label = "Number",
            category = "logic",
            hasPrevious = false,
            hasNext = false,
            outputType = "Number",
            isReporter = true,
            fields = listOf(FieldDefinition("value", "value", FieldKind.NUMBER, "0")),
        ),
        BlockDefinition(
            id = BlockTypes.LITERAL_STRING,
            label = "String",
            category = "logic",
            hasPrevious = false,
            hasNext = false,
            outputType = "Text",
            isReporter = true,
            fields = listOf(FieldDefinition("value", "value", FieldKind.TEXT, "")),
        ),
        BlockDefinition(
            id = BlockTypes.LITERAL_REGION,
            label = "Region",
            category = BlockCategories.VISION,
            hasPrevious = false,
            hasNext = false,
            outputType = "Region",
            isReporter = true,
            inputsInline = true,
            fields = listOf(
                FieldDefinition("x", "x", FieldKind.NUMBER, "0"),
                FieldDefinition("y", "y", FieldKind.NUMBER, "0"),
                FieldDefinition("width", "width", FieldKind.NUMBER, "1"),
                FieldDefinition("height", "height", FieldKind.NUMBER, "1"),
            ),
        ),
        BlockDefinition(
            id = BlockTypes.LITERAL_BOOLEAN,
            label = "Boolean",
            category = "logic",
            hasPrevious = false,
            hasNext = false,
            outputType = "Boolean",
            isReporter = true,
            fields = listOf(FieldDefinition("value", "value", FieldKind.BOOLEAN, "false")),
        ),
        BlockDefinition(
            id = BlockTypes.VARIABLE_GET,
            label = "Get variable",
            category = "variable",
            hasPrevious = false,
            hasNext = false,
            outputType = "Any",
            isReporter = true,
            fields = listOf(FieldDefinition("variable", "var", defaultValue = "")),
        ),
        BlockDefinition(
            id = BlockTypes.VARIABLE_SET,
            label = "Set variable",
            category = "variable",
            hasPrevious = true,
            hasNext = true,
            fields = listOf(
                FieldDefinition("assignmentKind", "kind", defaultValue = "SET"),
                FieldDefinition("variable", "var", defaultValue = ""),
                FieldDefinition("value", "value", defaultValue = ""),
            ),
            valueInputs = listOf(
                ValueInputDefinition(WorkspaceValueTypeSystem.VARIABLE_SET_VALUE_INPUT, "value", setOf("Any")),
            ),
        ),
    )

    private val generatedCommandDefinitions: List<BlockDefinition> =
        VisualTaskerCommandCatalog.allEntries()
            .mapNotNull { entry ->
                val blockType = entry.block?.blockType ?: return@mapNotNull null
                if (!blockType.startsWith(BlockTypes.EMSCRIPT_COMMAND_PREFIX)) return@mapNotNull null
                entry.toGeneratedBlockDefinition()
            }

    private val definitions: Map<String, BlockDefinition> = (baseDefinitions + generatedCommandDefinitions)
        .map { it.withCommandCatalogMetadata() }
        .associateBy { it.id }

    override fun getDefinition(id: String): BlockDefinition? = definitions[id]

    override fun allDefinitions(): List<BlockDefinition> = definitions.values.toList()
}

fun CommandCatalogEntry.toGeneratedBlockDefinition(): BlockDefinition {
    val valueCommand = canBeUsedAsExpression()
    return BlockDefinition(
        id = requireNotNull(block).blockType,
        label = shortDisplayName(),
        category = category,
        hasPrevious = !valueCommand,
        hasNext = !valueCommand,
        outputType = returnType.takeIf { valueCommand },
        isReporter = valueCommand,
        fields = listOf(
            FieldDefinition("command", "cmd", defaultValue = canonicalName),
            FieldDefinition(
                key = "args",
                label = "args",
                kind = FieldKind.TEXT,
                defaultValue = arguments.joinToString { it.defaultArgumentLiteral() },
            ),
        ),
        valueInputs = arguments
            .filter { argument ->
                argument.type != CommandArgumentType.STATEMENT_BODY &&
                    (valueCommand || argument.acceptedTypes.isNotEmpty())
            }
            .map { argument ->
                ValueInputDefinition(
                    name = argument.name,
                    label = argument.name,
                    accepts = argument.acceptedTypes.ifEmpty { argument.workspaceAcceptedTypes() },
                    required = argument.required,
                )
            },
    )
}

private fun CommandArgument.workspaceAcceptedTypes(): Set<String> = when (type) {
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

private fun CommandArgument.defaultArgumentLiteral(): String {
    val value = defaultValue.orEmpty()
    return when (type) {
        CommandArgumentType.TEXT,
        CommandArgumentType.IMAGE_TEMPLATE,
        -> "\"${value.escapeEmscriptString()}\""
        CommandArgumentType.REGION -> if (value.startsWith("region(")) value else "\"${value.escapeEmscriptString()}\""
        else -> value
    }
}

private fun String.escapeEmscriptString(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
