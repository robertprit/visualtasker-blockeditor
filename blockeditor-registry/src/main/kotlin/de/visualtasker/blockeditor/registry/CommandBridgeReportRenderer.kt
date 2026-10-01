package de.visualtasker.blockeditor.registry

import de.visualtasker.emscript.contract.LanguageTypeRef

object CommandBridgeReportRenderer {
    fun renderCsv(audit: LegacyCommandBridgeAudit): String = buildString {
        appendLine(
            listOf(
                "legacy_id",
                "legacy_name",
                "catalog_role",
                "legacy_spellings",
                "naming_status",
                "type_status",
                "expression_status",
                "type_classification",
                "proposed_v1_id",
                "proposed_canonical_name",
                "semantic_class",
                "domain",
                "family",
                "variant",
                "current_kind",
                "current_return",
                "proposed_return",
                "capability",
                "provider",
                "live_status",
                "bridge_status",
                "issues",
                "migration_class",
                "notes",
            ).joinToString(",", transform = ::csv)
        )
        audit.results.forEach { result ->
            val entry = result.sourceEntry
            val definition = result.definition
            appendLine(
                listOf(
                    entry.id,
                    entry.canonicalName,
                    entry.role.name,
                    EmscriptV1NamingNormalizations.byStableId(entry.id)
                        ?.legacySpellings
                        ?.joinToString("|") { "${it.name} [read ${it.readableThrough}; remove >= ${it.earliestRemoval}]" }
                        ?: "none",
                    result.namingStatus().name,
                    result.typeStatus().name,
                    result.expressionStatus().name,
                    EmscriptV1TypeConflictDecisions.byStableId(entry.id)?.classification?.name ?: "none",
                    definition?.id?.value ?: "unmappable",
                    definition?.canonicalName ?: "unmappable",
                    definition?.semanticClass?.name ?: "UNKNOWN",
                    definition?.domain?.value ?: entry.category.ifBlank { "unknown" },
                    definition?.family?.value ?: "none",
                    definition?.variant?.value ?: "none",
                    entry.kind.name,
                    entry.returnType ?: "Void/unspecified",
                    definition?.returnType?.let(::renderType) ?: "unknown",
                    entry.capabilities.joinToString("|") { it.name }.ifBlank { "none" },
                    definition?.provider?.value ?: "none",
                    result.dispatchEvidence.status.name,
                    result.status.name,
                    result.issues.joinToString("|") { it.type.name }.ifBlank { "none" },
                    result.migrationClass.name,
                    buildList {
                        addAll(result.issues.map { "${it.message} Evidence: ${it.evidence}" })
                        if (entry.id == "input.touch") add(touchAnalysisSummary())
                        if (isEmpty()) add(result.dispatchEvidence.evidence)
                    }.joinToString(" | "),
                ).joinToString(",", transform = ::csv)
            )
        }
    }

