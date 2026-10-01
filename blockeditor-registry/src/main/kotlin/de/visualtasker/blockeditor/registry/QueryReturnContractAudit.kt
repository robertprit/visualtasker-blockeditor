package de.visualtasker.blockeditor.registry

enum class QueryReturnClass {
    A_NULLABLE_SCALAR_READY,
    B_NONNULL_SCALAR_READY,
    C_STRUCTURED_RESULT_TYPE,
    D_RUNTIME_RESULT_GAP,
    E_PROVIDER_CONTRACT_GAP,
    F_SENTINEL_OR_ERROR_COLLISION,
    G_LANGUAGE_SEMANTICS_GAP,
    H_AMBIGUOUS,
}

enum class QueryReturnFlag {
    NULLABLE,
    PROVIDER_BACKED,
    STRUCTURED,
    SENTINEL,
    FAILURE_COLLISION,
    EXPRESSION_READY,
    RUNTIME_VALUE_EXISTS,
    NEEDS_NEW_TYPE,
    NEEDS_LANGUAGE_FEATURE,
    LEGACY_ALIAS_ONLY,
    BLOCKED,
}

enum class QueryNullability { YES, NO, UNKNOWN }
enum class QueryMigrationRisk { LOW, MEDIUM, HIGH, BLOCKED }
enum class QueryEffectClass { PURE, READ_ONLY_EXTERNAL, SIDE_EFFECTING_QUERY, UNKNOWN }

data class QueryReturnDecision(
    val stableId: String,
    val canonicalName: String,
    val currentReturnType: String = "Void/unspecified",
    val observedRuntimeType: String,
    val proposedV1ReturnType: String,
    val nullable: QueryNullability,
    val primaryClass: QueryReturnClass,
    val secondaryFlags: Set<QueryReturnFlag>,
    val commandCallReady: Boolean = false,
    val workspaceReady: Boolean = false,
    val irReady: Boolean = false,
    val runtimeHandlerExists: Boolean,
    val runtimeValueExists: Boolean,
    val absentSemanticsKnown: Boolean,
    val failureSemanticsKnown: Boolean,
    val sentinelUsed: Boolean,
    val provider: String,
    val structuredTypeNeeded: Boolean,
    val proposedStructuredType: String = "",
    val transportReady: Boolean,
    val consumptionReady: Boolean,
    val firstLossPoint: String,
    val sideEffectClass: QueryEffectClass = QueryEffectClass.READ_ONLY_EXTERNAL,
    val migrationRisk: QueryMigrationRisk,
    val recommendedNextSlice: String,
    val existingInternalType: String = "",
    val repositoryFields: String = "",
    val plannedOnlyFields: String = "none",
    val producer: String,
    val consumer: String,
    val evidence: String,
)

/** Remaining D_QUERY_RETURN inventory after the approved query convergence slices. */
object QueryReturnContractAudit {
    val MIGRATED_M1B_3J = linkedSetOf("clipboard.get", "system.info", "system.env")
    val MIGRATED_M1B_3M = linkedSetOf("file.readText", "system.datastoreGet")
    val MIGRATED_M1B_3P = linkedSetOf("vision.templateCompare")
    val MIGRATED_M1B_3R = linkedSetOf("chromeTab.isSupported")
    val MIGRATED_M1B_3T = linkedSetOf("tasker.isInstalled", "shizuku.isInstalled", "termux.isInstalled")
    val MIGRATED_M1B_3U = linkedSetOf("shizuku.isAvailable")

