package de.visualtasker.blockeditor.registry

import kotlinx.serialization.Serializable

@Serializable
enum class CommandCatalogKind {
    EVENT,
    STATEMENT,
    REPORTER,
    CONTROL,
    OPERATOR,
    VARIABLE,
}

@Serializable
enum class CommandCapability {
    CORE,
    TIMING,
    FEEDBACK,
    A11Y,
    SCREEN_CAPTURE,
    VISION,
    TASKER,
    SHIZUKU,
    TERMUX,
    CUSTOM_TAB,
    SCRCPY,
    CHARTS,
    DEBUG,
}

@Serializable
enum class CommandSideEffect {
    NONE,
    TIMING,
    UI_INPUT,
    FEEDBACK,
    LOGGING,
    VARIABLE_WRITE,
    CONTROL_FLOW,
    SCREEN_READ,
}

@Serializable
enum class CommandArgumentType {
    ANY,
    BOOLEAN,
    NUMBER,
    TEXT,
    DURATION_MS,
    FREQUENCY_HZ,
    PERCENT,
    VARIABLE_REF,
    IMAGE_TEMPLATE,
    REGION,
    STATEMENT_BODY,
}

@Serializable
data class CommandArgument(
    val name: String,
    val type: CommandArgumentType,
    val required: Boolean = true,
    val defaultValue: String? = null,
    val acceptedTypes: Set<String> = emptySet(),
)

@Serializable
data class CommandBlockBinding(
    val blockType: String,
    val paletteVisible: Boolean = true,
)

@Serializable
data class CommandFlowchartBinding(
    val nodeKind: String,
)

@Serializable
data class CommandRuntimeBinding(
    val dryRunBehavior: String,
    val liveCapabilityGate: CommandCapability,
)

@Serializable
data class CommandCatalogEntry(
    val id: String,
    val canonicalName: String,
    val acceptedAliases: List<String> = emptyList(),
    val kind: CommandCatalogKind,
    val category: String,
    val arguments: List<CommandArgument> = emptyList(),
    val returnType: String? = null,
    val sideEffect: CommandSideEffect,
    val capabilities: Set<CommandCapability>,
    val pluginOwner: String = "visualtasker.core",
    val block: CommandBlockBinding? = null,
    val flowchart: CommandFlowchartBinding? = null,
    val runtime: CommandRuntimeBinding? = null,
)

interface CommandCatalog {
    fun allEntries(): List<CommandCatalogEntry>
    fun findById(id: String): CommandCatalogEntry?
    fun findByCanonicalName(name: String): CommandCatalogEntry?
    fun findByAcceptedName(name: String): CommandCatalogEntry?
    fun findByBlockType(blockType: String): CommandCatalogEntry?
}

object VisualTaskerCommandCatalog : CommandCatalog {
    const val METADATA_COMMAND_ID = "emscript.command.id"
    const val METADATA_CANONICAL_NAME = "emscript.command.canonicalName"
    const val METADATA_COMMAND_KIND = "emscript.command.kind"
    const val METADATA_PLUGIN_OWNER = "emscript.command.pluginOwner"
    const val METADATA_RUNTIME_CAPABILITIES = "emscript.command.capabilities"