    fun renderMarkdown(audit: LegacyCommandBridgeAudit): String = buildString {
        val entries = audit.results.map { it.sourceEntry }
        val uniqueCanonicalNames = entries.map { it.canonicalName.lowercase() }.distinct().size
        val aliases = entries.sumOf { it.acceptedAliases.size }
        appendLine("# EMScript v1 Command Bridge Report")
        appendLine()
        appendLine("Stand: 2026-10-01  ")
        appendLine("Phase: M1B-3U Shizuku Availability Convergence  ")
        appendLine("Status: eleven queries migrated; shizuku.isAvailable converged; 12 D_QUERY_RETURN entries remain")
        appendLine()
        appendLine("## Inventory")
        appendLine()
        appendLine("| Metric | Count |")
        appendLine("| --- | ---: |")
        appendLine("| Catalog entries | ${entries.size} |")
        appendLine("| Unique canonical names, case-insensitive | $uniqueCanonicalNames |")
        appendLine("| Declared aliases | $aliases |")
        appendLine("| Alias/canonical collisions | ${audit.aliasCollisions.size} |")
        appendLine("| Provider-owned entries | ${entries.count { it.pluginOwner != "visualtasker.core" }} |")
        appendLine("| Query projections | ${audit.queryAudit.size} |")
        appendLine("| Native V1 definitions | ${audit.results.count { it.migrationClass == CommandMigrationClass.NATIVE_V1 }} |")
        CommandBridgeStatus.entries.forEach { status ->
            appendLine("| $status | ${audit.countsByStatus.getValue(status)} |")
        }
        CommandLiveStatus.entries.forEach { status ->
            appendLine("| Live status $status | ${audit.results.count { it.dispatchEvidence.status == status }} |")
        }
        appendLine()
        appendLine("## Bridge Contract")
        appendLine()
        appendLine("Each legacy entry is analyzed exactly once. `event.start`, `action.wait` and `feedback.beep` use native V1 definitions and verify their generated legacy compatibility entries through one shared adapter. Every other generated `CommandDefinition` remains a migration proposal. Typed issues carry the reason and repository evidence. M1B-3H-A adds read-only structural classification for legacy `input.touch`; it does not alter parser, Workspace, IR, source serialization or runtime dispatch.")
        appendLine()
        appendLine("## Complete Entry Matrix")
        appendLine()
        appendLine("| Legacy ID | Current name | Proposed name | Class | Return | Naming | Type | Expression | Overall | Issues |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        audit.results.forEach { result ->
            val entry = result.sourceEntry
            appendLine(
                "| `${md(entry.id)}` | `${md(entry.canonicalName)}` | `${md(result.definition?.canonicalName ?: "unmappable")}` | " +
                    "${result.definition?.semanticClass ?: "UNKNOWN"} | ${md(entry.returnType ?: "Void/unspecified")} -> ${md(result.definition?.returnType?.let(::renderType) ?: "unknown")} | " +
                    "${result.namingStatus()} | ${result.typeStatus()} | ${result.expressionStatus()} | ${result.status} | ${result.issues.joinToString("<br>") { it.type.name }.ifBlank { "none" }} |",
            )
        }
        appendLine()
        appendDuplicateCanonicalNames(audit)
        appendAliasCollisions(audit)
        appendNamingAudit(audit, entries)
        appendIfAudit(audit)
        appendBooleanAudit(audit)
        appendQueryAudit(audit)
        appendQueryReturnDecisions(audit)
        appendDefaultAudit(audit)
        appendSignatureAudits(audit)
        appendProviderAudit(audit)
        appendLiveAudit(audit)
        appendProjectionAudit(audit)
        appendLine("## M1B-2D Preflight Decisions")
        appendLine()
        appendLine("| Candidate | Classification | Decision | Reason |")
        appendLine("| --- | --- | --- | --- |")
        appendLine("| `event.start` | SAFE | NATIVE_V1 | Existing implicit source entrypoint maps losslessly to the event block, IR root and runtime entrypoint. |")
        appendLine("| `variable.get` | EXPRESSION_PROJECTION | LEGACY_ALIAS | Source variables use dynamic reporter identity and serialize as references, not `get(...)`. |")
        appendLine("| `logic.and` | CONFLICT | DEFER | Existing M1B-1 `OperatorDefinition` owns `&&`; a command would duplicate semantic truth. |")
        appendLine("| `logic.or` | CONFLICT | DEFER | Existing M1B-1 `OperatorDefinition` owns `||`; a command would duplicate semantic truth. |")
        appendLine("| `literal.number` | CONFLICT | DEFER | Number literals belong to grammar and expression IR, not callable command syntax. |")
        appendLine()
        appendLine("## Native V1 Migration")
        appendLine()
        appendLine("`event.start`, `action.wait` and `feedback.beep` are the M1B-2D native commands. Typed defaults project to the existing legacy scalar strings, and shared dual-read parity covers identity, signature, return, side effect, capability, provider and dispatchability.")
        appendLine()
        appendLine("## Migration Boundary")
        appendLine()
        appendLine("M1B-2D changes only the source of truth for `event.start`. It does not make the implicit entrypoint source-visible and does not change canonical names, aliases, command kinds, return values, parser acceptance, block definitions, runtime gates, dispatch, providers or projection behavior. The four rejected candidates remain explicit contract conflicts; all other proposed definitions remain read-only analysis output.")
        appendLine()
        appendNamingNormalization(audit)
        appendRemainingGroups(audit)
        appendTypeConflictDecisions(audit)
        appendExpressionTransport(audit)
    }

    private fun StringBuilder.appendNamingNormalization(audit: LegacyCommandBridgeAudit) {
        appendLine("## M1B-3A Naming Normalization")
        appendLine()
        appendLine("Command identity and source spelling are independent. The catalog keeps each stable ID and writes the canonical V1 spelling; explicit aliases retain V1.x reads and have no removal before V2.")
        appendLine()
        appendLine("| Stable ID | Legacy spelling | Canonical V1 | Alias lifecycle | Naming | Overall |")
        appendLine("| --- | --- | --- | --- | --- | --- |")
        EmscriptV1NamingNormalizations.ALL.forEach { normalization ->
            val result = audit.results.single { it.sourceEntry.id == normalization.stableId }
            val lifecycle = normalization.legacySpellings.joinToString("<br>") {
                "`${md(it.name)}`: read ${it.readableThrough}, remove >= ${it.earliestRemoval}"
            }
            appendLine("| `${md(normalization.stableId)}` | ${normalization.legacyAliases.joinToString { "`${md(it)}`" }} | `${md(normalization.canonicalName)}` | $lifecycle | ${result.namingStatus()} | ${result.status} / ${result.migrationClass} |")
        }
        appendLine()
        appendLine("`action.findTemplate` remains `NEEDS_NORMALIZATION` toward `templateFind`, but is outside the exclusive three-entry group because its independent domain-default and query-return contracts remain unresolved. M1B-3A does not change it.")
        appendLine()
    }