    val ALL: List<QueryReturnDecision> = listOf(
        decision(
            "action.findTemplate", "findTemplate", "RuntimeTemplateMatch?", "ImageMatch?",
            QueryNullability.YES, QueryReturnClass.C_STRUCTURED_RESULT_TYPE,
            flags(QueryReturnFlag.NULLABLE, QueryReturnFlag.STRUCTURED, QueryReturnFlag.RUNTIME_VALUE_EXISTS, QueryReturnFlag.NEEDS_NEW_TYPE, QueryReturnFlag.FAILURE_COLLISION),
            true, true, true, false, "workspace vision adapter", false, false,
            "WorkspaceBasicRuntime converts RuntimeTemplateMatch? to LiveExecutionOutcome text.",
            QueryMigrationRisk.HIGH,
            "Define ImageMatch and split not-found from vision/runtime failure before expression migration.",
            "WorkspaceBasicRuntimeEnvironment.findTemplate / WorkspaceScreen vision adapter",
            "WorkspaceBasicRuntime LiveExecutionOutcome",
            "A threshold-qualified match exists; null covers no qualifying match and unavailable comparison evidence, while score and region are discarded into display text.",
            structured = true, structuredType = "ImageMatch", internalType = "RuntimeTemplateMatch",
            fields = "name:String; region:RuntimeAutomationRegion; score:Float",
        ),
        decision(
            "vision.findText", "findText", "no runtime query result", "UNRESOLVED",
            QueryNullability.UNKNOWN, QueryReturnClass.D_RUNTIME_RESULT_GAP,
            flags(QueryReturnFlag.PROVIDER_BACKED, QueryReturnFlag.BLOCKED),
            false, false, false, false, "visualtasker.vision", false, false,
            "No WorkspaceBasicRuntime environment function or value-returning vision provider dispatch exists.",
            QueryMigrationRisk.BLOCKED,
            "Specify cardinality and provider result before selecting scalar, list or structured return type.",
            "none", "statement-only catalog/runtime trace",
            "Catalog parameters establish text and timeout only; no authoritative OCR result, match collection or not-found/failure contract exists.",
        ),
        decision(
            "vision.markerLoad", "markerLoad", "RuntimeAutomationRegion?", "Region?",
            QueryNullability.YES, QueryReturnClass.C_STRUCTURED_RESULT_TYPE,
            flags(QueryReturnFlag.NULLABLE, QueryReturnFlag.STRUCTURED, QueryReturnFlag.RUNTIME_VALUE_EXISTS, QueryReturnFlag.NEEDS_NEW_TYPE, QueryReturnFlag.FAILURE_COLLISION),
            true, true, true, false, "visualtasker.vision", false, false,
            "WorkspaceBasicRuntime converts RuntimeAutomationRegion? to text; EmscriptValue has no RegionValue.",
            QueryMigrationRisk.HIGH,
            "Define Region as a first-class language/runtime value and split lookup absence from load failure.",
            "WorkspaceScreen saved-marker lookup", "WorkspaceBasicRuntime LiveExecutionOutcome",
            "Saved markers yield exact bounds or null; Region exists as parameter syntax but not as a TypeDefinition-backed runtime result.",
            structured = true, structuredType = "Region", internalType = "RuntimeAutomationRegion",
            fields = "x:Int; y:Int; width:Int; height:Int",
        ),
        decision(
            "tasker.isEnabled", "Tasker.isEnabled", "TaskerRegistrationStatus.available Boolean", "Bool",
            QueryNullability.NO, QueryReturnClass.F_SENTINEL_OR_ERROR_COLLISION,
            flags(QueryReturnFlag.PROVIDER_BACKED, QueryReturnFlag.RUNTIME_VALUE_EXISTS, QueryReturnFlag.FAILURE_COLLISION),
            true, true, true, false, "visualtasker.tasker", true, true,
            "WorkspaceScreen maps isEnabled to composite status.available and then to RuntimeAdapterResult.",
            QueryMigrationRisk.HIGH,
            "Define whether isEnabled means the enabled preference or full provider availability.",
            "TaskerRegistration.inspect", "WorkspaceScreen taskerCommand adapter",
            "status.available combines installation, permission, enabled preference, external access and receiver availability; false is not one stable fact.",
        ),
        runtimeGap("tasker.getVariable", "Tasker.getVariable", "visualtasker.tasker", "Runner/Receiver contract is explicitly not connected; no variable value is returned.", "Connect a typed variable result and distinguish missing variable from provider failure."),
        runtimeGap("tasker.getVariables", "Tasker.getVariables", "visualtasker.tasker", "No collection result exists; command name and pattern do not prove List, Map or opaque payload semantics.", "Connect the result contract and establish collection cardinality."),
        decision(
            "shizuku.getUid", "Shizuku.getUid", "Int? rendered as -1", "Number?",
            QueryNullability.YES, QueryReturnClass.F_SENTINEL_OR_ERROR_COLLISION,
            flags(QueryReturnFlag.NULLABLE, QueryReturnFlag.PROVIDER_BACKED, QueryReturnFlag.RUNTIME_VALUE_EXISTS, QueryReturnFlag.SENTINEL, QueryReturnFlag.FAILURE_COLLISION),
            true, true, false, false, "visualtasker.shizuku", true, false,
            "inspect collapses unavailable/permission/binder/getUid failure to uid=null; adapter renders null as -1.",
            QueryMigrationRisk.HIGH,
            "Replace -1 with a typed UID/absence/failure provider result before Number? migration.",
            "ShizukuRegistration.inspect / Shizuku.getUid", "WorkspaceScreen shizukuCommand adapter",
            "uid is read only with live binder and permission; exceptions become null, so not installed, unavailable binder, missing permission and call failure are indistinguishable.",
            sentinel = true,
        ),
        decision(
            "termux.get", "Termux.get", "String selected by key; unknown key becomes empty String", "String?",
            QueryNullability.YES, QueryReturnClass.F_SENTINEL_OR_ERROR_COLLISION,
            flags(QueryReturnFlag.NULLABLE, QueryReturnFlag.PROVIDER_BACKED, QueryReturnFlag.RUNTIME_VALUE_EXISTS, QueryReturnFlag.SENTINEL, QueryReturnFlag.FAILURE_COLLISION),
            true, true, false, false, "visualtasker.termux", true, false,
            "WorkspaceScreen maps unknown keys to empty String and embeds values in RuntimeAdapterResult.message.",
            QueryMigrationRisk.HIGH,
            "Define supported keys and separate empty value, unknown key, unavailable provider and failure.",
            "TermuxRegistration.inspect / WorkspaceScreen key selector", "WorkspaceScreen termuxCommand adapter",
            "Known keys produce Boolean/status strings; unknown key uses an empty-string sentinel and no typed payload exists.",
            sentinel = true,
        ),
        decision(
            "scrcpy.isRunning", "Scrcpy.isRunning", "Vt2VtUsbAdbBridgeStatus.bridgeReady Boolean", "Bool",
            QueryNullability.NO, QueryReturnClass.F_SENTINEL_OR_ERROR_COLLISION,
            flags(QueryReturnFlag.PROVIDER_BACKED, QueryReturnFlag.RUNTIME_VALUE_EXISTS, QueryReturnFlag.FAILURE_COLLISION),
            true, true, true, false, "visualtasker.scrcpy", true, true,
            "WorkspaceScreen substitutes bridgeReady for process/session running state.",
            QueryMigrationRisk.HIGH,
            "Define actual scrcpy session-running state independently from USB/ADB readiness.",
            "Vt2VtUsbAdbBridge.detect", "WorkspaceScreen scrcpyCommand adapter",
            "bridgeReady means USB connected and ADB enabled; it does not establish a running scrcpy process or session.",
        ),
        decision(
            "scrcpy.get", "Scrcpy.get", "status summary String independent of requested key", "UNRESOLVED",
            QueryNullability.UNKNOWN, QueryReturnClass.F_SENTINEL_OR_ERROR_COLLISION,
            flags(QueryReturnFlag.PROVIDER_BACKED, QueryReturnFlag.RUNTIME_VALUE_EXISTS, QueryReturnFlag.FAILURE_COLLISION, QueryReturnFlag.BLOCKED),
            true, true, false, false, "visualtasker.scrcpy", false, false,
            "WorkspaceScreen ignores key semantics and always returns bridge status summary text.",
            QueryMigrationRisk.BLOCKED,
            "Define supported keys and typed status/session results.",
            "Vt2VtUsbAdbBridge.detect", "WorkspaceScreen scrcpyCommand adapter",
            "A summary exists, but key is ignored and no absence/failure contract exists.",
        ),
        runtimeGap("chart.exists", "Chart.exists", "visualtasker.charts", "Catalog/plugin metadata exists, but WorkspaceBasicRuntime has no chart dispatch or existence result.", "Add a provider-independent chart lookup result contract before Bool semantics."),
        runtimeGap("chart.get", "Chart.get", "visualtasker.charts", "No value-returning chart path exists; id and optional key do not establish scalar, collection or structured semantics.", "Define query keys and result model before selecting a return type."),
    )

