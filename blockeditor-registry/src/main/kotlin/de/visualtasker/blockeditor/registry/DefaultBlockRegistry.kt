package de.visualtasker.blockeditor.registry

object BlockTypes {
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
    const val LITERAL_BOOLEAN = "literal.boolean"
    const val VARIABLE_GET = "variable.get"
    const val VARIABLE_REPORTER = "variable.reporter"
    const val VARIABLES_GET = "variables.get"
    const val VARIABLE_VALUE = "variable.value"
    const val VARIABLE_SET = "variable.set"
    const val VARIABLE_REPORTER_PREFIX = "variable.reporter."
    const val EMSCRIPT_COMMAND_PREFIX = "emscript.command."
    const val CUSTOM_PREFIX = "custom."

    const val SLOT_DO = "DO"
    const val SLOT_THEN = "THEN"
    const val SLOT_ELIF = "ELIF"
    const val SLOT_ELSE = "ELSE"
    const val SLOT_BODY = "BODY"
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
            category = "action",
            hasPrevious = true,
            hasNext = true,
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
        ),
        BlockDefinition(
            id = BlockTypes.DEBUG_LOG,
            label = "Log",
            category = "debug",
            hasPrevious = true,
            hasNext = true,
            fields = listOf(FieldDefinition("message", "msg", defaultValue = "debug")),
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
                    defaultValue = "add",
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
                    defaultValue = "GREATER_OR_EQUAL",
                    options = listOf(
                        FieldOption("EQUAL", "=="),
                        FieldOption("NOT_EQUAL", "!="),
                        FieldOption("LESS", "<"),
                        FieldOption("LESS_OR_EQUAL", "<="),
                        FieldOption("GREATER", ">"),
                        FieldOption("GREATER_OR_EQUAL", ">="),
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
                FieldDefinition("variable", "var", defaultValue = ""),
                FieldDefinition("value", "value", defaultValue = ""),
            ),
        ),
    )

    private val generatedCommandDefinitions: List<BlockDefinition> =
        VisualTaskerCommandCatalog.allEntries()
            .mapNotNull { entry ->
                val blockType = entry.block?.blockType ?: return@mapNotNull null
                if (!blockType.startsWith(BlockTypes.EMSCRIPT_COMMAND_PREFIX)) return@mapNotNull null
                BlockDefinition(
                    id = blockType,
                    label = entry.shortDisplayName(),
                    category = entry.category,
                    hasPrevious = true,
                    hasNext = true,
                    fields = listOf(
                        FieldDefinition("command", "cmd", defaultValue = entry.canonicalName),
                        FieldDefinition(
                            key = "args",
                            label = "args",
                            kind = FieldKind.TEXT,
                            defaultValue = entry.arguments.joinToString { it.defaultValue ?: "" },
                        ),
                    ),
                )
            }

    private val definitions: Map<String, BlockDefinition> = (baseDefinitions + generatedCommandDefinitions)
        .map { it.withCommandCatalogMetadata() }
        .associateBy { it.id }

    override fun getDefinition(id: String): BlockDefinition? = definitions[id]

    override fun allDefinitions(): List<BlockDefinition> = definitions.values.toList()
}