    private fun StringBuilder.appendRemainingGroups(audit: LegacyCommandBridgeAudit) {
        val groups = audit.results
            .filterNot { it.migrationClass == CommandMigrationClass.NATIVE_V1 }
            .groupingBy(::remainingGroup)
            .eachCount()
        appendLine("## M1B-3 Remaining-Entry Groups")
        appendLine()
        appendLine("The groups are exclusive and ordered by the first architectural blocker: projection, operator/literal model, control structure, query return, provider ownership, runtime dispatch, type contract, naming, then lossless generic candidates.")
        appendLine()
        appendLine("| Group | Count |")
        appendLine("| --- | ---: |")
        RemainingEntryGroup.entries.forEach { group ->
            appendLine("| ${group.name} | ${groups[group] ?: 0} |")
        }
        appendLine("| TOTAL_REMAINING | ${groups.values.sum()} |")
        appendLine()
    }

    private fun StringBuilder.appendTypeConflictDecisions(audit: LegacyCommandBridgeAudit) {
        appendLine("## M1B-3B Type-Conflict Decisions")
        appendLine()
        appendLine("`TypeStatus` is orthogonal to bridge and migration status. `CONTRACT_DECIDED` means the normative treatment is known, not that the legacy representation has already been migrated.")
        appendLine()
        appendLine("| Stable ID | Legacy type | Normative V1 type/model | Classification | Type status | Implemented | Remaining blocker |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        EmscriptV1TypeConflictDecisions.ALL.forEach { decision ->
            val result = audit.results.single { it.sourceEntry.id == decision.stableId }
            appendLine(
                "| `${md(decision.stableId)}` | ${md(decision.legacyType)} | ${md(decision.normativeV1Model)} | " +
                    "${decision.classification} | ${result.typeStatus()} | ${if (decision.implemented) "yes" else "no"} | ${md(decision.remainingBlocker)} |",
            )
        }
        appendLine()
        val remainingTypeConflicts = EmscriptV1TypeConflictDecisions.ALL.count { !it.implemented }
        val nativeCount = audit.results.count { it.migrationClass == CommandMigrationClass.NATIVE_V1 }
        appendLine("M1B-3C implements `debug.log` expression transport, M1B-3D typed `variable.set`, M1B-3E `datastorePut(String, String)`, M1B-3F separates VariableReference and Bool literal projections from language commands, and M1B-3G converges `feedback.vibrate` on one variadic Number signature. The other $remainingTypeConflicts decision still requires structural migration work. These bounded implementations do not imply native migration; `NATIVE_V1` remains $nativeCount.")
        appendLine()
    }

    private fun StringBuilder.appendExpressionTransport(audit: LegacyCommandBridgeAudit) {
        val log = audit.results.single { it.sourceEntry.id == "debug.log" }
        appendLine("## M1B-3C Lossless Command-Argument Expressions")
        appendLine()
        appendLine("The first conformance probe is `log(value: Any)`. Its parameter is a semantic ValueInput and carries the existing literal, VariableReference or operator reporter into `IrExpression`; conversion to display text occurs only after DryRun/runtime evaluation.")
        appendLine()
        appendLine("Invariants:")
        appendLine()
        appendLine("- `Command Argument Expression != Serialized String`")
        appendLine("- `Any != String`")
        appendLine("- `VariableReference != variable.get Command`")
        appendLine("- Config/presentation fields remain fields; only semantic INPUT properties use reporter connections.")
        appendLine("- Legacy stored `message` fields remain read-compatible but are not the canonical projection.")
        appendLine()
        appendLine("`debug.log`: TypeStatus `${log.typeStatus()}`, ExpressionStatus `${log.expressionStatus()}`, bridge `${log.status}`, migration `${log.migrationClass}`. The last value must remain non-native until a separate migration slice.")
        appendLine()
        appendAssignmentTypecheck(audit)
    }

    private fun StringBuilder.appendAssignmentTypecheck(audit: LegacyCommandBridgeAudit) {
        val set = audit.results.single { it.sourceEntry.id == "variable.set" }
        appendLine("## M1B-3D Typed Variable Assignment")
        appendLine()
        appendLine("`LET` stores the declared or initializer-inferred type in `WorkspaceDocument.variables[variableId]`. `SET` resolves the target by `variableId`, types its connected expression, and applies `LanguageTypeCompatibility` before IR generation.")
        appendLine()
        appendLine("`variable.set`: TypeStatus `${set.typeStatus()}`, ExpressionStatus `${set.expressionStatus()}`, bridge `${set.status}`, migration `${set.migrationClass}`. Legacy catalog `Any` remains a bridge issue, so the command is intentionally not `NATIVE_V1`.")
        appendLine()
        appendDatastorePutTypecheck(audit)
    }