    fun currentDQueryIds(audit: LegacyCommandBridgeAudit): Set<String> = audit.results
        .filter { result -> result.issues.any { it.type == CommandBridgeIssueType.QUERY_RETURNS_VOID || it.type == CommandBridgeIssueType.MISSING_RETURN_TYPE } }
        .mapTo(linkedSetOf()) { it.sourceEntry.id }

    fun validateAgainst(audit: LegacyCommandBridgeAudit) {
        val currentIds = currentDQueryIds(audit)
        require(ALL.map { it.stableId }.distinct().size == ALL.size) { "Duplicate query decision stableId" }
        require(currentIds == ALL.mapTo(linkedSetOf()) { it.stableId }) { "Decision matrix differs from D_QUERY_RETURN inventory" }
        require(MIGRATED_M1B_3J.none { it in currentIds }) { "M1B-3J query returned to D_QUERY_RETURN" }
        require(MIGRATED_M1B_3M.none { it in currentIds }) { "M1B-3M query returned to D_QUERY_RETURN" }
        require(MIGRATED_M1B_3P.none { it in currentIds }) { "M1B-3P query returned to D_QUERY_RETURN" }
        require(MIGRATED_M1B_3R.none { it in currentIds }) { "M1B-3R query returned to D_QUERY_RETURN" }
        require(MIGRATED_M1B_3T.none { it in currentIds }) { "M1B-3T query returned to D_QUERY_RETURN" }
        require(MIGRATED_M1B_3U.none { it in currentIds }) { "M1B-3U query returned to D_QUERY_RETURN" }
        require(
            ALL.size + MIGRATED_M1B_3J.size + MIGRATED_M1B_3M.size +
                MIGRATED_M1B_3P.size + MIGRATED_M1B_3R.size + MIGRATED_M1B_3T.size +
                MIGRATED_M1B_3U.size == 23,
        ) { "Query inventory accounting drift" }
        val catalogById = audit.results.associateBy { it.sourceEntry.id }
        ALL.forEach { decision ->
            val entry = requireNotNull(catalogById[decision.stableId]).sourceEntry
            require(entry.canonicalName == decision.canonicalName) { "Canonical name drift for ${decision.stableId}" }
            require(entry.role == CommandCatalogRole.LANGUAGE_COMMAND)
            require((QueryReturnFlag.RUNTIME_VALUE_EXISTS in decision.secondaryFlags) == decision.runtimeValueExists)
            require((QueryReturnFlag.SENTINEL in decision.secondaryFlags) == decision.sentinelUsed)
            require((QueryReturnFlag.STRUCTURED in decision.secondaryFlags) == decision.structuredTypeNeeded)
            require(!decision.commandCallReady && !decision.workspaceReady && !decision.irReady) { "Audit must not migrate projections" }
            if (decision.nullable == QueryNullability.YES) require(QueryReturnFlag.NULLABLE in decision.secondaryFlags)
            if (decision.structuredTypeNeeded) {
                require(decision.proposedStructuredType.isNotBlank())
                require(decision.existingInternalType.isNotBlank())
                require(decision.repositoryFields.isNotBlank())
            }
        }
    }