    private val entries: List<CommandCatalogEntry> = listOf(
        event(
            id = "event.start",
            canonicalName = "onStart",
            aliases = listOf("EVENT.ON_START", "em_on_start"),
            blockType = BlockTypes.EVENT_START,
        ),
        statement(
            id = "action.wait",
            canonicalName = "wait",
            aliases = listOf("WAIT"),
            category = BlockCategories.ACTION,
            blockType = BlockTypes.ACTION_WAIT,
            sideEffect = CommandSideEffect.TIMING,
            capability = CommandCapability.TIMING,
            args = listOf(CommandArgument("ms", CommandArgumentType.DURATION_MS, defaultValue = "500")),
        ),
        statement(
            id = "action.clickText",
            canonicalName = "click",
            aliases = listOf("CLICK", "CLICK_TEXT", "em_click_text"),
            category = BlockCategories.ACTION,
            blockType = BlockTypes.ACTION_CLICK_TEXT,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.A11Y,
            args = listOf(CommandArgument("text", CommandArgumentType.TEXT, defaultValue = "OK")),
        ),
        statement(
            id = "action.findTemplate",
            canonicalName = "findTemplate",
            aliases = listOf("FIND_TEMPLATE"),
            category = BlockCategories.PERCEPTION,
            blockType = BlockTypes.ACTION_FIND_TEMPLATE,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.VISION,
            args = listOf(
                CommandArgument("imagePath", CommandArgumentType.IMAGE_TEMPLATE, defaultValue = ""),
                CommandArgument("threshold", CommandArgumentType.PERCENT, defaultValue = "0.82"),
                CommandArgument("timeoutMs", CommandArgumentType.DURATION_MS, defaultValue = "3000"),
                CommandArgument("retryCount", CommandArgumentType.NUMBER, defaultValue = "1"),
                CommandArgument("searchRegion", CommandArgumentType.REGION, required = false, defaultValue = ""),
            ),
        ),
        statement(
            id = "feedback.beep",
            canonicalName = "beep",
            aliases = listOf("BEEP"),
            category = BlockCategories.FEEDBACK,
            blockType = BlockTypes.FEEDBACK_BEEP,
            sideEffect = CommandSideEffect.FEEDBACK,
            capability = CommandCapability.FEEDBACK,
            args = listOf(
                CommandArgument("frequency", CommandArgumentType.FREQUENCY_HZ, defaultValue = "1000"),
                CommandArgument("durationMs", CommandArgumentType.DURATION_MS, defaultValue = "200"),
                CommandArgument("volume", CommandArgumentType.PERCENT, defaultValue = "100"),
            ),
        ),
        statement(
            id = "feedback.vibrate",
            canonicalName = "vibrate",
            aliases = listOf("VIBRATE"),
            category = BlockCategories.FEEDBACK,
            blockType = BlockTypes.FEEDBACK_VIBRATE,
            sideEffect = CommandSideEffect.FEEDBACK,
            capability = CommandCapability.FEEDBACK,
            args = listOf(CommandArgument("pattern", CommandArgumentType.DURATION_MS, defaultValue = "80")),
        ),
        statement(
            id = "debug.log",
            canonicalName = "log",
            aliases = listOf("LOG"),
            category = BlockCategories.DEBUG,
            blockType = BlockTypes.DEBUG_LOG,
            sideEffect = CommandSideEffect.LOGGING,
            capability = CommandCapability.DEBUG,
            args = listOf(CommandArgument("message", CommandArgumentType.TEXT, defaultValue = "debug")),
        ),
        variable(
            id = "variable.set",
            canonicalName = "set",
            aliases = listOf("SET"),
            blockType = BlockTypes.VARIABLE_SET,
            sideEffect = CommandSideEffect.VARIABLE_WRITE,
            args = listOf(
                CommandArgument("variable", CommandArgumentType.VARIABLE_REF),
                CommandArgument("value", CommandArgumentType.ANY),
            ),
        ),
        variable(
            id = "variable.get",
            canonicalName = "get",
            aliases = listOf("GET", "LET"),
            blockType = BlockTypes.VARIABLE_GET,
            sideEffect = CommandSideEffect.NONE,
            returnType = "Any",
            args = listOf(CommandArgument("variable", CommandArgumentType.VARIABLE_REF)),
        ),
        control(
            id = "control.repeat",
            canonicalName = "repeat",
            aliases = listOf("LOOP", "REPEAT"),
            blockType = BlockTypes.CONTROL_REPEAT,
            args = listOf(
                CommandArgument("times", CommandArgumentType.NUMBER, defaultValue = "3"),
                CommandArgument(BlockTypes.SLOT_DO, CommandArgumentType.STATEMENT_BODY),
            ),
        ),
        control(
            id = "control.while",
            canonicalName = "while",
            aliases = listOf("WHILE"),
            blockType = BlockTypes.CONTROL_WHILE,
            args = listOf(
                CommandArgument("condition", CommandArgumentType.BOOLEAN, acceptedTypes = setOf("Boolean")),
                CommandArgument(BlockTypes.SLOT_BODY, CommandArgumentType.STATEMENT_BODY),
            ),
        ),
        control(
            id = "control.if",
            canonicalName = "if",
            aliases = listOf("IF"),
            blockType = BlockTypes.CONTROL_IF,
            args = listOf(
                CommandArgument("condition", CommandArgumentType.BOOLEAN, acceptedTypes = setOf("Boolean")),
                CommandArgument(BlockTypes.SLOT_THEN, CommandArgumentType.STATEMENT_BODY),
            ),
        ),
        control(
            id = "control.ifElse",
            canonicalName = "if",
            aliases = listOf("IF_ELSE"),
            blockType = BlockTypes.CONTROL_IF_ELSE,
            args = listOf(
                CommandArgument("condition", CommandArgumentType.BOOLEAN, acceptedTypes = setOf("Boolean")),
                CommandArgument(BlockTypes.SLOT_THEN, CommandArgumentType.STATEMENT_BODY),
                CommandArgument(BlockTypes.SLOT_ELSE, CommandArgumentType.STATEMENT_BODY),
            ),
        ),
        control(
            id = "control.ifElseIfElse",
            canonicalName = "if",
            aliases = listOf("ELSEIF", "ELSE IF"),
            blockType = BlockTypes.CONTROL_IF_ELSEIF_ELSE,
            args = listOf(
                CommandArgument("condition", CommandArgumentType.BOOLEAN, acceptedTypes = setOf("Boolean")),
                CommandArgument(BlockTypes.SLOT_THEN, CommandArgumentType.STATEMENT_BODY),
                CommandArgument("elseIfCondition", CommandArgumentType.BOOLEAN, acceptedTypes = setOf("Boolean")),
                CommandArgument(BlockTypes.SLOT_ELIF, CommandArgumentType.STATEMENT_BODY),
                CommandArgument(BlockTypes.SLOT_ELSE, CommandArgumentType.STATEMENT_BODY),
            ),
        ),
        reporter(
            id = "logic.screenContains",
            canonicalName = "screenContains",
            aliases = listOf("SCREEN_CONTAINS"),
            category = BlockCategories.LOGIC,
            blockType = BlockTypes.LOGIC_SCREEN_CONTAINS,
            returnType = "Boolean",
            capability = CommandCapability.A11Y,
            sideEffect = CommandSideEffect.SCREEN_READ,
            args = listOf(CommandArgument("text", CommandArgumentType.TEXT, defaultValue = "OK")),
        ),
        reporter(
            id = "logic.boolean",
            canonicalName = "boolean",
            category = BlockCategories.LOGIC,
            blockType = BlockTypes.LOGIC_BOOLEAN,
            returnType = "Boolean",
            args = listOf(CommandArgument("value", CommandArgumentType.BOOLEAN, defaultValue = "true")),
        ),
        operator(
            id = "logic.and",
            canonicalName = "and",
            aliases = listOf("&&"),
            blockType = BlockTypes.LOGIC_AND,
            returnType = "Boolean",
            args = booleanPairArgs("A", "B"),
        ),
        operator(
            id = "logic.or",
            canonicalName = "or",
            aliases = listOf("||"),
            blockType = BlockTypes.LOGIC_OR,
            returnType = "Boolean",
            args = booleanPairArgs("A", "B"),
        ),
        operator(
            id = "logic.operate",
            canonicalName = "operate",
            aliases = listOf("+", "-", "*", "/", "%"),
            blockType = BlockTypes.LOGIC_OPERATE,
            returnType = "Any",
            args = anyPairArgs("Input1", "Input2") + CommandArgument("operator", CommandArgumentType.TEXT, defaultValue = "add"),
        ),
        operator(
            id = "logic.compare",
            canonicalName = "compare",
            aliases = listOf("==", "!=", "<", "<=", ">", ">="),
            blockType = BlockTypes.LOGIC_COMPARE,
            returnType = "Boolean",
            args = anyPairArgs("LEFT", "RIGHT") + CommandArgument("operator", CommandArgumentType.TEXT, defaultValue = "GREATER_OR_EQUAL"),
        ),
        reporter(
            id = "literal.number",
            canonicalName = "number",
            category = BlockCategories.LOGIC,
            blockType = BlockTypes.LITERAL_NUMBER,
            returnType = "Number",
            args = listOf(CommandArgument("value", CommandArgumentType.NUMBER, defaultValue = "0")),
        ),
        reporter(
            id = "literal.string",
            canonicalName = "string",
            category = BlockCategories.LOGIC,
            blockType = BlockTypes.LITERAL_STRING,
            returnType = "Text",
            args = listOf(CommandArgument("value", CommandArgumentType.TEXT, defaultValue = "")),
        ),
        reporter(
            id = "literal.boolean",
            canonicalName = "boolean",
            category = BlockCategories.LOGIC,
            blockType = BlockTypes.LITERAL_BOOLEAN,
            returnType = "Boolean",
            args = listOf(CommandArgument("value", CommandArgumentType.BOOLEAN, defaultValue = "false")),
        ),
    )