    private fun StringBuilder.appendDatastorePutTypecheck(audit: LegacyCommandBridgeAudit) {
        val datastorePut = audit.results.single { it.sourceEntry.id == "system.datastorePut" }
        appendLine("## M1B-3E datastorePut String Contract")
        appendLine()
        appendLine("`datastorePut` has two required semantic ValueInputs: `key: String` and `value: String`. Parser expressions are preserved through Workspace and IR; `WorkspaceValueTypeSystem` delegates compatibility to `LanguageTypeCompatibility`. Number, Bool and Any do not implicitly convert to String and fail during pre-apply validation.")
        appendLine()
        appendLine("The same catalog-driven ValueInput mechanism validates command parameters that explicitly declare `acceptedTypes`. `system.datastoreGet` is now a separate nullable `String?` reporter; no unrelated command was migrated by that change.")
        appendLine()
        appendLine("`system.datastorePut`: TypeStatus `${datastorePut.typeStatus()}`, ExpressionStatus `${datastorePut.expressionStatus()}`, bridge `${datastorePut.status}`, migration `${datastorePut.migrationClass}`. It remains non-native by design.")
        appendExpressionModelCleanup(audit)
    }

    private fun StringBuilder.appendExpressionModelCleanup(audit: LegacyCommandBridgeAudit) {
        appendLine()
        appendLine("## M1B-3F Expression Model Cleanup")
        appendLine()
        appendLine("`VariableReference != Command`, `Bool Literal != Command`, `Visual Projection != Language Command`, and `Legacy Catalog Entry != V1 Command`.")
        appendLine()
        appendLine("| Catalog entry | Catalog role | Canonical semantic model | Bridge | Migration |")
        appendLine("| --- | --- | --- | --- | --- |")
        listOf("variable.get", "logic.boolean", "literal.boolean").forEach { id ->
            val result = audit.results.single { it.sourceEntry.id == id }
            val model = EmscriptV1TypeConflictDecisions.byStableId(id)?.normativeV1Model.orEmpty()
            appendLine("| `${result.sourceEntry.id}` | ${result.sourceEntry.role} | ${md(model)} | ${result.status} | ${result.migrationClass} |")
        }
        appendLine()
        appendLine("The compatibility catalog still contains 127 heterogeneous entries. That inventory includes language commands, expressions, literals, visual projections, structural constructs and legacy aliases; it is not the EMScript V1 language-command count. `NATIVE_V1` remains 3.")
        appendVibrateSignatureConvergence(audit)
    }

    private fun StringBuilder.appendVibrateSignatureConvergence(audit: LegacyCommandBridgeAudit) {
        val vibrate = audit.results.single { it.sourceEntry.id == "feedback.vibrate" }
        appendLine()
        appendLine("## M1B-3G feedback.vibrate Signature Convergence")
        appendLine()
        appendLine("The normative V1 contract is `vibrate(patternMs: Number...): Void`. The variadic parameter is required, which generically enforces a minimum of one argument; there is no maximum, no language default and no repeat parameter.")
        appendLine()
        appendLine("Every pattern member follows the shared command-expression path and must be Number-compatible. One value is dispatched as a one-shot duration. Multiple values retain their order as alternating delay/vibration phases and are dispatched as a non-repeating waveform. Even and odd lengths are both accepted.")
        appendLine()
        appendLine("Zero and negative values remain explicit through source, Workspace, IR and DryRun. The existing Android adapter clamps them to zero and removes non-positive phases before one-shot/waveform dispatch. If no positive phase remains, it performs no vibration. This is adapter behavior, not a source-language default or validation rule.")
        appendLine()
        appendLine("Legacy workspaces with an explicit scalar `pattern` field remain readable and serialize as one explicit argument, including an explicit `80`. The existing empty-field fallback to `80` is classified strictly as `LEGACY READ NORMALIZATION`; it is not part of the V1 CommandDefinition. Source `vibrate()` is rejected and never becomes `vibrate(80)`. A dynamic block mutator is intentionally deferred.")
        appendLine()
        appendLine("`feedback.vibrate`: TypeStatus `${vibrate.typeStatus()}`, ExpressionStatus `${vibrate.expressionStatus()}`, bridge `${vibrate.status}`, migration `${vibrate.migrationClass}`. It remains non-native by design.")
        appendLegacyTouchStructuralClassification(audit)
    }