    fun renderCsv(audit: LegacyCommandBridgeAudit): String {
        validateAgainst(audit)
        val entries = audit.results.associateBy { it.sourceEntry.id }
        return buildString {
            appendLine(CSV_COLUMNS.joinToString(",", transform = ::csv))
            ALL.forEach { item ->
                val result = entries.getValue(item.stableId)
                val entry = result.sourceEntry
                appendLine(listOf(
                    item.stableId, item.canonicalName, entry.acceptedAliases.joinToString("|"),
                    entry.arguments.joinToString("|") { "${it.name}:${it.type}${if (it.required) "" else "?"}" },
                    item.currentReturnType, item.proposedV1ReturnType, item.nullable.name, item.primaryClass.name,
                    item.secondaryFlags.joinToString("|") { it.name }, item.commandCallReady.toString(),
                    item.workspaceReady.toString(), item.irReady.toString(), item.runtimeHandlerExists.toString(),
                    item.runtimeValueExists.toString(), item.absentSemanticsKnown.toString(), item.failureSemanticsKnown.toString(),
                    item.sentinelUsed.toString(), item.provider, item.structuredTypeNeeded.toString(), item.proposedStructuredType,
                    item.transportReady.toString(), item.consumptionReady.toString(), item.firstLossPoint,
                    item.migrationRisk.name, item.recommendedNextSlice, item.existingInternalType, item.repositoryFields,
                    item.plannedOnlyFields, item.producer, item.consumer,
                    "${result.status}/${result.migrationClass}", testEvidence(item.stableId), item.evidence,
                ).joinToString(",", transform = ::csv))
            }
        }
    }