    private val byId = entries.associateBy(CommandCatalogEntry::id)
    private val byCanonicalName = entries.groupBy { it.canonicalName.lowercase() }
    private val byAcceptedName = entries
        .flatMap { entry -> (entry.acceptedAliases + entry.canonicalName).map { it.lowercase() to entry } }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
    private val byBlockType = entries.mapNotNull { entry ->
        entry.block?.blockType?.let { blockType -> blockType to entry }
    }.toMap()

    override fun allEntries(): List<CommandCatalogEntry> = entries.toList()

    override fun findById(id: String): CommandCatalogEntry? = byId[id]

    override fun findByCanonicalName(name: String): CommandCatalogEntry? =
        byCanonicalName[name.lowercase()]?.firstOrNull()

    override fun findByAcceptedName(name: String): CommandCatalogEntry? =
        byAcceptedName[name.lowercase()]?.firstOrNull()

    override fun findByBlockType(blockType: String): CommandCatalogEntry? = byBlockType[blockType]

    fun blockTypes(): Set<String> = byBlockType.keys

    fun metadataForBlockType(blockType: String): Map<String, String> {
        val entry = findByBlockType(blockType) ?: return emptyMap()
        return mapOf(
            METADATA_COMMAND_ID to entry.id,
            METADATA_CANONICAL_NAME to entry.canonicalName,
            METADATA_COMMAND_KIND to entry.kind.name,
            METADATA_PLUGIN_OWNER to entry.pluginOwner,
            METADATA_RUNTIME_CAPABILITIES to entry.capabilities.joinToString(",") { it.name },
        )
    }
}

