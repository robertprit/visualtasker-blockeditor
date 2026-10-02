package de.visualtasker.blockeditor.registry

enum class QueryInfrastructureFit {
    EXISTING_SCALAR,
    NEW_STRUCTURED_VALUE,
    NEW_COLLECTION_AND_STRUCTURED_VALUE,
}

data class RemainingQueryReturnDecision(
    val commandId: String,
    val meaning: String,
    val currentReturnDeclaration: String,
    val currentRuntimeValue: String,
    val proposedV1ReturnType: String,
    val nullable: Boolean,
    val absentSemantics: String,
    val negativeResultSemantics: String,
    val failureSemantics: String,
    val firstLossPoint: String,
    val currentTransportPath: String,
    val infrastructureFit: QueryInfrastructureFit,
    val existingEmscriptValue: String,
    val requiredNewValueType: String,
    val diagnostics: List<String>,
    val genericCommandCallSufficient: Boolean = true,
    val genericReporterSufficient: Boolean = true,
    val roundtripAndLegacyRisk: String,
    val migrationGroup: String,
)

data class RemainingQueryMigrationGroup(
    val id: String,
    val name: String,
    val commandIds: List<String>,
    val sharedContract: String,
    val requiredInfrastructure: String,
    val diagnostics: String,
    val expectedReduction: Int,
    val risk: String,
    val dependencies: String,
)