    private fun StringBuilder.appendLegacyTouchStructuralClassification(audit: LegacyCommandBridgeAudit) {
        val touch = audit.results.single { it.sourceEntry.id == "input.touch" }
        appendLine()
        appendLine("## M1B-3H-A Legacy input.touch Structural Classification")
        appendLine()
        appendLine("The classifier is analysis-only. It preserves exact raw argument text, records only observed evidence and does not choose a V1 target type. No current repository fixture proves timing, pointer identity, multi-pointer structure, complete gesture boundaries or coordinate space.")
        appendLine()
        appendLine("| Alias | Payload | Classification | Evidence | Migration readiness |")
        appendLine("| --- | --- | --- | --- | --- |")
        touchInventoryFixtures.forEach { (alias, payload) ->
            val analysis = LegacyTouchStructuralClassifier.classify(alias, payload)
            appendLine(
                "| `${md(alias)}` | `${md(payload)}` | ${analysis.classification} | " +
                    "${analysis.evidence.joinToString("<br>").ifBlank { "none" }} | ${analysis.migrationReadiness} |",
            )
        }
        appendLine()
        appendLine("`input.touch`: TypeStatus `${touch.typeStatus()}`, bridge `${touch.status}`, migration `${touch.migrationClass}`. It remains the sole `C_TYPE_CONFLICT`, is not `NATIVE_V1`, and live dispatch remains disabled.")
    }

    private fun remainingGroup(result: CommandBridgeResult): RemainingEntryGroup {
        val entry = result.sourceEntry
        val issueTypes = result.issues.mapTo(mutableSetOf()) { it.type }
        return when {
            entry.role != CommandCatalogRole.LANGUAGE_COMMAND -> RemainingEntryGroup.H_PROJECTION
            entry.id.startsWith("rem.") || CommandBridgeIssueType.PROJECTION_MODELED_AS_COMMAND in issueTypes ->
                RemainingEntryGroup.H_PROJECTION
            CommandBridgeIssueType.OPERATOR_OR_LITERAL_MODEL in issueTypes ||
                entry.kind == CommandCatalogKind.OPERATOR ||
                entry.id.startsWith("literal.") -> RemainingEntryGroup.F_OPERATOR_OR_LITERAL_MODEL
            entry.kind == CommandCatalogKind.CONTROL || CommandBridgeIssueType.CONTROL_DUPLICATE in issueTypes ->
                RemainingEntryGroup.E_CONTROL_STRUCTURE
            CommandBridgeIssueType.QUERY_RETURNS_VOID in issueTypes || CommandBridgeIssueType.MISSING_RETURN_TYPE in issueTypes ->
                RemainingEntryGroup.D_QUERY_RETURN
            result.dispatchEvidence.status == CommandLiveStatus.LIVE_PROVIDER_DEPENDENT ||
                entry.pluginOwner != "visualtasker.core" -> RemainingEntryGroup.G_PROVIDER_DEPENDENT
            issueTypes.any {
                it in setOf(
                    CommandBridgeIssueType.LIVE_FLAG_WITHOUT_DISPATCH,
                    CommandBridgeIssueType.DISPATCH_WITHOUT_LIVE_FLAG,
                    CommandBridgeIssueType.MULTIPLE_RUNTIME_DISPATCH,
                )
            } -> RemainingEntryGroup.I_RUNTIME_DISPATCH_CONFLICT
            issueTypes.any {
                it in setOf(
                    CommandBridgeIssueType.UNTYPED_PARAMETER,
                    CommandBridgeIssueType.UNTYPED_DEFAULT,
                    CommandBridgeIssueType.INVALID_DEFAULT,
                    CommandBridgeIssueType.DEFAULT_SOURCE_CONFLICT,
                    CommandBridgeIssueType.SIGNATURE_CONFLICT,
                    CommandBridgeIssueType.REPORTER_DUPLICATE,
                    CommandBridgeIssueType.VARIABLE_IDENTITY_MODEL_CONFLICT,
                )
            } -> RemainingEntryGroup.C_TYPE_CONFLICT
            issueTypes.any {
                it in setOf(
                    CommandBridgeIssueType.DUPLICATE_CANONICAL_NAME,
                    CommandBridgeIssueType.AMBIGUOUS_ALIAS,
                    CommandBridgeIssueType.LEGACY_ALIAS_REQUIRED,
                    CommandBridgeIssueType.NAMING_FAMILY_OUTLIER,
                    CommandBridgeIssueType.NON_CANONICAL_NAME,
                )
            } -> RemainingEntryGroup.B_NAMING_NORMALIZATION
            else -> RemainingEntryGroup.A_SAFE_GENERIC
        }
    }

    private enum class RemainingEntryGroup {
        A_SAFE_GENERIC,
        B_NAMING_NORMALIZATION,
        C_TYPE_CONFLICT,
        D_QUERY_RETURN,
        E_CONTROL_STRUCTURE,
        F_OPERATOR_OR_LITERAL_MODEL,
        G_PROVIDER_DEPENDENT,
        H_PROJECTION,
        I_RUNTIME_DISPATCH_CONFLICT,
    }