fun BlockDefinition.withCommandCatalogMetadata(
    catalog: CommandCatalog = VisualTaskerCommandCatalog,
): BlockDefinition {
    val entry = catalog.findByBlockType(id) ?: return this
    return copy(
        metadata = metadata + mapOf(
            VisualTaskerCommandCatalog.METADATA_COMMAND_ID to entry.id,
            VisualTaskerCommandCatalog.METADATA_CANONICAL_NAME to entry.canonicalName,
            VisualTaskerCommandCatalog.METADATA_COMMAND_KIND to entry.kind.name,
            VisualTaskerCommandCatalog.METADATA_PLUGIN_OWNER to entry.pluginOwner,
            VisualTaskerCommandCatalog.METADATA_RUNTIME_CAPABILITIES to entry.capabilities.joinToString(",") { it.name },
        ),
    )
}

private fun event(
    id: String,
    canonicalName: String,
    aliases: List<String>,
    blockType: String,
): CommandCatalogEntry = CommandCatalogEntry(
    id = id,
    canonicalName = canonicalName,
    acceptedAliases = aliases,
    kind = CommandCatalogKind.EVENT,
    category = BlockCategories.EVENT,
    sideEffect = CommandSideEffect.CONTROL_FLOW,
    capabilities = setOf(CommandCapability.CORE),
    block = CommandBlockBinding(blockType),
    flowchart = CommandFlowchartBinding("event"),
    runtime = CommandRuntimeBinding("entrypoint", CommandCapability.CORE),
)