/** Audit-only M1B-3V decisions. This object changes no command or runtime contract. */
object RemainingQueryReturnClassification {
    val decisions: List<RemainingQueryReturnDecision> = listOf(
        decision(
            id = "action.findTemplate",
            meaning = "Find the best threshold-qualified occurrence of a stored image template in the current visual evidence.",
            runtime = "RuntimeTemplateMatch? is converted to LiveExecutionOutcome text; null is shown as not found.",
            proposed = "ImageMatch?",
            absent = "The template search completed successfully but no candidate in the requested region reached the threshold before timeout.",
            negative = "No separate negative scalar exists; a score, including 0 when threshold permits it, is data inside ImageMatch.",
            failure = "Vision adapter unavailable, template resource missing or unreadable, capture unavailable, or comparison failed.",
            firstLoss = "compareScreenshotRegions returns nullable score, so missing evidence, comparison failure and no match collapse before RuntimeTemplateMatch; LiveExecutionOutcome then discards region and score.",
            transport = "WorkspaceScreen vision callback -> RuntimeTemplateMatch? -> WorkspaceBasicRuntime statement outcome text.",
            fit = QueryInfrastructureFit.NEW_STRUCTURED_VALUE,
            existingValue = "NullValue only",
            newValue = "ImageMatchValue(templateId, label, region, score)",
            diagnostics = listOf("VISION_ADAPTER_UNAVAILABLE", "TEMPLATE_RESOURCE_UNAVAILABLE", "VISION_CAPTURE_FAILED", "TEMPLATE_MATCH_FAILED"),
            legacy = "Keep stable ID action.findTemplate and import aliases findTemplate/FIND_TEMPLATE; canonical templateFind normalization must preserve all five arguments.",
            group = "M1B-3Y",
        ),
        decision(
            id = "vision.findText",
            meaning = "Return the best textual observation matching the requested text from one completed perception pass.",
            runtime = "No value-returning provider or runtime handler exists; the catalog currently produces only a statement trace.",
            proposed = "TextMatch?",
            absent = "OCR/perception completed successfully and produced no text observation matching the query.",
            negative = "No negative scalar; confidence 0 is a legitimate TextMatch field only when a provider emits such a match.",
            failure = "Vision adapter unavailable, screen capture failed, OCR engine failed, or provider response was malformed.",
            firstLoss = "There is no provider result contract or WorkspaceBasicRuntime environment function.",
            transport = "Catalog statement -> generic trace only; no producer-to-runtime value path.",
            fit = QueryInfrastructureFit.NEW_STRUCTURED_VALUE,
            existingValue = "NullValue only",
            newValue = "TextMatchValue(text, region, confidence, source)",
            diagnostics = listOf("VISION_ADAPTER_UNAVAILABLE", "VISION_CAPTURE_FAILED", "OCR_FAILED", "VISION_RESULT_INVALID"),
            legacy = "Keep stable ID vision.findText and aliases Region.findText/FIND_TEXT. Singular findText returns the best match; a future multi-match command must use a distinct ID.",
            group = "M1B-3Y",
        ),
        decision(
            id = "vision.markerLoad",
            meaning = "Load an immutable snapshot of a persisted marker resource by stable marker ID or legacy label.",
            runtime = "RuntimeAutomationRegion? is returned by the callback after mutating canvas selection; marker identity and metadata are discarded.",
            proposed = "Marker?",
            absent = "The marker repository was read successfully and contains no marker with the requested ID or label.",
            negative = "No negative scalar and no empty-marker sentinel.",
            failure = "Marker repository unavailable, persisted marker malformed, or geometry/metadata decoding failed.",
            firstLoss = "WorkspaceScreen markerLoad projects ScreenshotCanvasSavedMarker to region only and uses null for both missing and failed storage paths.",
            transport = "Persisted marker list -> ScreenshotCanvasSavedMarker -> RuntimeAutomationRegion? -> statement outcome text.",
            fit = QueryInfrastructureFit.NEW_STRUCTURED_VALUE,
            existingValue = "NullValue only",
            newValue = "MarkerValue(markerId, label, region, path, mode, assetId, threshold)",
            diagnostics = listOf("VISION_ADAPTER_UNAVAILABLE", "MARKER_REPOSITORY_UNAVAILABLE", "MARKER_DECODE_FAILED"),
            legacy = "Keep markerLoad/Marker.load/LOAD_MARKER. Lookup may accept a legacy label, but the returned value must retain the stable marker ID and must not be a live UI handle.",
            group = "M1B-3Y",
        ),
        decision(
            id = "tasker.isEnabled",
            meaning = "Report Tasker's own enabled preference, not complete WSS-to-Tasker execution readiness.",
            runtime = "TaskerRegistrationStatus.available combines installation, RUN_TASKS permission, enabled preference, external access and receiver availability.",
            proposed = "Bool",
            nullable = false,
            absent = "No ABSENT state.",
            negative = "false means Tasker is not installed or its own enabled preference is explicitly false.",
            failure = "Adapter unavailable, installation inspection failed, or the installed Tasker preference could not be read reliably.",
            firstLoss = "taskerPrefSet converts provider exceptions and missing rows to null; WorkspaceScreen then substitutes composite status.available.",
            transport = "Tasker content-provider/package inspection -> TaskerRegistrationStatus -> RuntimeAdapterResult success/message without typed value.",
            fit = QueryInfrastructureFit.EXISTING_SCALAR,
            existingValue = "BooleanValue",
            diagnostics = listOf("TASKER_ADAPTER_UNAVAILABLE", "TASKER_INSTALLATION_CHECK_FAILED", "TASKER_ENABLED_CHECK_FAILED"),
            legacy = "Normalize Tasker.isEnabled to tasker.isEnabled while retaining the former spelling as an import alias.",
            group = "M1B-3W",
        ),
        decision(
            id = "tasker.getVariable",
            meaning = "Read the current String value of one named Tasker variable.",
            runtime = "No Runner/Receiver result contract is connected and no value is produced.",
            proposed = "String?",
            absent = "The Tasker query succeeded but the named variable does not exist.",
            negative = "An empty String is a legitimate variable value and must not become ABSENT.",
            failure = "Tasker or adapter unavailable, permission/access denied, IPC failed, or response was malformed.",
            firstLoss = "The Tasker adapter has no value-returning receiver/session path for getVariable.",
            transport = "Catalog statement -> unconnected Tasker adapter warning; no return payload.",
            fit = QueryInfrastructureFit.EXISTING_SCALAR,
            existingValue = "StringValue and NullValue",
            diagnostics = listOf("TASKER_ADAPTER_UNAVAILABLE", "TASKER_NOT_INSTALLED", "TASKER_ACCESS_DENIED", "TASKER_VARIABLE_QUERY_FAILED", "TASKER_RESULT_INVALID"),
            legacy = "Normalize Tasker.getVariable to tasker.getVariable and retain the legacy alias and variable-reference argument spelling.",
            group = "M1B-3W",
        ),
        decision(
            id = "tasker.getVariables",
            meaning = "Return all Tasker variable name/value pairs matching the optional pattern.",
            runtime = "No collection result producer or receiver contract exists.",
            proposed = "List<TaskerVariable>",
            nullable = false,
            absent = "No ABSENT state; a successful query always returns a list.",
            negative = "An empty list means the query succeeded and no variables matched. Empty String remains a legitimate TaskerVariable value.",
            failure = "Tasker or adapter unavailable, permission/access denied, IPC failed, or collection response was malformed.",
            firstLoss = "The Tasker bridge has no collection-returning receiver/session path or runtime list value.",
            transport = "Catalog statement -> unconnected Tasker adapter warning; no collection payload.",
            fit = QueryInfrastructureFit.NEW_COLLECTION_AND_STRUCTURED_VALUE,
            existingValue = "ContractValue.ListValue exists only in language-core; runtime EmscriptValue has no list or TaskerVariable value.",
            newValue = "ListValue<TaskerVariableValue(name, value)>",
            diagnostics = listOf("TASKER_ADAPTER_UNAVAILABLE", "TASKER_NOT_INSTALLED", "TASKER_ACCESS_DENIED", "TASKER_VARIABLE_QUERY_FAILED", "TASKER_RESULT_INVALID"),
            legacy = "Normalize Tasker.getVariables to tasker.getVariables and retain the legacy spelling; result ordering must be deterministic by variable name.",
            group = "M1B-3X",
        ),
        decision(
            id = "shizuku.getUid",
            meaning = "Return the UID of the currently available Shizuku service.",
            runtime = "ShizukuRegistrationStatus.uid is rendered as -1 when unavailable; inspectAvailability also suppresses getUid exceptions into null.",
            proposed = "Number?",
            absent = "No Shizuku service UID is currently available because Shizuku is not installed, permission is not granted, or the binder is not alive.",
            negative = "0 is a legitimate UID and must remain NumberValue(0); negative sentinel values are forbidden.",
            failure = "Adapter unavailable, package/permission/binder inspection failed technically, getUid threw, or returned an invalid negative UID.",
            firstLoss = "inspectShizukuAvailability uses runCatching(uidProbe).getOrNull and WorkspaceScreen converts null to -1 text.",
            transport = "Shizuku inspection -> nullable Int -> RuntimeAdapterResult message text with -1 sentinel.",
            fit = QueryInfrastructureFit.EXISTING_SCALAR,
            existingValue = "NumberValue and NullValue",
            diagnostics = listOf("SHIZUKU_ADAPTER_UNAVAILABLE", "SHIZUKU_INSTALLATION_CHECK_FAILED", "SHIZUKU_PERMISSION_CHECK_FAILED", "SHIZUKU_BINDER_CHECK_FAILED", "SHIZUKU_UID_QUERY_FAILED"),
            legacy = "Normalize Shizuku.getUid to shizuku.getUid and retain the legacy spelling; remove only the -1 sentinel, not valid UID 0.",
            group = "M1B-3W",
        ),
        decision(
            id = "termux.get",
            meaning = "Read one supported Termux registration-status property as canonical text.",
            runtime = "Known keys produce String text; an unknown key becomes empty String and all inspection faults are flattened into status fields.",
            proposed = "String?",
            absent = "The requested key is not in the frozen key set: installed, apiInstalled, canRunCommands, permission, summary.",
            negative = "Supported Boolean/status keys return explicit text such as false or missing; these are values, not ABSENT. Empty String is not a missing-key sentinel in V1.",
            failure = "Adapter unavailable or package/permission/status inspection failed technically.",
            firstLoss = "TermuxRegistration.inspect uses Boolean fallbacks, then WorkspaceScreen maps unknown keys to empty String and stores the value only in message text.",
            transport = "Termux registration inspection -> key switch -> RuntimeAdapterResult message text.",
            fit = QueryInfrastructureFit.EXISTING_SCALAR,
            existingValue = "StringValue and NullValue",
            diagnostics = listOf("TERMUX_ADAPTER_UNAVAILABLE", "TERMUX_STATUS_CHECK_FAILED"),
            legacy = "Normalize Termux.get to termux.get and retain the legacy spelling. Empty key remains a legacy alias for summary; unknown nonempty keys become NullValue.",
            group = "M1B-3W",
        ),
        decision(
            id = "scrcpy.isRunning",
            meaning = "Report whether a scrcpy session for the optional serial is actively running.",
            runtime = "Vt2VtUsbAdbBridgeStatus.bridgeReady is substituted for scrcpy process/session state.",
            proposed = "Bool",
            nullable = false,
            absent = "No ABSENT state.",
            negative = "false means the session registry was inspected successfully and no matching scrcpy session is running, including when no host/device is available.",
            failure = "scrcpy adapter unavailable or session/process state could not be inspected reliably.",
            firstLoss = "WorkspaceScreen uses USB/ADB bridge readiness instead of a scrcpy session registry; Vt2VtUsbAdbBridge.detect also converts technical probe errors to false.",
            transport = "VT2VT USB/ADB status -> bridgeReady Boolean -> RuntimeAdapterResult success/message without typed value.",
            fit = QueryInfrastructureFit.EXISTING_SCALAR,
            existingValue = "BooleanValue",
            diagnostics = listOf("SCRCPY_ADAPTER_UNAVAILABLE", "SCRCPY_SESSION_CHECK_FAILED"),
            legacy = "Normalize Scrcpy.isRunning to scrcpy.isRunning and retain the legacy spelling. Optional serial selects one session without changing return type.",
            group = "M1B-3W",
        ),
        decision(
            id = "scrcpy.get",
            meaning = "Read one textual property of the active scrcpy session selected by the provider and key.",
            runtime = "The requested key is ignored and the VT2VT USB/ADB bridge summary is always embedded in message text.",
            proposed = "String?",
            absent = "A known session key has no value because no matching active session exists. Frozen keys are sessionId, serial, state, transport, host and port.",
            negative = "No negative scalar; textual state values such as stopped are legitimate values when supplied by an existing session snapshot.",
            failure = "Adapter unavailable, session inspection failed, response malformed, or a nonempty key is outside the frozen key set.",
            firstLoss = "WorkspaceScreen ignores key semantics and substitutes Vt2VtUsbAdbBridgeStatus.summary for a scrcpy session value.",
            transport = "VT2VT USB/ADB status -> summary String -> RuntimeAdapterResult message text.",
            fit = QueryInfrastructureFit.EXISTING_SCALAR,
            existingValue = "StringValue and NullValue",
            diagnostics = listOf("SCRCPY_ADAPTER_UNAVAILABLE", "SCRCPY_SESSION_CHECK_FAILED", "SCRCPY_UNKNOWN_KEY", "SCRCPY_RESULT_INVALID"),
            legacy = "Normalize Scrcpy.get to scrcpy.get and retain the legacy spelling; key names are case-insensitive on import and canonical lowerCamelCase on serialization.",
            group = "M1B-3W",
        ),
        decision(
            id = "chart.exists",
            meaning = "Test whether the chart repository contains a chart snapshot with the requested stable ID.",
            runtime = "No chart dispatch, repository lookup or result exists in WorkspaceBasicRuntime.",
            proposed = "Bool",
            nullable = false,
            absent = "No ABSENT state.",
            negative = "false means the chart repository was read successfully and contains no chart with that ID.",
            failure = "Chart adapter/repository unavailable or repository lookup failed.",
            firstLoss = "No provider-independent chart repository adapter is connected to WorkspaceBasicRuntime.",
            transport = "Catalog statement only; ChartGraph domain models are not connected to EMScript runtime.",
            fit = QueryInfrastructureFit.EXISTING_SCALAR,
            existingValue = "BooleanValue",
            diagnostics = listOf("CHART_ADAPTER_UNAVAILABLE", "CHART_REPOSITORY_QUERY_FAILED"),
            legacy = "Normalize Chart.exists to chart.exists and retain the legacy spelling; chart ID is a persistent resource ID, not a runtime handle.",
            group = "M1B-3Z",
        ),
        decision(
            id = "chart.get",
            meaning = "Load an immutable chart value snapshot by persistent chart ID.",
            runtime = "No chart dispatch or value exists; the optional legacy key has no repository-backed semantics.",
            proposed = "ChartSnapshot?",
            absent = "The chart repository was read successfully and contains no chart with the requested ID.",
            negative = "An existing chart with empty series/data is a legitimate ChartSnapshot value, not ABSENT.",
            failure = "Chart adapter/repository unavailable, persisted chart malformed, or snapshot decoding failed.",
            firstLoss = "No chart repository adapter or EMScript chart value exists; catalog metadata is the only current path.",
            transport = "Catalog statement only; ChartDocument exists in the plugin but is not transported into EMScript.",
            fit = QueryInfrastructureFit.NEW_STRUCTURED_VALUE,
            existingValue = "NullValue only; ChartDocument is a plugin domain model, not an EMScript runtime value.",
            newValue = "ChartSnapshotValue(id, title, kind, immutable data and options)",
            diagnostics = listOf("CHART_ADAPTER_UNAVAILABLE", "CHART_REPOSITORY_QUERY_FAILED", "CHART_DECODE_FAILED", "CHART_LEGACY_KEY_UNSUPPORTED"),
            legacy = "Normalize Chart.get to chart.get and retain the legacy spelling. Canonical V1 uses chart.get(id); the optional legacy key is import-only and must fail explicitly until mapped to a dedicated property command.",
            group = "M1B-3Z",
        ),
    )