    private fun StringBuilder.appendDuplicateCanonicalNames(audit: LegacyCommandBridgeAudit) {
        val groups = audit.results.groupBy { it.sourceEntry.canonicalName.lowercase() }.filterValues { it.size > 1 }
        appendLine("## Duplicate Canonical Names")
        appendLine()
        appendLine("| Name | IDs | Actual semantic difference |")
        appendLine("| --- | --- | --- |")
        groups.toSortedMap().forEach { (name, results) ->
            val difference = when (name) {
                "if" -> "Three control-block variants with different statement-slot contracts; parser syntax selects structure, not a catalog command overload."
                "boolean" -> "Both are visual projections of one Bool-literal expression: logic.boolean is the V1.x legacy alias and literal.boolean is canonical."
                else -> "Needs contract decision."
            }
            appendLine("| `$name` | ${results.joinToString { "`${it.sourceEntry.id}`" }} | $difference |")
        }
        appendLine()
    }

    private fun StringBuilder.appendAliasCollisions(audit: LegacyCommandBridgeAudit) {
        appendLine("## Alias Resolution Collisions")
        appendLine()
        appendLine("Current lookup lowercases input and returns the first candidate. The bridge reports every candidate and never uses the winner as semantic truth.")
        appendLine()
        appendLine("| Input | Candidates | Current winner | V1 result |")
        appendLine("| --- | --- | --- | --- |")
        audit.aliasCollisions.forEach { collision ->
            appendLine("| `${md(collision.inputName)}` | ${collision.candidateIds.joinToString { "`${md(it)}`" }} | `${md(collision.currentWinnerId)}` | AMBIGUOUS |")
        }
        if (audit.aliasCollisions.isEmpty()) appendLine("| none | none | none | CLEAN |")
        appendLine()
    }

    private fun StringBuilder.appendNamingAudit(
        audit: LegacyCommandBridgeAudit,
        entries: List<CommandCatalogEntry>,
    ) {
        appendLine("## Command Family Naming Audit")
        appendLine()
        appendLine("| Family | Current command | Sibling pattern | Proposed canonical | Legacy alias | Confidence | Reason |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        audit.namingAudit.forEach { item ->
            appendLine("| ${item.family} | `${md(item.currentCommand)}` | ${md(item.siblingPattern)} | `${md(item.proposedCanonical)}` | ${item.legacyAliasRequired} | ${item.confidence} | ${md(item.reason)} |")
        }
        familyCoverage(entries).forEach { row -> appendLine(row) }
        appendLine()
        appendLine("`findTemplate` is **NORMALIZABLE** to `templateFind`; `findTemplate` remains a required legacy alias. This is supported by the existing siblings `templateDefine` and `templateCompare`.")
        appendLine()
    }

    private fun familyCoverage(entries: List<CommandCatalogEntry>): List<String> {
        fun names(predicate: (CommandCatalogEntry) -> Boolean): String = entries.filter(predicate)
            .joinToString { "`${it.canonicalName}`" }
            .ifBlank { "none" }
        return listOf(
            "| Marker | ${names { it.id.startsWith("vision.marker") }} | marker+operation | unchanged | false | HIGH | Consistent sibling family. |",
            "| Scene | ${names { it.id.startsWith("scene.") }} | scene+operation | unchanged | false | MEDIUM | Current family has one member. |",
            "| Vision/Image/OCR | ${names { it.category in setOf(BlockCategories.VISION, BlockCategories.PERCEPTION) }} | mixed | NEEDS_DECISION | true | MEDIUM | Mixed standalone verbs and domain aliases; no mechanical rename. |",
            "| Input | ${names { it.category == BlockCategories.INPUT || it.id == "action.clickText" }} | operation+target | clickText | true | HIGH | Coordinate click and text click require separate signatures. |",
            "| Feedback | ${names { it.category == BlockCategories.FEEDBACK }} | verb | unchanged | false | HIGH | Names are consistent; vibrate signature is not. |",
            "| File/Storage | ${names { it.category in setOf(BlockCategories.FILE, BlockCategories.SYSTEM) }} | lowerCamel namespace | namespace case only | true | HIGH | Uppercase legacy namespace segments violate V1 naming. |",
            "| Provider namespaces | ${names { it.pluginOwner != "visualtasker.core" }} | lowerCamel provider.operation | namespace case only | true | HIGH | Provider identity must be separate from pluginOwner. |",
        )
    }

    private fun StringBuilder.appendIfAudit(audit: LegacyCommandBridgeAudit) {
        appendLine("## `if` Variant Audit")
        appendLine()
        appendLine("| ID | Kind | Parameters | Return | Block | Runtime | Semantics |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        audit.results.filter { it.sourceEntry.canonicalName.equals("if", true) }.forEach { result ->
            val entry = result.sourceEntry
            appendLine("| `${entry.id}` | ${entry.kind} | ${entry.arguments.joinToString { it.name }} | ${entry.returnType ?: "Void"} | `${entry.block?.blockType ?: "none"}` | ${entry.runtime?.dryRunBehavior ?: "none"} | Distinct branch-slot variant of one control construct. |")
        }
        appendLine()
        appendLine("Classification: **B, variants of the same control family**. They are not three independently callable V1 commands and are not merged in M1B-2A.")
        appendLine()
    }

