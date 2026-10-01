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
enum class CommandCatalogRole {
    LANGUAGE_COMMAND,
    CANONICAL_EXPRESSION_PROJECTION,
    LEGACY_EXPRESSION_ALIAS,
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
    val variadic: Boolean = false,
)

fun CommandCatalogEntry.minimumArgumentCount(): Int =
    arguments
        .filter { it.type != CommandArgumentType.STATEMENT_BODY }
        .count { it.required }

fun CommandCatalogEntry.maximumArgumentCount(): Int? =
    arguments
        .filter { it.type != CommandArgumentType.STATEMENT_BODY }
        .takeUnless { specs -> specs.lastOrNull()?.variadic == true }
        ?.size

fun CommandCatalogEntry.argumentAt(index: Int): CommandArgument? {
    val specs = arguments.filter { it.type != CommandArgumentType.STATEMENT_BODY }
    return specs.getOrNull(index) ?: specs.lastOrNull()?.takeIf { it.variadic }
}

fun CommandCatalogEntry.workspaceInputNameAt(index: Int): String? {
    val specs = arguments.filter { it.type != CommandArgumentType.STATEMENT_BODY }
    val argument = argumentAt(index) ?: return null
    if (!argument.variadic) return argument.name
    val variadicIndex = specs.indexOfLast { it.variadic }
    val occurrence = index - variadicIndex
    return if (occurrence == 0) argument.name else "${argument.name}_${occurrence + 1}"
}

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
    val liveImplemented: Boolean = true,
    val diagnosticCode: String = "CAPABILITY_ADAPTER_REQUIRED",
)