    val groups: List<RemainingQueryMigrationGroup> = listOf(
        RemainingQueryMigrationGroup(
            id = "M1B-3W",
            name = "Provider scalar queries",
            commandIds = listOf("tasker.isEnabled", "tasker.getVariable", "shizuku.getUid", "termux.get", "scrcpy.isRunning", "scrcpy.get"),
            sharedContract = "Provider-backed Bool, Number? and String? values use Success(value/NullValue) while technical inability remains Failure(Diagnostic).",
            requiredInfrastructure = "Existing RuntimeAdapterResult, scalar EmscriptValue variants, NullValue and provider-local provenance; no new public value type.",
            diagnostics = "Provider-specific adapter, inspection, query and invalid-result diagnostics.",
            expectedReduction = 6,
            risk = "HIGH: each provider needs its own provenance even though transport is shared.",
            dependencies = "M1B-3R typed transport, M1B-3L nullable infrastructure, M1B-3T/U provider inspection patterns.",
        ),
        RemainingQueryMigrationGroup(
            id = "M1B-3X",
            name = "Tasker variable collection",
            commandIds = listOf("tasker.getVariables"),
            sharedContract = "A successful collection query always returns List<TaskerVariable>; no matches is an empty list, never NullValue.",
            requiredInfrastructure = "Runtime ListValue plus TaskerVariableValue and a value-returning Tasker receiver/session contract.",
            diagnostics = "TASKER_ADAPTER_UNAVAILABLE, TASKER_NOT_INSTALLED, TASKER_ACCESS_DENIED, TASKER_VARIABLE_QUERY_FAILED, TASKER_RESULT_INVALID.",
            expectedReduction = 1,
            risk = "HIGH: runtime list transport is missing even though language-core already models lists.",
            dependencies = "M1B-3W Tasker scalar/provider provenance should be reused.",
        ),
        RemainingQueryMigrationGroup(
            id = "M1B-3Y",
            name = "Perception and marker values",
            commandIds = listOf("action.findTemplate", "vision.findText", "vision.markerLoad"),
            sharedContract = "Successful lookup returns an immutable structured value or NullValue for a completed no-match/no-resource result; capture, engine and storage faults are failures.",
            requiredInfrastructure = "ImageMatchValue, TextMatchValue and MarkerValue plus provider result contracts; generic CommandCall and reporter remain sufficient.",
            diagnostics = "Vision adapter/capture/engine/result diagnostics and marker repository/decode diagnostics.",
            expectedReduction = 3,
            risk = "HIGH: existing callbacks collapse absent and failure and markerLoad currently discards identity.",
            dependencies = "M1B-3L nullable infrastructure and M1B-3P perception failure separation.",
        ),
        RemainingQueryMigrationGroup(
            id = "M1B-3Z",
            name = "Chart repository queries",
            commandIds = listOf("chart.exists", "chart.get"),
            sharedContract = "Named persistent chart resources expose Bool existence and an immutable ChartSnapshot? value; runtime UI handles never cross into EMScript.",
            requiredInfrastructure = "Provider-independent chart repository adapter and ChartSnapshotValue; existing Bool and NullValue transport for existence/absence.",
            diagnostics = "CHART_ADAPTER_UNAVAILABLE, CHART_REPOSITORY_QUERY_FAILED, CHART_DECODE_FAILED, CHART_LEGACY_KEY_UNSUPPORTED.",
            expectedReduction = 2,
            risk = "HIGH: no runtime dispatch exists and legacy chart.get key semantics are undefined.",
            dependencies = "ChartGraph ChartDocument can inform the immutable value schema but must not be exposed as a live handle.",
        ),
    )