private fun statement(
    id: String,
    canonicalName: String,
    aliases: List<String>,
    category: String,
    blockType: String,
    sideEffect: CommandSideEffect,
    capability: CommandCapability,
    args: List<CommandArgument> = emptyList(),
): CommandCatalogEntry = CommandCatalogEntry(
    id = id,
    canonicalName = canonicalName,
    acceptedAliases = aliases,
    kind = CommandCatalogKind.STATEMENT,
    category = category,
    arguments = args,
    sideEffect = sideEffect,
    capabilities = setOf(CommandCapability.CORE, capability),
    block = CommandBlockBinding(blockType),
    flowchart = CommandFlowchartBinding(category),
    runtime = CommandRuntimeBinding("simulate", capability),
)

private fun variable(
    id: String,
    canonicalName: String,
    aliases: List<String>,
    blockType: String,
    sideEffect: CommandSideEffect,
    args: List<CommandArgument>,
    returnType: String? = null,
): CommandCatalogEntry = CommandCatalogEntry(
    id = id,
    canonicalName = canonicalName,
    acceptedAliases = aliases,
    kind = CommandCatalogKind.VARIABLE,
    category = BlockCategories.VARIABLE,
    arguments = args,
    returnType = returnType,
    sideEffect = sideEffect,
    capabilities = setOf(CommandCapability.CORE),
    block = CommandBlockBinding(blockType),
    flowchart = CommandFlowchartBinding("variable"),
    runtime = CommandRuntimeBinding("evaluate", CommandCapability.CORE),
)

private fun control(
    id: String,
    canonicalName: String,
    aliases: List<String>,
    blockType: String,
    args: List<CommandArgument>,
): CommandCatalogEntry = CommandCatalogEntry(
    id = id,
    canonicalName = canonicalName,
    acceptedAliases = aliases,
    kind = CommandCatalogKind.CONTROL,
    category = BlockCategories.CONTROL,
    arguments = args,
    sideEffect = CommandSideEffect.CONTROL_FLOW,
    capabilities = setOf(CommandCapability.CORE),
    block = CommandBlockBinding(blockType),
    flowchart = CommandFlowchartBinding("control"),
    runtime = CommandRuntimeBinding("branch", CommandCapability.CORE),
)

private fun reporter(
    id: String,
    canonicalName: String,
    aliases: List<String> = emptyList(),
    category: String,
    blockType: String,
    returnType: String,
    args: List<CommandArgument> = emptyList(),
    capability: CommandCapability = CommandCapability.CORE,
    sideEffect: CommandSideEffect = CommandSideEffect.NONE,
): CommandCatalogEntry = CommandCatalogEntry(
    id = id,
    canonicalName = canonicalName,
    acceptedAliases = aliases,
    kind = CommandCatalogKind.REPORTER,
    category = category,
    arguments = args,
    returnType = returnType,
    sideEffect = sideEffect,
    capabilities = setOf(CommandCapability.CORE, capability),
    block = CommandBlockBinding(blockType),
    flowchart = CommandFlowchartBinding("value"),
    runtime = CommandRuntimeBinding("evaluate", capability),
)

private fun operator(
    id: String,
    canonicalName: String,
    aliases: List<String>,
    blockType: String,
    returnType: String,
    args: List<CommandArgument>,
): CommandCatalogEntry = CommandCatalogEntry(
    id = id,
    canonicalName = canonicalName,
    acceptedAliases = aliases,
    kind = CommandCatalogKind.OPERATOR,
    category = BlockCategories.LOGIC,
    arguments = args,
    returnType = returnType,
    sideEffect = CommandSideEffect.NONE,
    capabilities = setOf(CommandCapability.CORE),
    block = CommandBlockBinding(blockType),
    flowchart = CommandFlowchartBinding("operator"),
    runtime = CommandRuntimeBinding("evaluate", CommandCapability.CORE),
)

private fun booleanPairArgs(first: String, second: String): List<CommandArgument> =
    listOf(
        CommandArgument(first, CommandArgumentType.BOOLEAN, acceptedTypes = setOf("Boolean")),
        CommandArgument(second, CommandArgumentType.BOOLEAN, acceptedTypes = setOf("Boolean")),
    )

private fun anyPairArgs(first: String, second: String): List<CommandArgument> =
    listOf(
        CommandArgument(first, CommandArgumentType.ANY, acceptedTypes = setOf("Any", "Number", "Boolean", "Text")),
        CommandArgument(second, CommandArgumentType.ANY, acceptedTypes = setOf("Any", "Number", "Boolean", "Text")),
    )