    private fun StringBuilder.appendBooleanAudit(audit: LegacyCommandBridgeAudit) {
        appendLine("## `boolean` Duplicate Audit")
        appendLine()
        appendLine("| ID | Block | Runtime | Actual meaning |")
        appendLine("| --- | --- | --- | --- |")
        audit.results.filter { it.sourceEntry.canonicalName.equals("boolean", true) }.forEach { result ->
            val entry = result.sourceEntry
            val meaning = if (entry.id == "literal.boolean") "Canonical Bool-literal projection" else "V1.x legacy Bool-literal projection alias"
            appendLine("| `${entry.id}` | `${entry.block?.blockType ?: "none"}` | ${entry.runtime?.dryRunBehavior ?: "none"} | $meaning |")
        }
        appendLine()
        appendLine("The duplicate is a semantic conflict, not a display-only duplicate. A V1 naming decision is required before migration.")
        appendLine()
    }

    private fun StringBuilder.appendQueryAudit(audit: LegacyCommandBridgeAudit) {
        appendLine("## Query Return Audit")
        appendLine()
        appendLine("| Command | Kind | Current return | Runtime result | Block output | Proposed return | Evidence | Confidence |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- |")
        audit.queryAudit.forEach { item ->
            appendLine("| `${item.commandId}` | ${item.currentKind} | ${md(item.currentReturn)} | ${md(item.observedRuntimeResult)} | ${md(item.blockOutput)} | ${md(item.proposedReturnType)} | ${md(item.evidence)} | ${item.confidence} |")
        }
        appendLine()
        appendLine("Entries reported as `NEEDS_DECISION` remain Void in the proposed bridge definition so the conflict cannot be mistaken for an invented return type.")
        appendLine()
    }

    private fun StringBuilder.appendQueryReturnDecisions(audit: LegacyCommandBridgeAudit) {
        QueryReturnContractAudit.validateAgainst(audit)
        appendLine("## M1B-3U Shizuku Availability Convergence")
        appendLine()
        appendLine("The current `D_QUERY_RETURN` inventory contains ${QueryReturnContractAudit.ALL.size} language commands. Those remaining entries are statement-shaped; `shizuku.isAvailable` has joined the previously converged typed reporters.")
        appendLine()
        appendLine("| Class | Count |")
        appendLine("| --- | ---: |")
        QueryReturnClass.entries.forEach { queryClass ->
            appendLine("| $queryClass | ${QueryReturnContractAudit.ALL.count { it.primaryClass == queryClass }} |")
        }
        appendLine()
        appendLine("| Stable ID | Observed runtime | Proposed V1 | Class | Runtime | Provider | Risk |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        QueryReturnContractAudit.ALL.forEach { item ->
            appendLine(
                "| `${md(item.stableId)}` | ${md(item.observedRuntimeType)} | ${md(item.proposedV1ReturnType)} | " +
                    "${item.primaryClass} | ${if (item.runtimeValueExists) "typed value observed" else "gap"} | " +
                    "${if (QueryReturnFlag.PROVIDER_BACKED in item.secondaryFlags) "yes" else "no"} | ${item.migrationRisk} |",
            )
        }
        appendLine()
        appendLine("M1B-3U migrates `shizuku.isAvailable` as non-null Bool through the generic `RuntimeAdapterResult.value` payload. Normal unavailable states remain successful false; installation, permission and Binder inspection failures retain distinct structured diagnostics.")
        appendLine()
    }

    private fun StringBuilder.appendDefaultAudit(audit: LegacyCommandBridgeAudit) {
        appendLine("## Default Value Audit")
        appendLine()
        appendLine("| Command | Parameter | Declared | Raw | Parser | Runtime | V1 type | Bridge result |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- |")
        audit.defaultAudit.forEach { item ->
            appendLine("| `${item.commandId}` | `${item.parameter}` | ${item.declaredType} | `${md(item.rawDefault)}` | ${md(item.parserInterpretation)} | ${md(item.runtimeInterpretation)} | `${item.targetType}` | ${md(item.result)} |")
        }
        appendLine()
        appendLine("`ANY` defaults are typed only as a bridge guess and remain marked `UNTYPED_DEFAULT`; they are not promoted to V1 truth.")
        appendLine()
    }