    fun validate(audit: LegacyCommandBridgeAudit) {
        val repositoryIds = QueryReturnContractAudit.currentDQueryIds(audit)
        val migratedIds = QueryReturnContractAudit.MIGRATED_M1B_3W + QueryReturnContractAudit.MIGRATED_M1B_3X
        val historicalIds = repositoryIds + migratedIds
        require(decisions.map { it.commandId }.toSet() == historicalIds) {
            "M1B-3V historical decisions differ from current D_QUERY_RETURN plus completed migrations"
        }
        require(decisions.map { it.commandId }.distinct().size == decisions.size)
        require(groups.flatMap { it.commandIds }.toSet() == historicalIds)
        require(groups.sumOf { it.expectedReduction } == decisions.size)
        require(groups.flatMap { it.commandIds }.groupingBy { it }.eachCount().values.all { it == 1 })
        decisions.forEach { item ->
            val entry = requireNotNull(VisualTaskerCommandCatalog.findById(item.commandId))
            require(item.currentReturnDeclaration == "Void/unspecified (statement-shaped)")
            require(item.diagnostics.isNotEmpty())
            require(item.genericCommandCallSufficient && item.genericReporterSufficient)
            require(item.requiredNewValueType.isNotBlank())
            if (!item.nullable) require(!item.proposedV1ReturnType.endsWith("?"))
            if (item.commandId in migratedIds) {
                require(entry.kind == CommandCatalogKind.REPORTER)
                require(entry.returnType == item.proposedV1ReturnType)
            } else {
                require(entry.kind == CommandCatalogKind.STATEMENT)
                require(entry.returnType == null)
            }
        }
    }

