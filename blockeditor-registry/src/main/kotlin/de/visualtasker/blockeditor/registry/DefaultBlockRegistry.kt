package de.visualtasker.blockeditor.registry

object BlockTypes {
    const val EVENT_START = "event.start"
    const val ACTION_CLICK_TEXT = "action.clickText"
    const val ACTION_WAIT = "action.wait"
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
    const val VARIABLE_GET = "variable.get"
    const val VARIABLE_SET = "variable.set"
    const val VARIABLE_REPORTER_PREFIX = "variable.reporter."
    const val CUSTOM_PREFIX = "custom."

    const val SLOT_DO = "DO"
    const val SLOT_THEN = "THEN"
    const val SLOT_ELIF = "ELIF"
    const val SLOT_ELSE = "ELSE"
    const val SLOT_BODY = "BODY"
}

object DefaultBlockRegistry : BlockRegistry {
    private val definitions: Map<String, BlockDefinition> = listOf(
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
            fields = listOf(FieldDefinition("ms", "ms", FieldKind.NUMBER, "500")),
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
    ).associateBy { it.id }

    override fun getDefinition(id: String): BlockDefinition? = definitions[id]

    override fun allDefinitions(): List<BlockDefinition> = definitions.values.toList()
}