    fun renderMarkdown(audit: LegacyCommandBridgeAudit): String {
        validateAgainst(audit)
        val entries = audit.results.associateBy { it.sourceEntry.id }
        return buildString {
            appendLine("# EMScript v1 Remaining Query Return Reclassification")
            appendLine()
            appendLine("Stand: 2026-10-01  ")
            appendLine("Phase: M1B-3U Shizuku availability convergence  ")
            appendLine("Status: shizuku.isAvailable converged as a typed Bool reporter")
            appendLine()
            appendLine("## Invariants")
            appendLine()
            appendLine("The bridge derives exactly ${ALL.size} D_QUERY_RETURN entries. Each has one primary class. The eleven approved query migrations through M1B-3U remain outside this inventory.")
            appendLine()
            appendLine("## Classification Summary")
            appendLine()
            appendLine("| Class | Count |")
            appendLine("| --- | ---: |")
            QueryReturnClass.entries.forEach { type -> appendLine("| $type | ${ALL.count { it.primaryClass == type }} |") }
            appendLine()
            appendLine("## Decision Matrix")
            appendLine()
            appendLine("| Stable ID | Proposed | Primary class | Flags | Runtime value | Transport | Consumption | First loss | Risk |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
            ALL.forEach { item -> appendLine("| `${item.stableId}` | `${md(item.proposedV1ReturnType)}` | ${item.primaryClass} | ${md(item.secondaryFlags.joinToString())} | ${yesNo(item.runtimeValueExists)} | ${yesNo(item.transportReady)} | ${yesNo(item.consumptionReady)} | ${md(item.firstLossPoint)} | ${item.migrationRisk} |") }
            appendLine()
            appendLine("## Catalog And Provider Evidence")
            appendLine()
            appendLine("| Stable ID | Aliases | Parameters | Provider | Bridge | Tests | Producer / first consumer | Evidence |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- | --- |")
            ALL.forEach { item ->
                val result = entries.getValue(item.stableId)
                val entry = result.sourceEntry
                val aliases = entry.acceptedAliases.joinToString().ifBlank { "none" }
                val parameters = entry.arguments.joinToString { "${it.name}:${it.type}${if (it.required) "" else "?"}" }.ifBlank { "none" }
                appendLine("| `${item.stableId}` | ${md(aliases)} | ${md(parameters)} | `${item.provider}` | ${result.status}/${result.migrationClass} | ${md(testEvidence(item.stableId))} | ${md(item.producer)} / ${md(item.consumer)} | ${md(item.evidence)} |")
            }
            appendLine()
            appendLine("## Structured Result Evidence")
            appendLine()
            appendLine("| Stable ID | Proposed type | Internal type | Repository fields | Planned-only fields | Nullable |")
            appendLine("| --- | --- | --- | --- | --- | --- |")
            ALL.filter { it.structuredTypeNeeded }.forEach { item -> appendLine("| `${item.stableId}` | `${item.proposedStructuredType}` | `${item.existingInternalType}` | ${md(item.repositoryFields)} | ${md(item.plannedOnlyFields)} | ${item.nullable} |") }
            appendLine()
            appendLine("## Nullable Language Impact")
            appendLine()
            appendLine("Scalar nullable transport exists, but source-level inspection or resolution does not. Every nullable candidate here has consumptionReady=false. Repository evidence creates needs for presence testing, conditional branching on presence, extraction of a present value and explicit fallback; this audit chooses no syntax.")
            appendLine()
            appendLine("## Recommended Next Slice")
            appendLine()
            appendLine("M1B-3U converges shizuku.isAvailable through provider-local installation, permission and Binder provenance plus the generic RuntimeAdapterResult value payload. The next slice must be selected from the remaining twelve contracts rather than inferred here.")
        }
    }

    private fun providerBoolean(id: String, name: String, provider: String, evidence: String) = decision(
        id, name, "Boolean computed by provider registration", "Bool", QueryNullability.NO,
        QueryReturnClass.E_PROVIDER_CONTRACT_GAP,
        flags(QueryReturnFlag.PROVIDER_BACKED, QueryReturnFlag.RUNTIME_VALUE_EXISTS),
        true, true, true, false, provider, true, true,
        "WorkspaceScreen flattens the Boolean into RuntimeAdapterResult success/message without typed payload.",
        QueryMigrationRisk.MEDIUM, "Introduce a provider-independent typed Bool result with a separate diagnostic channel.",
        "$provider registration inspection", "WorkspaceScreen provider adapter / WorkspaceBasicRuntime", evidence,
    )

    private fun runtimeGap(id: String, name: String, provider: String, evidence: String, next: String) = decision(
        id, name, "no authoritative runtime result", "UNRESOLVED", QueryNullability.UNKNOWN,
        QueryReturnClass.D_RUNTIME_RESULT_GAP, flags(QueryReturnFlag.PROVIDER_BACKED, QueryReturnFlag.BLOCKED),
        false, false, false, false, provider, false, false,
        "No value-returning runtime/provider contract exists for this command.", QueryMigrationRisk.BLOCKED, next,
        "none", "statement-only adapter/trace", evidence,
    )

    private fun decision(
        id: String, name: String, observed: String, proposed: String, nullable: QueryNullability,
        primary: QueryReturnClass, flags: Set<QueryReturnFlag>, runtimeHandler: Boolean, runtimeValue: Boolean,
        absentKnown: Boolean, failureKnown: Boolean, provider: String, transportReady: Boolean,
        consumptionReady: Boolean, firstLoss: String, risk: QueryMigrationRisk, next: String,
        producer: String, consumer: String, evidence: String, sentinel: Boolean = false,
        structured: Boolean = false, structuredType: String = "", internalType: String = "", fields: String = "",
    ) = QueryReturnDecision(
        stableId = id, canonicalName = name, observedRuntimeType = observed, proposedV1ReturnType = proposed,
        nullable = nullable, primaryClass = primary, secondaryFlags = flags,
        runtimeHandlerExists = runtimeHandler, runtimeValueExists = runtimeValue,
        absentSemanticsKnown = absentKnown, failureSemanticsKnown = failureKnown, sentinelUsed = sentinel,
        provider = provider, structuredTypeNeeded = structured, proposedStructuredType = structuredType,
        transportReady = transportReady, consumptionReady = consumptionReady, firstLossPoint = firstLoss,
        migrationRisk = risk, recommendedNextSlice = next, existingInternalType = internalType,
        repositoryFields = fields, producer = producer, consumer = consumer, evidence = evidence,
    )

    private fun flags(vararg values: QueryReturnFlag): Set<QueryReturnFlag> = linkedSetOf(*values)
    private fun testEvidence(id: String): String = when (id) {
        "action.findTemplate", "vision.markerLoad" ->
            "WorkspaceBasicRuntimeTest; WorkspaceDryRunRuntimeTest; EmscriptParserSliceTest"
        "vision.findText" -> "EmscriptParserSliceTest; RuntimeCapabilityGateTest"
        "shizuku.isInstalled", "shizuku.isAvailable", "shizuku.getUid" ->
            "WorkspaceBasicRuntimeTest; ShizukuRegistrationStatusTest; PluginRuntimeReadinessTest"
        else -> "WorkspaceBasicRuntimeTest provider dispatch; QueryReturnContractAuditTest"
    }
    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun md(value: String): String = value.replace("|", "\\|").replace("\n", "<br>")
    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

    private val CSV_COLUMNS = listOf(
        "stableId", "canonicalName", "aliases", "parameters", "currentReturnType", "proposedReturnType",
        "nullable", "primaryClass", "secondaryFlags", "commandCallReady", "workspaceReady", "irReady",
        "runtimeHandlerExists", "runtimeValueExists", "absentSemanticsKnown", "failureSemanticsKnown",
        "sentinelUsed", "provider", "structuredTypeNeeded", "proposedStructuredType", "transportReady",
        "consumptionReady", "firstLossPoint", "migrationRisk", "recommendedSlice", "existingInternalType",
        "repositoryFields", "plannedOnlyFields", "producer", "consumer", "bridgeStatus", "testEvidence", "evidence",
    )
}