    fun renderCsv(audit: LegacyCommandBridgeAudit): String {
        validate(audit)
        return buildString {
            appendLine(CSV_COLUMNS.joinToString(",", transform = ::csv))
            decisions.forEach { item ->
                appendLine(listOf(
                    item.commandId, item.meaning, item.currentReturnDeclaration, item.currentRuntimeValue,
                    item.proposedV1ReturnType, item.nullable.toString(), item.absentSemantics,
                    item.negativeResultSemantics, item.failureSemantics, item.firstLossPoint,
                    item.currentTransportPath, item.infrastructureFit.name, item.existingEmscriptValue,
                    item.requiredNewValueType, item.diagnostics.joinToString("|"),
                    item.genericCommandCallSufficient.toString(), item.genericReporterSufficient.toString(),
                    item.roundtripAndLegacyRisk, item.migrationGroup,
                ).joinToString(",", transform = ::csv))
            }
        }
    }

    fun renderMarkdown(audit: LegacyCommandBridgeAudit): String {
        validate(audit)
        return buildString {
            appendLine("# EMScript v1 M1B-3V Remaining Query Return Classification")
            appendLine()
            appendLine("Stand: 2026-10-01")
            appendLine("Status: historical 3V audit; scalar and Tasker collection queries migrated through M1B-3X")
            appendLine()
            appendLine("## Repository Truth")
            appendLine()
            appendLine("M1B-3V derived exactly ${decisions.size} D_QUERY_RETURN conflicts. Post-3X, seven provider queries are typed reporters and five conflicts remain. Command Catalog remains 127 and NATIVE_V1 remains 3.")
            appendLine()
            appendLine("## Decision Summary")
            appendLine()
            appendLine("| Command | Proposed V1 | Nullable | Infrastructure | Group |")
            appendLine("| --- | --- | --- | --- | --- |")
            decisions.forEach { item ->
                appendLine("| `${item.commandId}` | `${item.proposedV1ReturnType}` | ${yesNo(item.nullable)} | ${item.infrastructureFit} | ${item.migrationGroup} |")
            }
            appendLine()
            appendLine("## Per-command Classification")
            decisions.forEach { item ->
                appendLine()
                appendLine("### `${item.commandId}`")
                appendLine()
                appendLine("- Meaning: ${item.meaning}")
                appendLine("- Current declaration: `${item.currentReturnDeclaration}`")
                appendLine("- Current runtime value: ${item.currentRuntimeValue}")
                appendLine("- Proposed V1 return: `${item.proposedV1ReturnType}`; nullable: ${yesNo(item.nullable)}")
                appendLine("- ABSENT: ${item.absentSemantics}")
                appendLine("- Negative/empty/zero: ${item.negativeResultSemantics}")
                appendLine("- Failure: ${item.failureSemantics}")
                appendLine("- First loss point: ${item.firstLossPoint}")
                appendLine("- Current transport: ${item.currentTransportPath}")
                appendLine("- Infrastructure: ${item.infrastructureFit}; existing `${item.existingEmscriptValue}`; required `${item.requiredNewValueType}`")
                appendLine("- Diagnostics: ${item.diagnostics.joinToString(", ") { "`$it`" }}")
                appendLine("- IR/projection: generic `IrExpression.CommandCall` and generic reporter are sufficient.")
                appendLine("- Roundtrip/legacy: ${item.roundtripAndLegacyRisk}")
                appendLine("- Migration: ${item.migrationGroup}")
            }
            appendLine()
            appendLine("## Migration Groups")
            groups.forEach { group ->
                appendLine()
                appendLine("### ${group.id} - ${group.name}")
                appendLine()
                appendLine("- Commands: ${group.commandIds.joinToString { "`$it`" }}")
                appendLine("- Contract: ${group.sharedContract}")
                appendLine("- Infrastructure: ${group.requiredInfrastructure}")
                appendLine("- Diagnostics: ${group.diagnostics}")
                appendLine("- Expected D_QUERY_RETURN reduction: ${group.expectedReduction}")
                appendLine("- Risk: ${group.risk}")
                appendLine("- Dependencies: ${group.dependencies}")
            }
            appendLine()
            appendLine("## Recommended Order")
            appendLine()
            appendLine("1. M1B-3W reuses only existing scalar values and establishes provider-local provenance.")
            appendLine("2. M1B-3X adds the missing runtime collection/value pair on top of the Tasker provider path.")
            appendLine("3. M1B-3Y adds immutable perception and marker values with strict no-match versus failure handling.")
            appendLine("4. M1B-3Z connects the chart repository and removes the final two conflicts without exposing runtime handles.")
            appendLine()
            appendLine("Expected reductions are 6 + 1 + 3 + 2 = 12; successful completion leaves D_QUERY_RETURN at 0.")
            appendLine()
            appendLine("## Infrastructure Gap Summary")
            appendLine()
            appendLine("RuntimeAdapterResult, scalar EmscriptValue variants, NullValue, ListValue, TaskerVariableValue, nullable/list type compatibility, generic CommandCall and generic reporter projection are sufficient through M1B-3X. The remaining minimal additions are ImageMatchValue, TextMatchValue, MarkerValue and ChartSnapshotValue plus provider result contracts. No special expression class is required.")
        }
    }