    private fun StringBuilder.appendSignatureAudits(audit: LegacyCommandBridgeAudit) {
        fun result(id: String) = audit.results.first { it.sourceEntry.id == id }
        appendLine("## Signature Conflicts")
        appendLine()
        appendLine("### click / clickText")
        appendLine()
        appendLine("`action.clickText` currently exposes `click(text)`. V1 requires `click` for point/coordinate semantics and `clickText` for text lookup. Legacy `click(\"text\")` therefore needs signature-aware import normalization, not a simple alias.")
        appendLine()
        appendLine("### vibrate")
        appendLine()
        appendLine("Catalog: one `DURATION_MS` parameter with default `80`. Parser/generator/runtime: multiple integer durations interpreted as an alternating vibration/pause pattern. Runtime consumes a comma-separated `Long` list and falls back to `[80]`. Result: ${result("feedback.vibrate").status}; V1 signature remains undecided.")
        appendLine()
        appendLine("### log")
        appendLine()
        appendLine("Catalog declares `TEXT`; parser accepts an expression and runtime renders the resulting value to text. This supports `log(Any)` plus deterministic string conversion, but M1B-2A records the conflict rather than changing either side. Result: ${result("debug.log").status}.")
        appendLine()
    }

    private fun StringBuilder.appendProviderAudit(audit: LegacyCommandBridgeAudit) {
        appendLine("## Provider / Capability / Owner Audit")
        appendLine()
        appendLine("| Command | Capability | pluginOwner | Observed adapter | Provider candidate | Conflict |")
        appendLine("| --- | --- | --- | --- | --- | --- |")
        audit.results.filter { it.sourceEntry.pluginOwner != "visualtasker.core" || it.definition?.provider != null }.forEach { result ->
            val entry = result.sourceEntry
            appendLine("| `${entry.id}` | ${entry.runtime?.liveCapabilityGate ?: "none"} | `${entry.pluginOwner}` | ${md(result.dispatchEvidence.evidence)} | `${result.definition?.provider?.value ?: "none"}` | ${result.issues.filter { it.type in providerIssueTypes }.joinToString { it.type.name }.ifBlank { "none" }} |")
        }
        appendLine()
    }

    private fun StringBuilder.appendLiveAudit(audit: LegacyCommandBridgeAudit) {
        appendLine("## Live Dispatch Audit")
        appendLine()
        appendLine("`liveImplemented=true` is treated only as a legacy claim. Confirmation requires resolver/gate, runtime branch, adapter and result/error path.")
        appendLine()
        appendLine("### LIVE_FLAG_WITHOUT_DISPATCH")
        appendLine()
        appendLine("| Command | Catalog flag | Classification | Evidence |")
        appendLine("| --- | --- | --- | --- |")
        val missing = audit.results.filter { result ->
            result.issues.any { it.type == CommandBridgeIssueType.LIVE_FLAG_WITHOUT_DISPATCH }
        }
        missing.forEach { result ->
            appendLine("| `${result.sourceEntry.id}` | ${result.sourceEntry.runtime?.liveImplemented} | ${result.dispatchEvidence.status} | ${md(result.dispatchEvidence.evidence)} |")
        }
        if (missing.isEmpty()) appendLine("| none | none | none | none |")
        appendLine()
    }

    private fun StringBuilder.appendProjectionAudit(audit: LegacyCommandBridgeAudit) {
        appendLine("## Projection Commands")
        appendLine()
        appendLine("| ID | Kind | Side effect | liveImplemented | Bridge result |")
        appendLine("| --- | --- | --- | --- | --- |")
        audit.results.filter { it.sourceEntry.id.startsWith("rem.") }.forEach { result ->
            val entry = result.sourceEntry
            appendLine("| `${entry.id}` | ${entry.kind} | ${entry.sideEffect} | ${entry.runtime?.liveImplemented} | PROJECTION_MODELED_AS_COMMAND |")
        }
        appendLine()
        appendLine("All `rem.*` entries must later move to `ProjectionDefinition`; none is migrated here.")
        appendLine()
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"").replace("\n", " ")}\""

    private fun md(value: String): String = value.replace("|", "\\|").replace("\n", " ")

    private fun renderType(type: LanguageTypeRef): String =
        de.visualtasker.emscript.contract.LanguageTypeCompatibility.sourceName(type)

    private fun touchAnalysisSummary(): String =
        "M1B-3H-A classifier: 3 repository payload forms, all PARTIAL; raw preserved; no typed migration."

    private val touchInventoryFixtures = listOf(
        "touch" to "[540, 1100]",
        "touch" to "[\"down\", 120, 240, \"up\"]",
        "touch" to "\"down(10,20);move(20,30);up(20,30)\"",
    )

    private val providerIssueTypes = setOf(
        CommandBridgeIssueType.CAPABILITY_PROVIDER_MIXED,
        CommandBridgeIssueType.PLUGIN_OWNER_NOT_PROVIDER,
        CommandBridgeIssueType.MISSING_CAPABILITY,
        CommandBridgeIssueType.MISSING_PROVIDER_METADATA,
    )
}