@Serializable
data class CommandCatalogEntry(
    val id: String,
    val canonicalName: String,
    val acceptedAliases: List<String> = emptyList(),
    val kind: CommandCatalogKind,
    val role: CommandCatalogRole = CommandCatalogRole.LANGUAGE_COMMAND,
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

@Serializable
data class CommandCapabilityDescriptor(
    val id: String,
    val canonicalName: String,
    val acceptedNames: Set<String>,
    val kind: CommandCatalogKind,
    val category: String,
    val arguments: List<CommandArgument>,
    val returnType: String?,
    val sideEffect: CommandSideEffect,
    val declaredCapabilities: Set<CommandCapability>,
    val requiredAdapter: CommandCapability?,
    val pluginOwner: String,
    val blockType: String?,
    val flowNodeKind: String?,
    val dryRunBehavior: String,
    val liveImplemented: Boolean,
    val diagnosticCode: String,
)

enum class CommandCatalogDiagnosticCode {
    BLANK_ID,
    DUPLICATE_ID,
    BLANK_CANONICAL_NAME,
    DUPLICATE_BLOCK_BINDING,
    DUPLICATE_ARGUMENT,
    VARIADIC_ARGUMENT_NOT_LAST,
    UNKNOWN_CATEGORY,
    RUNTIME_CAPABILITY_NOT_DECLARED,
    EXECUTABLE_COMMAND_WITHOUT_RUNTIME,
    VALUE_COMMAND_WITHOUT_RETURN_TYPE,
}

data class CommandCatalogDiagnostic(
    val code: CommandCatalogDiagnosticCode,
    val entryId: String,
    val message: String,
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
    const val METADATA_DISPLAY_NAME = "emscript.command.displayName"
    const val METADATA_SHORT_NAME = "emscript.command.shortName"
    const val METADATA_RUNTIME_STATUS = "emscript.command.runtimeStatus"

    private val plannedAdapterCommands: List<CommandCatalogEntry> = listOf(
        catalogCommand(
            id = "chromeTab.isSupported",
            canonicalName = "chromeTab.isSupported",
            aliases = listOf("ChromeTab.isSupported"),
            category = BlockCategories.CHROME_TAB,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.CUSTOM_TAB,
            pluginOwner = "visualtasker.customtabs",
            returnType = "Bool",
            kind = CommandCatalogKind.REPORTER,
        ),
        catalogCommand("chromeTab.bind", "ChromeTab.bind", category = BlockCategories.CHROME_TAB, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.CUSTOM_TAB, pluginOwner = "visualtasker.customtabs", args = listOf(textArg("packageName", required = false))),
        catalogCommand("chromeTab.create", "ChromeTab.create", category = BlockCategories.CHROME_TAB, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.CUSTOM_TAB, pluginOwner = "visualtasker.customtabs", args = listOf(textArg("url", "https://"), anyArg("options", required = false))),
        catalogCommand("chromeTab.mayLaunchUrl", "ChromeTab.mayLaunchUrl", category = BlockCategories.CHROME_TAB, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.CUSTOM_TAB, pluginOwner = "visualtasker.customtabs", args = listOf(textArg("url", "https://"))),
        catalogCommand("chromeTab.requestPostMessageChannel", "ChromeTab.requestPostMessageChannel", category = BlockCategories.CHROME_TAB, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.CUSTOM_TAB, pluginOwner = "visualtasker.customtabs", args = listOf(textArg("origin", "https://"))),
        catalogCommand("chromeTab.postMessage", "ChromeTab.postMessage", category = BlockCategories.CHROME_TAB, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.CUSTOM_TAB, pluginOwner = "visualtasker.customtabs", args = listOf(textArg("message"))),
        catalogCommand("chromeTab.validateRelationship", "ChromeTab.validateRelationship", category = BlockCategories.CHROME_TAB, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.CUSTOM_TAB, pluginOwner = "visualtasker.customtabs", args = listOf(textArg("origin", "https://"), textArg("relation", "delegate_permission/common.handle_all_urls"))),
        catalogCommand(
            id = "tasker.isInstalled",
            canonicalName = "tasker.isInstalled",
            aliases = listOf("Tasker.isInstalled"),
            category = BlockCategories.TASKER,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.TASKER,
            pluginOwner = "visualtasker.tasker",
            returnType = "Bool",
            kind = CommandCatalogKind.REPORTER,
        ),
        catalogCommand("tasker.isEnabled", "Tasker.isEnabled", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker"),
        catalogCommand("tasker.cancel", "Tasker.cancel", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker", args = listOf(textArg("name", required = false))),
        catalogCommand("tasker.getVariable", "Tasker.getVariable", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker", args = listOf(variableArg("name", "%var"))),
        catalogCommand("tasker.clearVariable", "Tasker.clearVariable", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.VARIABLE_WRITE, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker", args = listOf(variableArg("name", "%var"))),
        catalogCommand("tasker.getVariables", "Tasker.getVariables", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker", args = listOf(textArg("pattern", required = false))),
        catalogCommand("tasker.lastResult", "Tasker.lastResult", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker", args = listOf(textArg("runId", required = false))),
        catalogCommand("tasker.error", "Tasker.error", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker", args = listOf(textArg("runId", required = false))),
        catalogCommand("tasker.action", "Tasker.action", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker", args = listOf(textArg("action"), anyArg("args", required = false))),
        catalogCommand("tasker.pluginAction", "Tasker.pluginAction", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker", args = listOf(textArg("plugin"), textArg("action"), anyArg("args", required = false))),
        catalogCommand("tasker.profileEnable", "Tasker.profileEnable", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker", args = listOf(textArg("profile"))),
        catalogCommand("tasker.profileDisable", "Tasker.profileDisable", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker", args = listOf(textArg("profile"))),
        catalogCommand("tasker.profileToggle", "Tasker.profileToggle", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker", args = listOf(textArg("profile"))),
        catalogCommand("tasker.profileState", "Tasker.profileState", category = BlockCategories.TASKER, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.TASKER, pluginOwner = "visualtasker.tasker", args = listOf(textArg("profile"))),
        catalogCommand(
            id = "shizuku.isInstalled",
            canonicalName = "shizuku.isInstalled",
            aliases = listOf("Shizuku.isInstalled"),
            category = BlockCategories.SHIZUKU,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.SHIZUKU,
            pluginOwner = "visualtasker.shizuku",
            returnType = "Bool",
            kind = CommandCatalogKind.REPORTER,
        ),
        catalogCommand(
            id = "shizuku.isAvailable",
            canonicalName = "shizuku.isAvailable",
            aliases = listOf("Shizuku.isAvailable"),
            category = BlockCategories.SHIZUKU,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.SHIZUKU,
            pluginOwner = "visualtasker.shizuku",
            returnType = "Bool",
            kind = CommandCatalogKind.REPORTER,
        ),
        catalogCommand("shizuku.getUid", "Shizuku.getUid", category = BlockCategories.SHIZUKU, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.SHIZUKU, pluginOwner = "visualtasker.shizuku"),
        catalogCommand("shizuku.permissionState", "Shizuku.permissionState", category = BlockCategories.SHIZUKU, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.SHIZUKU, pluginOwner = "visualtasker.shizuku"),
        catalogCommand("shizuku.requestPermission", "Shizuku.requestPermission", category = BlockCategories.SHIZUKU, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.SHIZUKU, pluginOwner = "visualtasker.shizuku"),
        catalogCommand("shizuku.bindUserService", "Shizuku.bindUserService", category = BlockCategories.SHIZUKU, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.SHIZUKU, pluginOwner = "visualtasker.shizuku", args = listOf(textArg("component"))),
        catalogCommand("shizuku.unbindUserService", "Shizuku.unbindUserService", category = BlockCategories.SHIZUKU, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.SHIZUKU, pluginOwner = "visualtasker.shizuku", args = listOf(textArg("component"))),
        catalogCommand("shizuku.systemService", "Shizuku.systemService", category = BlockCategories.SHIZUKU, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.SHIZUKU, pluginOwner = "visualtasker.shizuku", args = listOf(textArg("name"))),
        catalogCommand("shizuku.call", "Shizuku.call", category = BlockCategories.SHIZUKU, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.SHIZUKU, pluginOwner = "visualtasker.shizuku", args = listOf(textArg("service"), textArg("method"), anyArg("args", required = false))),
        catalogCommand(
            id = "termux.isInstalled",
            canonicalName = "termux.isInstalled",
            aliases = listOf("Termux.isInstalled"),
            category = BlockCategories.TERMUX,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.TERMUX,
            pluginOwner = "visualtasker.termux",
            returnType = "Bool",
            kind = CommandCatalogKind.REPORTER,
        ),
        catalogCommand("termux.canRunCommands", "Termux.canRunCommands", category = BlockCategories.TERMUX, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.TERMUX, pluginOwner = "visualtasker.termux"),
        catalogCommand("termux.writeStdin", "Termux.writeStdin", category = BlockCategories.TERMUX, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.TERMUX, pluginOwner = "visualtasker.termux", args = listOf(textArg("sessionId"), textArg("text"))),
        catalogCommand("termux.cancel", "Termux.cancel", category = BlockCategories.TERMUX, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.TERMUX, pluginOwner = "visualtasker.termux", args = listOf(textArg("sessionId"))),
        catalogCommand("termux.get", "Termux.get", category = BlockCategories.TERMUX, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.TERMUX, pluginOwner = "visualtasker.termux", args = listOf(textArg("key"))),
        catalogCommand("scrcpy.hostAvailable", "Scrcpy.hostAvailable", category = BlockCategories.SCRCPY, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.SCRCPY, pluginOwner = "visualtasker.scrcpy"),
        catalogCommand("scrcpy.devices", "Scrcpy.devices", category = BlockCategories.SCRCPY, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.SCRCPY, pluginOwner = "visualtasker.scrcpy"),
        catalogCommand("scrcpy.connect", "Scrcpy.connect", category = BlockCategories.SCRCPY, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.SCRCPY, pluginOwner = "visualtasker.scrcpy", args = listOf(textArg("serial", required = false))),
        catalogCommand("scrcpy.disconnect", "Scrcpy.disconnect", category = BlockCategories.SCRCPY, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.SCRCPY, pluginOwner = "visualtasker.scrcpy", args = listOf(textArg("serial", required = false))),
        catalogCommand("scrcpy.isRunning", "Scrcpy.isRunning", category = BlockCategories.SCRCPY, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.SCRCPY, pluginOwner = "visualtasker.scrcpy", args = listOf(textArg("serial", required = false))),
        catalogCommand("scrcpy.get", "Scrcpy.get", category = BlockCategories.SCRCPY, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.SCRCPY, pluginOwner = "visualtasker.scrcpy", args = listOf(textArg("key"))),
        catalogCommand("scrcpy.key", "Scrcpy.key", category = BlockCategories.SCRCPY, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.SCRCPY, pluginOwner = "visualtasker.scrcpy", args = listOf(textArg("key"), numberArg("repeat", "1", required = false))),
        catalogCommand("scrcpy.text", "Scrcpy.text", category = BlockCategories.SCRCPY, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.SCRCPY, pluginOwner = "visualtasker.scrcpy", args = listOf(textArg("text"))),
        catalogCommand("scrcpy.scroll", "Scrcpy.scroll", category = BlockCategories.SCRCPY, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.SCRCPY, pluginOwner = "visualtasker.scrcpy", args = listOf(numberArg("x", "0"), numberArg("y", "0"), numberArg("deltaY", "1"), numberArg("deltaX", "0", required = false))),
        catalogCommand("scrcpy.setClipboard", "Scrcpy.setClipboard", category = BlockCategories.SCRCPY, sideEffect = CommandSideEffect.VARIABLE_WRITE, capability = CommandCapability.SCRCPY, pluginOwner = "visualtasker.scrcpy", args = listOf(textArg("text"))),
        catalogCommand("scrcpy.setScreenPower", "Scrcpy.setScreenPower", category = BlockCategories.SCRCPY, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.SCRCPY, pluginOwner = "visualtasker.scrcpy", args = listOf(boolArg("on", "true"))),
        catalogCommand("scrcpy.rotate", "Scrcpy.rotate", category = BlockCategories.SCRCPY, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.SCRCPY, pluginOwner = "visualtasker.scrcpy", args = listOf(textArg("direction", "right", required = false))),
        catalogCommand("chart.hide", "Chart.hide", category = BlockCategories.CHARTS, sideEffect = CommandSideEffect.UI_INPUT, capability = CommandCapability.CHARTS, pluginOwner = "visualtasker.charts", args = listOf(textArg("id"))),
        catalogCommand("chart.remove", "Chart.remove", category = BlockCategories.CHARTS, sideEffect = CommandSideEffect.VARIABLE_WRITE, capability = CommandCapability.CHARTS, pluginOwner = "visualtasker.charts", args = listOf(textArg("id"))),
        catalogCommand("chart.exists", "Chart.exists", category = BlockCategories.CHARTS, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.CHARTS, pluginOwner = "visualtasker.charts", args = listOf(textArg("id"))),
        catalogCommand("chart.setData", "Chart.setData", category = BlockCategories.CHARTS, sideEffect = CommandSideEffect.VARIABLE_WRITE, capability = CommandCapability.CHARTS, pluginOwner = "visualtasker.charts", args = listOf(textArg("id"), anyArg("data"))),
        catalogCommand("chart.setOptions", "Chart.setOptions", category = BlockCategories.CHARTS, sideEffect = CommandSideEffect.VARIABLE_WRITE, capability = CommandCapability.CHARTS, pluginOwner = "visualtasker.charts", args = listOf(textArg("id"), anyArg("options"))),
        catalogCommand("chart.add", "Chart.add", category = BlockCategories.CHARTS, sideEffect = CommandSideEffect.VARIABLE_WRITE, capability = CommandCapability.CHARTS, pluginOwner = "visualtasker.charts", args = listOf(textArg("id"), anyArg("data"))),
        catalogCommand("chart.update", "Chart.update", category = BlockCategories.CHARTS, sideEffect = CommandSideEffect.VARIABLE_WRITE, capability = CommandCapability.CHARTS, pluginOwner = "visualtasker.charts", args = listOf(textArg("id"), anyArg("patch"))),
        catalogCommand("chart.removeData", "Chart.removeData", category = BlockCategories.CHARTS, sideEffect = CommandSideEffect.VARIABLE_WRITE, capability = CommandCapability.CHARTS, pluginOwner = "visualtasker.charts", args = listOf(textArg("id"), anyArg("selector"))),
        catalogCommand("chart.clear", "Chart.clear", category = BlockCategories.CHARTS, sideEffect = CommandSideEffect.VARIABLE_WRITE, capability = CommandCapability.CHARTS, pluginOwner = "visualtasker.charts", args = listOf(textArg("id"))),
        catalogCommand("chart.get", "Chart.get", category = BlockCategories.CHARTS, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.CHARTS, pluginOwner = "visualtasker.charts", args = listOf(textArg("id"), textArg("key", required = false))),
        catalogCommand("chart.capture", "Chart.capture", category = BlockCategories.CHARTS, sideEffect = CommandSideEffect.SCREEN_READ, capability = CommandCapability.CHARTS, pluginOwner = "visualtasker.charts", args = listOf(textArg("id"), textArg("path", required = false))),
    )

    private val entries: List<CommandCatalogEntry> = listOf(
        NativeCommandLegacyDefinitions.requireEntry(BlockTypes.EVENT_START),
        WaitCommandCompatibility.legacyEntry,
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
                CommandArgument("retryCount", CommandArgumentType.NUMBER, required = false, defaultValue = "1"),
                CommandArgument("searchRegion", CommandArgumentType.REGION, required = false, defaultValue = ""),
            ),
        ),
        catalogCommand(
            id = "action.swipe",
            canonicalName = "swipe",
            aliases = listOf("SWIPE"),
            category = BlockCategories.INPUT,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.A11Y,
            args = listOf(
                CommandArgument("points", CommandArgumentType.ANY, defaultValue = ""),
                CommandArgument("repeat", CommandArgumentType.ANY, required = false, defaultValue = "1"),
            ),
        ),
        catalogCommand(
            id = "input.clickPoint",
            canonicalName = "clickPoint",
            aliases = listOf("CLICK_POINT", "tap"),
            category = BlockCategories.INPUT,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.A11Y,
            args = listOf(
                CommandArgument("x", CommandArgumentType.NUMBER, defaultValue = "0"),
                CommandArgument("y", CommandArgumentType.NUMBER, defaultValue = "0"),
                CommandArgument("repeat", CommandArgumentType.NUMBER, required = false, defaultValue = "1"),
            ),
        ),
        catalogCommand(
            id = "input.touch",
            canonicalName = "touch",
            aliases = listOf("Touch.dispatch", "TOUCH"),
            category = BlockCategories.INPUT,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.A11Y,
            args = listOf(CommandArgument("sequence", CommandArgumentType.ANY, defaultValue = "")),
        ),
        catalogCommand(
            id = "vision.screenshot",
            canonicalName = "screenshot",
            aliases = listOf("VISION.SCREENSHOT", "ScreenCapture.capture"),
            category = BlockCategories.VISION,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.SCREEN_CAPTURE,
            args = listOf(CommandArgument("path", CommandArgumentType.TEXT, required = false, defaultValue = "")),
        ),
        catalogCommand(
            id = "vision.ocr",
            canonicalName = "ocr",
            aliases = listOf("Region.readText", "readText", "OCR"),
            category = BlockCategories.VISION,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.VISION,
            args = listOf(
                CommandArgument("region", CommandArgumentType.REGION, required = false, defaultValue = ""),
                CommandArgument("timeoutMs", CommandArgumentType.DURATION_MS, required = false, defaultValue = "3000"),
            ),
        ),
        catalogCommand(
            id = "vision.findText",
            canonicalName = "findText",
            aliases = listOf("Region.findText", "FIND_TEXT"),
            category = BlockCategories.VISION,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.VISION,
            args = listOf(
                CommandArgument("text", CommandArgumentType.TEXT, defaultValue = ""),
                CommandArgument("timeoutMs", CommandArgumentType.DURATION_MS, required = false, defaultValue = "3000"),
            ),
        ),
        catalogCommand(
            id = "vision.highlight",
            canonicalName = "highlight",
            aliases = listOf("Region.highlight", "Overlay.highlight"),
            category = BlockCategories.VISION,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.VISION,
            args = listOf(CommandArgument("region", CommandArgumentType.REGION, defaultValue = "")),
        ),
        catalogCommand(
            id = "vision.markerSave",
            canonicalName = "markerSave",
            aliases = listOf("Marker.save", "SAVE_MARKER"),
            category = BlockCategories.VISION,
            sideEffect = CommandSideEffect.VARIABLE_WRITE,
            capability = CommandCapability.VISION,
            pluginOwner = "visualtasker.vision",
            args = listOf(
                CommandArgument("name", CommandArgumentType.TEXT, defaultValue = "marker"),
                CommandArgument("region", CommandArgumentType.REGION, defaultValue = ""),
                CommandArgument("mode", CommandArgumentType.TEXT, required = false, defaultValue = "region"),
                CommandArgument("threshold", CommandArgumentType.PERCENT, required = false, defaultValue = "0.85"),
            ),
        ),
        catalogCommand(
            id = "vision.markerLoad",
            canonicalName = "markerLoad",
            aliases = listOf("Marker.load", "LOAD_MARKER"),
            category = BlockCategories.VISION,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.VISION,
            pluginOwner = "visualtasker.vision",
            args = listOf(CommandArgument("name", CommandArgumentType.TEXT, defaultValue = "marker")),
        ),
        catalogCommand(
            id = "vision.markerDelete",
            canonicalName = "markerDelete",
            aliases = listOf("Marker.delete", "DELETE_MARKER"),
            category = BlockCategories.VISION,
            sideEffect = CommandSideEffect.VARIABLE_WRITE,
            capability = CommandCapability.VISION,
            pluginOwner = "visualtasker.vision",
            args = listOf(CommandArgument("name", CommandArgumentType.TEXT, defaultValue = "marker")),
        ),
        catalogCommand(
            id = "vision.templateDefine",
            canonicalName = "templateDefine",
            aliases = listOf("Template.define", "DEFINE_TEMPLATE"),
            category = BlockCategories.VISION,
            sideEffect = CommandSideEffect.VARIABLE_WRITE,
            capability = CommandCapability.VISION,
            pluginOwner = "visualtasker.vision",
            args = listOf(
                CommandArgument("name", CommandArgumentType.TEXT, defaultValue = "template"),
                CommandArgument("region", CommandArgumentType.REGION, defaultValue = ""),
                CommandArgument("processing", CommandArgumentType.TEXT, required = false, defaultValue = "grayscale"),
            ),
        ),
        catalogCommand(
            id = "vision.templateCompare",
            canonicalName = "templateCompare",
            aliases = listOf("Template.compare", "COMPARE_TEMPLATE"),
            category = BlockCategories.VISION,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.VISION,
            pluginOwner = "visualtasker.vision",
            args = listOf(
                CommandArgument("name", CommandArgumentType.TEXT, defaultValue = "template"),
                CommandArgument("region", CommandArgumentType.REGION, defaultValue = ""),
                CommandArgument("processing", CommandArgumentType.TEXT, required = false, defaultValue = "grayscale"),
            ),
            returnType = "Number",
            kind = CommandCatalogKind.REPORTER,
        ),
        catalogCommand(
            id = "scene.save",
            canonicalName = "sceneSave",
            aliases = listOf("Scene.save", "SAVE_SCENE"),
            category = BlockCategories.SCENE,
            sideEffect = CommandSideEffect.VARIABLE_WRITE,
            capability = CommandCapability.CORE,
            args = listOf(
                CommandArgument("name", CommandArgumentType.TEXT, defaultValue = "scene"),
                CommandArgument("markerMode", CommandArgumentType.TEXT, required = false, defaultValue = "region"),
                CommandArgument("region", CommandArgumentType.REGION, required = false, defaultValue = ""),
                CommandArgument("asset", CommandArgumentType.TEXT, required = false, defaultValue = ""),
            ),
        ),
        catalogCommand(
            id = "system.datastorePut",
            canonicalName = "datastorePut",
            aliases = listOf("Datastore.put", "DATASTORE_PUT"),
            category = BlockCategories.SYSTEM,
            sideEffect = CommandSideEffect.VARIABLE_WRITE,
            capability = CommandCapability.CORE,
            args = listOf(
                CommandArgument(
                    "key",
                    CommandArgumentType.TEXT,
                    defaultValue = "key",
                    acceptedTypes = setOf("Text"),
                ),
                CommandArgument(
                    "value",
                    CommandArgumentType.TEXT,
                    defaultValue = "value",
                    acceptedTypes = setOf("Text"),
                ),
            ),
        ),
        catalogCommand(
            id = "system.datastoreGet",
            canonicalName = "datastoreGet",
            aliases = listOf("Datastore.get", "DATASTORE_GET"),
            category = BlockCategories.SYSTEM,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.CORE,
            args = listOf(CommandArgument("key", CommandArgumentType.TEXT, defaultValue = "key")),
            returnType = "String?",
            kind = CommandCatalogKind.REPORTER,
        ),
        BeepCommandCompatibility.legacyEntry,
        statement(
            id = "feedback.vibrate",
            canonicalName = "vibrate",
            aliases = listOf("VIBRATE"),
            category = BlockCategories.FEEDBACK,
            blockType = BlockTypes.FEEDBACK_VIBRATE,
            sideEffect = CommandSideEffect.FEEDBACK,
            capability = CommandCapability.FEEDBACK,
            args = listOf(
                CommandArgument(
                    name = "patternMs",
                    type = CommandArgumentType.NUMBER,
                    required = true,
                    acceptedTypes = setOf("Number"),
                    variadic = true,
                ),
            ),
        ),
        statement(
            id = "debug.log",
            canonicalName = "log",
            aliases = listOf("LOG"),
            category = BlockCategories.DEBUG,
            blockType = BlockTypes.DEBUG_LOG,
            sideEffect = CommandSideEffect.LOGGING,
            capability = CommandCapability.DEBUG,
            args = listOf(
                CommandArgument(
                    name = "value",
                    type = CommandArgumentType.ANY,
                    defaultValue = "\"debug\"",
                    acceptedTypes = setOf("Any", "Number", "Boolean", "Text"),
                ),
            ),
        ),
        catalogCommand(
            id = "file.readText",
            canonicalName = "File.readText",
            category = BlockCategories.FILE,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.CORE,
            args = listOf(CommandArgument("path", CommandArgumentType.TEXT, defaultValue = "")),
            returnType = "String?",
            kind = CommandCatalogKind.REPORTER,
        ),
        catalogCommand(
            id = EmscriptV1NamingNormalizations.FILE_WRITE_TEXT.stableId,
            canonicalName = EmscriptV1NamingNormalizations.FILE_WRITE_TEXT.canonicalName,
            aliases = EmscriptV1NamingNormalizations.FILE_WRITE_TEXT.legacyAliases,
            category = BlockCategories.FILE,
            sideEffect = CommandSideEffect.VARIABLE_WRITE,
            capability = CommandCapability.CORE,
            args = listOf(
                CommandArgument("path", CommandArgumentType.TEXT, defaultValue = ""),
                CommandArgument("text", CommandArgumentType.TEXT, defaultValue = ""),
            ),
        ),
        catalogCommand(
            id = "clipboard.get",
            canonicalName = "Clipboard.get",
            category = BlockCategories.SYSTEM,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.CORE,
            returnType = "String",
            kind = CommandCatalogKind.REPORTER,
        ),
        catalogCommand(
            id = EmscriptV1NamingNormalizations.CLIPBOARD_SET.stableId,
            canonicalName = EmscriptV1NamingNormalizations.CLIPBOARD_SET.canonicalName,
            aliases = EmscriptV1NamingNormalizations.CLIPBOARD_SET.legacyAliases,
            category = BlockCategories.SYSTEM,
            sideEffect = CommandSideEffect.VARIABLE_WRITE,
            capability = CommandCapability.CORE,
            args = listOf(CommandArgument("text", CommandArgumentType.TEXT, defaultValue = "")),
        ),
        catalogCommand(
            id = EmscriptV1NamingNormalizations.CACHE_CLEAR.stableId,
            canonicalName = EmscriptV1NamingNormalizations.CACHE_CLEAR.canonicalName,
            aliases = EmscriptV1NamingNormalizations.CACHE_CLEAR.legacyAliases,
            category = BlockCategories.SYSTEM,
            sideEffect = CommandSideEffect.VARIABLE_WRITE,
            capability = CommandCapability.CORE,
        ),
        catalogCommand(
            id = "system.info",
            canonicalName = "Sys.info",
            category = BlockCategories.SYSTEM,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.CORE,
            returnType = "String",
            kind = CommandCatalogKind.REPORTER,
        ),
        catalogCommand(
            id = "system.env",
            canonicalName = "Env.get",
            category = BlockCategories.SYSTEM,
            sideEffect = CommandSideEffect.SCREEN_READ,
            capability = CommandCapability.CORE,
            args = listOf(CommandArgument("name", CommandArgumentType.TEXT, defaultValue = "")),
            returnType = "String",
            kind = CommandCatalogKind.REPORTER,
        ),
        catalogCommand(
            id = "chromeTab.open",
            canonicalName = "ChromeTab.open",
            category = BlockCategories.CHROME_TAB,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.CUSTOM_TAB,
            pluginOwner = "visualtasker.customtabs",
            args = listOf(
                CommandArgument("url", CommandArgumentType.TEXT, defaultValue = "https://"),
                CommandArgument("options", CommandArgumentType.ANY, required = false, defaultValue = ""),
            ),
        ),
        catalogCommand(
            id = "chromeTab.close",
            canonicalName = "ChromeTab.unbind",
            category = BlockCategories.CHROME_TAB,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.CUSTOM_TAB,
            pluginOwner = "visualtasker.customtabs",
        ),
        catalogCommand(
            id = "tasker.runTask",
            canonicalName = "Tasker.runTask",
            category = BlockCategories.TASKER,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.TASKER,
            pluginOwner = "visualtasker.tasker",
            args = listOf(
                CommandArgument("name", CommandArgumentType.TEXT, defaultValue = ""),
                CommandArgument("parameters", CommandArgumentType.ANY, required = false, defaultValue = ""),
            ),
        ),
        catalogCommand(
            id = "tasker.setVariable",
            canonicalName = "Tasker.setVariable",
            category = BlockCategories.TASKER,
            sideEffect = CommandSideEffect.VARIABLE_WRITE,
            capability = CommandCapability.TASKER,
            pluginOwner = "visualtasker.tasker",
            args = listOf(
                CommandArgument("name", CommandArgumentType.VARIABLE_REF, defaultValue = "%var"),
                CommandArgument("value", CommandArgumentType.ANY, defaultValue = ""),
            ),
        ),
        catalogCommand(
            id = "tasker.emitEvent",
            canonicalName = "Tasker.emitEvent",
            category = BlockCategories.TASKER,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.TASKER,
            pluginOwner = "visualtasker.tasker",
            args = listOf(CommandArgument("name", CommandArgumentType.TEXT, defaultValue = "")),
        ),
        catalogCommand(
            id = "shizuku.exec",
            canonicalName = "Shizuku.exec",
            category = BlockCategories.SHIZUKU,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.SHIZUKU,
            pluginOwner = "visualtasker.shizuku",
            args = listOf(
                CommandArgument("command", CommandArgumentType.TEXT, defaultValue = ""),
                CommandArgument("args", CommandArgumentType.ANY, required = false, defaultValue = ""),
            ),
        ),
        catalogCommand(
            id = "shizuku.shell",
            canonicalName = "Shizuku.shell",
            category = BlockCategories.SHIZUKU,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.SHIZUKU,
            pluginOwner = "visualtasker.shizuku",
            args = listOf(CommandArgument("commandLine", CommandArgumentType.TEXT, defaultValue = "")),
        ),
        catalogCommand(
            id = "termux.run",
            canonicalName = "Termux.run",
            category = BlockCategories.TERMUX,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.TERMUX,
            pluginOwner = "visualtasker.termux",
            args = listOf(
                CommandArgument("path", CommandArgumentType.TEXT, defaultValue = ""),
                CommandArgument("args", CommandArgumentType.ANY, required = false, defaultValue = ""),
            ),
        ),
        catalogCommand(
            id = "termux.shell",
            canonicalName = "Termux.shell",
            category = BlockCategories.TERMUX,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.TERMUX,
            pluginOwner = "visualtasker.termux",
            args = listOf(CommandArgument("commandLine", CommandArgumentType.TEXT, defaultValue = "")),
        ),
        catalogCommand(
            id = "termux.api",
            canonicalName = "Termux.api",
            category = BlockCategories.TERMUX,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.TERMUX,
            pluginOwner = "visualtasker.termux",
            args = listOf(CommandArgument("command", CommandArgumentType.TEXT, defaultValue = "battery-status")),
        ),
        catalogCommand(
            id = "scrcpy.start",
            canonicalName = "Scrcpy.start",
            category = BlockCategories.SCRCPY,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.SCRCPY,
            pluginOwner = "visualtasker.scrcpy",
            args = listOf(CommandArgument("device", CommandArgumentType.TEXT, required = false, defaultValue = "")),
        ),
        catalogCommand(
            id = "scrcpy.stop",
            canonicalName = "Scrcpy.stop",
            category = BlockCategories.SCRCPY,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.SCRCPY,
            pluginOwner = "visualtasker.scrcpy",
            args = listOf(CommandArgument("session", CommandArgumentType.ANY, defaultValue = "")),
        ),
        catalogCommand(
            id = "scrcpy.touch",
            canonicalName = "Scrcpy.touch",
            category = BlockCategories.SCRCPY,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.SCRCPY,
            pluginOwner = "visualtasker.scrcpy",
            args = listOf(
                CommandArgument("session", CommandArgumentType.ANY, defaultValue = ""),
                CommandArgument("action", CommandArgumentType.TEXT, defaultValue = "tap"),
                CommandArgument("point", CommandArgumentType.ANY, defaultValue = ""),
            ),
        ),
        catalogCommand(
            id = "chart.create",
            canonicalName = "Chart.create",
            category = BlockCategories.CHARTS,
            sideEffect = CommandSideEffect.VARIABLE_WRITE,
            capability = CommandCapability.CHARTS,
            pluginOwner = "visualtasker.charts",
            args = listOf(
                CommandArgument("type", CommandArgumentType.TEXT, defaultValue = "line"),
                CommandArgument("data", CommandArgumentType.ANY, defaultValue = ""),
            ),
        ),
        catalogCommand(
            id = "chart.show",
            canonicalName = "Chart.show",
            category = BlockCategories.CHARTS,
            sideEffect = CommandSideEffect.UI_INPUT,
            capability = CommandCapability.CHARTS,
            pluginOwner = "visualtasker.charts",
            args = listOf(CommandArgument("chart", CommandArgumentType.ANY, defaultValue = "")),
        ),
        catalogCommand(
            id = "chart.export",
            canonicalName = "Chart.export",
            category = BlockCategories.CHARTS,
            sideEffect = CommandSideEffect.VARIABLE_WRITE,
            capability = CommandCapability.CHARTS,
            pluginOwner = "visualtasker.charts",
            args = listOf(
                CommandArgument("chart", CommandArgumentType.ANY, defaultValue = ""),
                CommandArgument("path", CommandArgumentType.TEXT, defaultValue = ""),
            ),
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
        ).copy(role = CommandCatalogRole.LEGACY_EXPRESSION_ALIAS),
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
        ).copy(role = CommandCatalogRole.LEGACY_EXPRESSION_ALIAS),
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
        ).copy(role = CommandCatalogRole.CANONICAL_EXPRESSION_PROJECTION),
    ) + remFlowNodeCommands + plannedAdapterCommands

    private val remFlowNodeCommands: List<CommandCatalogEntry>
        get() = listOf(
        remFlowNode(
            id = "rem.region",
            canonicalName = "rem.region",
            aliases = listOf("REM_REGION"),
            args = listOf(
                textArg("name", "region"),
                textArg("mode", "facet"),
                textArg("color", "auto", required = false),
            ),
        ),
        remFlowNode(
            id = "rem.variableBulk",
            canonicalName = "rem.variableBulk",
            aliases = listOf("REM_VARIABLE_BULK"),
            args = listOf(
                textArg("name", "variables"),
                textArg("layout", "stack", required = false),
                anyArg("variables", required = false),
            ),
        ),
        remFlowNode(
            id = "rem.expressionCapsule",
            canonicalName = "rem.expressionCapsule",
            aliases = listOf("REM_EXPRESSION_CAPSULE"),
            args = listOf(
                textArg("name", "expression"),
                textArg("strategy", "collapse", required = false),
                anyArg("nodes", required = false),
            ),
        ),
        remFlowNode(
            id = "rem.flowBreak",
            canonicalName = "rem.flowBreak",
            aliases = listOf("REM_FLOW_BREAK"),
            args = listOf(
                textArg("label", "break"),
                textArg("direction", "right", required = false),
            ),
        ),
        remFlowNode(
            id = "rem.offPageOut",
            canonicalName = "rem.offPageOut",
            aliases = listOf("REM_OFF_PAGE_OUT"),
            args = listOf(textArg("connector", "A")),
        ),
        remFlowNode(
            id = "rem.offPageIn",
            canonicalName = "rem.offPageIn",
            aliases = listOf("REM_OFF_PAGE_IN"),
            args = listOf(textArg("connector", "A")),
        ),
        remFlowNode(
            id = "rem.group",
            canonicalName = "rem.group",
            aliases = listOf("REM_GROUP"),
            args = listOf(
                textArg("name", "group"),
                boolArg("active", "true", required = false),
            ),
        ),
        remFlowNode(
            id = "rem.layoutHint",
            canonicalName = "rem.layoutHint",
            aliases = listOf("REM_LAYOUT_HINT"),
            args = listOf(
                textArg("mode", "vertical"),
                textArg("scope", "next", required = false),
            ),
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

    fun findLanguageCommandByAcceptedName(name: String): CommandCatalogEntry? =
        byAcceptedName[name.lowercase()]?.firstOrNull { it.role == CommandCatalogRole.LANGUAGE_COMMAND }

    fun blockTypes(): Set<String> = byBlockType.keys

    fun acceptedNamesForKinds(vararg kinds: CommandCatalogKind): Set<String> {
        val acceptedKinds = kinds.toSet()
        return entries
            .filter { it.kind in acceptedKinds && it.role == CommandCatalogRole.LANGUAGE_COMMAND }
            .flatMap { entry -> entry.acceptedAliases + entry.canonicalName }
            .toSet()
    }

    fun acceptedNamesForRuntime(): Set<String> =
        entries
            .filter { it.runtime != null && it.role == CommandCatalogRole.LANGUAGE_COMMAND }
            .flatMap { entry -> entry.acceptedAliases + entry.canonicalName }
            .toSet()

    fun capabilityDescriptors(): List<CommandCapabilityDescriptor> =
        entries.map(CommandCatalogEntry::toCapabilityDescriptor)

    fun runtimeCapabilityDescriptors(): List<CommandCapabilityDescriptor> =
        capabilityDescriptors().filter { it.requiredAdapter != null }

    fun capabilityDescriptorForAcceptedName(name: String): CommandCapabilityDescriptor? =
        findLanguageCommandByAcceptedName(name)?.toCapabilityDescriptor()

    fun metadataForBlockType(blockType: String): Map<String, String> {
        val entry = findByBlockType(blockType) ?: return emptyMap()
        return mapOf(
            METADATA_COMMAND_ID to entry.id,
            METADATA_CANONICAL_NAME to entry.canonicalName,
            METADATA_COMMAND_KIND to entry.kind.name,
            METADATA_PLUGIN_OWNER to entry.pluginOwner,
            METADATA_RUNTIME_CAPABILITIES to entry.capabilities.joinToString(",") { it.name },
            METADATA_DISPLAY_NAME to entry.canonicalName,
            METADATA_SHORT_NAME to entry.shortDisplayName(),
            METADATA_RUNTIME_STATUS to (entry.runtime?.dryRunBehavior ?: "unknown"),
        )
    }

    fun validate(): List<CommandCatalogDiagnostic> = validateCommandCatalog(entries)
}

fun CommandCatalogEntry.toCapabilityDescriptor(): CommandCapabilityDescriptor =
    CommandCapabilityDescriptor(
        id = id,
        canonicalName = canonicalName,
        acceptedNames = (acceptedAliases + canonicalName).toSet(),
        kind = kind,
        category = category,
        arguments = arguments,
        returnType = returnType,
        sideEffect = sideEffect,
        declaredCapabilities = capabilities,
        requiredAdapter = runtime?.liveCapabilityGate,
        pluginOwner = pluginOwner,
        blockType = block?.blockType,
        flowNodeKind = flowchart?.nodeKind,
        dryRunBehavior = runtime?.dryRunBehavior ?: "none",
        liveImplemented = runtime?.liveImplemented == true,
        diagnosticCode = runtime?.diagnosticCode ?: "CAPABILITY_NOT_EXECUTABLE",
    )

fun validateCommandCatalog(entries: List<CommandCatalogEntry>): List<CommandCatalogDiagnostic> = buildList {
    entries
        .filter { it.id.isBlank() }
        .forEach { add(CommandCatalogDiagnostic(CommandCatalogDiagnosticCode.BLANK_ID, it.id, "Command id must not be blank")) }
    entries
        .groupBy { it.id }
        .filter { (id, group) -> id.isNotBlank() && group.size > 1 }
        .forEach { (id, _) -> add(CommandCatalogDiagnostic(CommandCatalogDiagnosticCode.DUPLICATE_ID, id, "Duplicate command id: $id")) }
    entries
        .filter { it.canonicalName.isBlank() }
        .forEach { add(CommandCatalogDiagnostic(CommandCatalogDiagnosticCode.BLANK_CANONICAL_NAME, it.id, "Canonical command name must not be blank")) }
    entries
        .mapNotNull { entry -> entry.block?.blockType?.let { blockType -> blockType to entry.id } }
        .groupBy { it.first }
        .filterValues { it.size > 1 }
        .forEach { (blockType, owners) ->
            add(CommandCatalogDiagnostic(CommandCatalogDiagnosticCode.DUPLICATE_BLOCK_BINDING, blockType, "Block binding $blockType is used by ${owners.map { it.second }}"))
        }
    val knownCategories = BlockCategories.all.map { it.id }.toSet()
    entries
        .filter { it.category !in knownCategories }
        .forEach { add(CommandCatalogDiagnostic(CommandCatalogDiagnosticCode.UNKNOWN_CATEGORY, it.id, "Unknown command category: ${it.category}")) }
    entries.forEach { entry ->
        entry.arguments
            .groupBy { it.name }
            .filter { (name, group) -> name.isNotBlank() && group.size > 1 }
            .forEach { (name, _) ->
                add(CommandCatalogDiagnostic(CommandCatalogDiagnosticCode.DUPLICATE_ARGUMENT, entry.id, "Duplicate argument $name in ${entry.id}"))
            }
        entry.arguments.forEachIndexed { index, argument ->
            if (argument.variadic && index != entry.arguments.lastIndex) {
                add(
                    CommandCatalogDiagnostic(
                        CommandCatalogDiagnosticCode.VARIADIC_ARGUMENT_NOT_LAST,
                        entry.id,
                        "Variadic argument ${argument.name} must be last in ${entry.id}",
                    ),
                )
            }
        }
        entry.runtime?.let { runtime ->
            if (runtime.liveCapabilityGate !in entry.capabilities) {
                add(CommandCatalogDiagnostic(CommandCatalogDiagnosticCode.RUNTIME_CAPABILITY_NOT_DECLARED, entry.id, "Runtime capability ${runtime.liveCapabilityGate} is not declared by ${entry.id}"))
            }
        }
        if (entry.kind in setOf(CommandCatalogKind.EVENT, CommandCatalogKind.STATEMENT, CommandCatalogKind.CONTROL, CommandCatalogKind.VARIABLE) && entry.runtime == null) {
            add(CommandCatalogDiagnostic(CommandCatalogDiagnosticCode.EXECUTABLE_COMMAND_WITHOUT_RUNTIME, entry.id, "Executable command ${entry.id} must define a runtime binding"))
        }
        if (entry.kind in setOf(CommandCatalogKind.REPORTER, CommandCatalogKind.OPERATOR) && entry.returnType.isNullOrBlank()) {
            add(CommandCatalogDiagnostic(CommandCatalogDiagnosticCode.VALUE_COMMAND_WITHOUT_RETURN_TYPE, entry.id, "Value command ${entry.id} must define a return type"))
        }
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
            VisualTaskerCommandCatalog.METADATA_DISPLAY_NAME to entry.canonicalName,
            VisualTaskerCommandCatalog.METADATA_SHORT_NAME to entry.shortDisplayName(),
            VisualTaskerCommandCatalog.METADATA_RUNTIME_STATUS to (entry.runtime?.dryRunBehavior ?: "unknown"),
        ),
    )
}

internal fun CommandCatalogEntry.shortDisplayName(): String =
    when (canonicalName) {
        "ChromeTab.requestPostMessageChannel" -> "requestMsg"
        "ChromeTab.validateRelationship" -> "validateRel"
        "Tasker.pluginAction" -> "pluginAct"
        "Tasker.lastResult" -> "lastResult"
        "Tasker.profileEnable" -> "profileOn"
        "Tasker.profileDisable" -> "profileOff"
        "Tasker.profileToggle" -> "profileTog"
        "Tasker.profileState" -> "profileState"
        "Shizuku.permissionState" -> "permission"
        "Shizuku.requestPermission" -> "requestPerm"
        "Shizuku.bindUserService" -> "bindService"
        "Shizuku.unbindUserService" -> "unbindSvc"
        "Shizuku.systemService" -> "service"
        "Termux.canRunCommands" -> "canRun"
        "Termux.writeStdin" -> "stdin"
        "Scrcpy.hostAvailable" -> "hostReady"
        "Scrcpy.setClipboard" -> "clipboard"
        "Scrcpy.setScreenPower" -> "screenPower"
        else -> canonicalName.substringAfterLast('.')
    }

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

private fun catalogCommand(
    id: String,
    canonicalName: String,
    aliases: List<String> = emptyList(),
    category: String,
    sideEffect: CommandSideEffect,
    capability: CommandCapability,
    pluginOwner: String = "visualtasker.core",
    args: List<CommandArgument> = emptyList(),
    returnType: String? = null,
    kind: CommandCatalogKind = CommandCatalogKind.STATEMENT,
): CommandCatalogEntry = CommandCatalogEntry(
    id = id,
    canonicalName = canonicalName,
    acceptedAliases = aliases,
    kind = kind,
    category = category,
    arguments = args,
    returnType = returnType,
    sideEffect = sideEffect,
    capabilities = setOf(CommandCapability.CORE, capability),
    pluginOwner = pluginOwner,
    block = CommandBlockBinding(BlockTypes.EMSCRIPT_COMMAND_PREFIX + id),
    flowchart = CommandFlowchartBinding(category),
    runtime = CommandRuntimeBinding(
        dryRunBehavior = when {
            pluginOwner == "visualtasker.core" &&
        capability in setOf(CommandCapability.CORE, CommandCapability.A11Y, CommandCapability.SCREEN_CAPTURE) &&
                canonicalName != "touch" -> "simulate"
            else -> "adapter-gated"
        },
        liveCapabilityGate = capability,
        liveImplemented = canonicalName != "touch",
    ),
)

fun CommandCatalogEntry.canBeUsedAsExpression(): Boolean =
    returnType != null && !returnType.equals("Void", ignoreCase = true)

private fun remFlowNode(
    id: String,
    canonicalName: String,
    aliases: List<String> = emptyList(),
    args: List<CommandArgument> = emptyList(),
): CommandCatalogEntry = CommandCatalogEntry(
    id = id,
    canonicalName = canonicalName,
    acceptedAliases = aliases,
    kind = CommandCatalogKind.STATEMENT,
    category = BlockCategories.REM_FLOW_NODES,
    arguments = args,
    sideEffect = CommandSideEffect.CONTROL_FLOW,
    capabilities = setOf(CommandCapability.CORE),
    pluginOwner = "visualtasker.flowchart",
    block = CommandBlockBinding(BlockTypes.EMSCRIPT_COMMAND_PREFIX + id),
    flowchart = CommandFlowchartBinding("rem-flow-node"),
    runtime = CommandRuntimeBinding("visual-metadata", CommandCapability.CORE),
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

private fun textArg(
    name: String,
    defaultValue: String = "",
    required: Boolean = true,
): CommandArgument = CommandArgument(name, CommandArgumentType.TEXT, required = required, defaultValue = defaultValue)

private fun anyArg(
    name: String,
    defaultValue: String = "{}",
    required: Boolean = true,
): CommandArgument = CommandArgument(name, CommandArgumentType.ANY, required = required, defaultValue = defaultValue)

private fun numberArg(
    name: String,
    defaultValue: String = "0",
    required: Boolean = true,
): CommandArgument = CommandArgument(name, CommandArgumentType.NUMBER, required = required, defaultValue = defaultValue)

private fun boolArg(
    name: String,
    defaultValue: String = "true",
    required: Boolean = true,
): CommandArgument = CommandArgument(name, CommandArgumentType.BOOLEAN, required = required, defaultValue = defaultValue)

private fun variableArg(
    name: String,
    defaultValue: String = "",
    required: Boolean = true,
): CommandArgument = CommandArgument(name, CommandArgumentType.VARIABLE_REF, required = required, defaultValue = defaultValue)