    private fun decision(
        id: String,
        meaning: String,
        runtime: String,
        proposed: String,
        nullable: Boolean = true,
        absent: String,
        negative: String,
        failure: String,
        firstLoss: String,
        transport: String,
        fit: QueryInfrastructureFit,
        existingValue: String,
        newValue: String = "none",
        diagnostics: List<String>,
        legacy: String,
        group: String,
    ) = RemainingQueryReturnDecision(
        commandId = id,
        meaning = meaning,
        currentReturnDeclaration = "Void/unspecified (statement-shaped)",
        currentRuntimeValue = runtime,
        proposedV1ReturnType = proposed,
        nullable = nullable,
        absentSemantics = absent,
        negativeResultSemantics = negative,
        failureSemantics = failure,
        firstLossPoint = firstLoss,
        currentTransportPath = transport,
        infrastructureFit = fit,
        existingEmscriptValue = existingValue,
        requiredNewValueType = newValue,
        diagnostics = diagnostics,
        roundtripAndLegacyRisk = legacy,
        migrationGroup = group,
    )

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

    private val CSV_COLUMNS = listOf(
        "command_id", "meaning", "current_return_declaration", "current_runtime_value",
        "proposed_v1_return_type", "nullable", "absent_semantics", "negative_result_semantics",
        "failure_semantics", "first_loss_point", "current_transport_path", "infrastructure_fit",
        "existing_emscript_value", "required_new_value_type", "diagnostics",
        "generic_command_call_sufficient", "generic_reporter_sufficient", "roundtrip_legacy_risk",
        "migration_group",
    )
}
