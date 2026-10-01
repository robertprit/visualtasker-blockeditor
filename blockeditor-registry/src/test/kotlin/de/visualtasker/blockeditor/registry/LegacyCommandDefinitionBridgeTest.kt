package de.visualtasker.blockeditor.registry

import de.visualtasker.emscript.contract.ContractValue
import de.visualtasker.emscript.contract.CoreTypes
import de.visualtasker.emscript.contract.EmscriptV1Commands
import de.visualtasker.emscript.contract.LanguageTypeCompatibility
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCommandDefinitionBridgeTest {
    private val catalogEntries = VisualTaskerCommandCatalog.allEntries()
    private val audit = LegacyCommandDefinitionBridge().analyze()

    @Test
    fun `every catalog entry produces exactly one deterministic bridge result`() {
        val second = LegacyCommandDefinitionBridge().analyze()

        assertEquals(127, catalogEntries.size)
        assertEquals(catalogEntries.size, audit.results.size)
        assertEquals(catalogEntries.map { it.id }.toSet(), audit.results.map { it.sourceEntry.id }.toSet())
        assertEquals(audit, second)
        assertEquals(catalogEntries.size, audit.countsByStatus.values.sum())
        assertEquals(3, audit.results.count { it.migrationClass == CommandMigrationClass.NATIVE_V1 })
        assertEquals(
            setOf("event.start", "action.wait", "feedback.beep"),
            audit.results
                .filter { it.migrationClass == CommandMigrationClass.NATIVE_V1 }
                .map { it.sourceEntry.id }
                .toSet(),
        )
    }

    @Test
    fun `event start uses native V1 truth and retains implicit source semantics`() {
        val eventStart = audit.result("event.start")
        val legacy = eventStart.sourceEntry
        val native = eventStart.definition!!

        assertEquals(EmscriptV1Commands.EVENT_START, native)
        assertEquals(CommandMigrationClass.NATIVE_V1, eventStart.migrationClass)
        assertEquals(CommandBridgeStatus.CLEAN, eventStart.status)
        assertTrue(NativeCommandLegacyDefinitions.parityIssues(legacy, native).isEmpty())
        assertEquals(CommandCatalogKind.EVENT, legacy.kind)
        assertTrue(legacy.arguments.isEmpty())
        assertEquals(CommandSideEffect.CONTROL_FLOW, legacy.sideEffect)
        assertEquals(CommandLiveStatus.LIVE_CONFIRMED, eventStart.dispatchEvidence.status)
    }

    @Test
    fun `event start mismatch cannot be silently accepted by dual read`() {
        val native = EmscriptV1Commands.EVENT_START
        val projected = NativeCommandLegacyDefinitions.requireEntry(native.id.value)
        val mismatched = projected.copy(canonicalName = "start")
        val customEntries = catalogEntries.map { if (it.id == mismatched.id) mismatched else it }
        val result = LegacyCommandDefinitionBridge(entries = customEntries).analyze().result("event.start")

        assertEquals(CommandBridgeStatus.CONFLICT, result.status)
        assertTrue(result.hasIssue(CommandBridgeIssueType.NATIVE_DEFINITION_PARITY_MISMATCH))
    }

    @Test
    fun `preflight distinguishes expression projections from unresolved command duplication`() {
        val variableGet = audit.result("variable.get")
        val logicAnd = audit.result("logic.and")
        val logicOr = audit.result("logic.or")
        val number = audit.result("literal.number")

        assertFalse(variableGet.hasIssue(CommandBridgeIssueType.VARIABLE_IDENTITY_MODEL_CONFLICT))
        assertEquals(CommandBridgeStatus.CLEAN, variableGet.status)
        assertEquals(CommandMigrationClass.LEGACY_ALIAS, variableGet.migrationClass)
        assertTrue(logicAnd.hasIssue(CommandBridgeIssueType.OPERATOR_OR_LITERAL_MODEL))
        assertTrue(logicOr.hasIssue(CommandBridgeIssueType.OPERATOR_OR_LITERAL_MODEL))
        assertTrue(number.hasIssue(CommandBridgeIssueType.OPERATOR_OR_LITERAL_MODEL))
        assertTrue(listOf(logicAnd, logicOr, number).all {
            it.status == CommandBridgeStatus.CONFLICT &&
                it.migrationClass == CommandMigrationClass.CONTRACT_DECISION
        })
    }

    @Test
    fun `naming normalization is resolved independently from native migration`() {
        val normalized = EmscriptV1NamingNormalizations.ALL.map { audit.result(it.stableId) }

        assertEquals(3, normalized.size)
        assertTrue(normalized.all { it.namingStatus() == CommandNamingStatus.RESOLVED_WITH_LEGACY_ALIAS })
        assertTrue(normalized.all { it.status == CommandBridgeStatus.CLEAN })
        assertTrue(normalized.all { it.migrationClass == CommandMigrationClass.NONE })
        assertTrue(normalized.none { it.migrationClass == CommandMigrationClass.NATIVE_V1 })

        val templateFind = audit.result("action.findTemplate")
        assertEquals(CommandNamingStatus.NEEDS_NORMALIZATION, templateFind.namingStatus())
        assertEquals(CommandBridgeStatus.CONFLICT, templateFind.status)
        assertTrue(templateFind.hasIssue(CommandBridgeIssueType.QUERY_RETURNS_VOID))
        assertTrue(templateFind.hasIssue(CommandBridgeIssueType.DEFAULT_SOURCE_CONFLICT))
    }

    @Test
    fun `duplicate canonical names retain every semantic candidate`() {
        val duplicates = audit.results
            .groupBy { it.sourceEntry.canonicalName.lowercase() }
            .filterValues { it.size > 1 }

        assertEquals(
            setOf("control.if", "control.ifElse", "control.ifElseIfElse"),
            duplicates.getValue("if").map { it.sourceEntry.id }.toSet(),
        )
        assertEquals(
            setOf("logic.boolean", "literal.boolean"),
            duplicates.getValue("boolean").map { it.sourceEntry.id }.toSet(),
        )
        assertTrue(duplicates.getValue("if").all { it.hasIssue(CommandBridgeIssueType.CONTROL_DUPLICATE) })
        assertTrue(duplicates.getValue("boolean").none { it.hasIssue(CommandBridgeIssueType.REPORTER_DUPLICATE) })
    }

    @Test
    fun `alias collision report is complete instead of first match dependent`() {
        val ifCollision = audit.aliasCollisions.first { it.inputName == "if" }

        assertEquals(
            setOf("control.if", "control.ifElse", "control.ifElseIfElse"),
            ifCollision.candidateIds.toSet(),
        )
        assertTrue(audit.aliasCollisions.none { it.inputName == "boolean" })
        assertEquals("control.if", ifCollision.currentWinnerId)
        assertTrue(ifCollision.reason.contains("first"))
    }

    @Test
    fun `click and template naming conflicts produce migration proposals without changing catalog`() {
        val click = audit.result("action.clickText")
        val template = audit.result("action.findTemplate")

        assertEquals("click", click.sourceEntry.canonicalName)
        assertEquals("clickText", click.definition?.canonicalName)
        assertTrue(click.hasIssue(CommandBridgeIssueType.SIGNATURE_CONFLICT))
        assertTrue(click.hasIssue(CommandBridgeIssueType.LEGACY_ALIAS_REQUIRED))

        assertEquals("findTemplate", template.sourceEntry.canonicalName)
        assertEquals("templateFind", template.definition?.canonicalName)
        assertTrue(template.hasIssue(CommandBridgeIssueType.NAMING_FAMILY_OUTLIER))
        assertTrue(template.hasIssue(CommandBridgeIssueType.LEGACY_ALIAS_REQUIRED))
        assertEquals("findTemplate", VisualTaskerCommandCatalog.findById("action.findTemplate")?.canonicalName)
    }

    @Test
    fun `nullable core queries are typed while touch conflicts remain explicit`() {
        val fileRead = audit.result("file.readText")
        val datastoreGet = audit.result("system.datastoreGet")
        val touch = audit.result("input.touch")

        assertFalse(fileRead.hasIssue(CommandBridgeIssueType.QUERY_RETURNS_VOID))
        assertFalse(fileRead.hasIssue(CommandBridgeIssueType.MISSING_RETURN_TYPE))
        assertFalse(datastoreGet.hasIssue(CommandBridgeIssueType.QUERY_RETURNS_VOID))
        assertFalse(datastoreGet.hasIssue(CommandBridgeIssueType.MISSING_RETURN_TYPE))
        assertEquals("String?", fileRead.definition?.returnType?.let(LanguageTypeCompatibility::sourceName))
        assertEquals("String?", datastoreGet.definition?.returnType?.let(LanguageTypeCompatibility::sourceName))
        assertTrue(touch.hasIssue(CommandBridgeIssueType.UNTYPED_PARAMETER))
        assertTrue(touch.hasIssue(CommandBridgeIssueType.UNTYPED_DEFAULT))
        assertEquals(CommandMigrationClass.STRUCTURAL_MIGRATION, touch.migrationClass)
    }

    @Test
    fun `vibrate converges to one lossless variadic Number contract`() {
        val vibrate = audit.result("feedback.vibrate")
        val log = audit.result("debug.log")

        assertFalse(vibrate.hasIssue(CommandBridgeIssueType.SIGNATURE_CONFLICT))
        assertFalse(vibrate.hasIssue(CommandBridgeIssueType.DEFAULT_SOURCE_CONFLICT))
        assertEquals(CommandTypeStatus.RESOLVED, vibrate.typeStatus())
        assertEquals(CommandExpressionStatus.LOSSLESS, vibrate.expressionStatus())
        assertEquals(CommandBridgeStatus.CLEAN, vibrate.status)
        assertEquals(CommandMigrationClass.NONE, vibrate.migrationClass)
        assertEquals(true, vibrate.definition?.parameters?.single()?.variadic)
        assertEquals(true, vibrate.definition?.parameters?.single()?.required)
        assertEquals(null, vibrate.definition?.parameters?.single()?.defaultValue)
        assertFalse(log.hasIssue(CommandBridgeIssueType.SIGNATURE_CONFLICT))
        assertFalse(log.hasIssue(CommandBridgeIssueType.UNTYPED_PARAMETER))
        assertFalse(log.hasIssue(CommandBridgeIssueType.UNTYPED_DEFAULT))
        assertEquals(CommandTypeStatus.RESOLVED, log.typeStatus())
        assertEquals(CommandExpressionStatus.LOSSLESS, log.expressionStatus())
        assertTrue(log.migrationClass != CommandMigrationClass.NATIVE_V1)
        assertEquals("vibrate", vibrate.sourceEntry.canonicalName)
        assertEquals("log", log.sourceEntry.canonicalName)
    }

    @Test
    fun `explicit Any expression unions are typed while raw Any payloads remain conflicts`() {
        val compare = audit.result("logic.compare")
        val variableSet = audit.result("variable.set")
        val touch = audit.result("input.touch")

        assertFalse(compare.hasIssue(CommandBridgeIssueType.UNTYPED_PARAMETER))
        assertFalse(variableSet.hasIssue(CommandBridgeIssueType.UNTYPED_PARAMETER))
        assertTrue(touch.hasIssue(CommandBridgeIssueType.UNTYPED_PARAMETER))
    }

    @Test
    fun `all eight type decisions have one normative treatment without hidden native migration`() {
        val decisions = EmscriptV1TypeConflictDecisions.ALL

        assertEquals(8, decisions.size)
        assertEquals(
            setOf(
                "input.touch",
                "system.datastorePut",
                "feedback.vibrate",
                "debug.log",
                "variable.set",
                "variable.get",
                "logic.boolean",
                "literal.boolean",
            ),
            decisions.map { it.stableId }.toSet(),
        )
        assertTrue(decisions.all { it.typeResolved })
        assertEquals(
            setOf("debug.log", "feedback.vibrate", "variable.set", "system.datastorePut", "variable.get", "logic.boolean", "literal.boolean"),
            decisions.filter { it.implemented }.map { it.stableId }.toSet(),
        )
        assertEquals(setOf("input.touch"), decisions.filterNot { it.implemented }.map { it.stableId }.toSet())
        assertTrue(
            decisions.filterNot { it.implemented }
                .all { audit.result(it.stableId).typeStatus() == CommandTypeStatus.CONTRACT_DECIDED },
        )
        assertEquals(CommandTypeStatus.RESOLVED, audit.result("debug.log").typeStatus())
        assertEquals(CommandTypeStatus.RESOLVED, audit.result("variable.set").typeStatus())
        assertEquals(CommandTypeStatus.RESOLVED, audit.result("system.datastorePut").typeStatus())
        assertEquals(CommandTypeStatus.RESOLVED, audit.result("feedback.vibrate").typeStatus())
        assertEquals(CommandExpressionStatus.LOSSLESS, audit.result("feedback.vibrate").expressionStatus())
        assertEquals(CommandExpressionStatus.LOSSLESS, audit.result("system.datastorePut").expressionStatus())
        assertEquals(CommandExpressionStatus.LOSSLESS, audit.result("variable.set").expressionStatus())
        assertTrue(decisions.none { audit.result(it.stableId).migrationClass == CommandMigrationClass.NATIVE_V1 })
        assertEquals(CommandMigrationClass.LEGACY_ALIAS, audit.result("variable.get").migrationClass)
        assertEquals(CommandMigrationClass.LEGACY_ALIAS, audit.result("logic.boolean").migrationClass)
        assertEquals(CommandMigrationClass.NONE, audit.result("literal.boolean").migrationClass)

        assertEquals(
            TypeConflictClassification.TYPE_MAPPING,
            EmscriptV1TypeConflictDecisions.byStableId("system.datastorePut")?.classification,
        )
        assertEquals(
            "datastorePut(key: String, value: String)",
            EmscriptV1TypeConflictDecisions.byStableId("system.datastorePut")?.normativeV1Model,
        )
        assertEquals(
            TypeConflictClassification.TYPE_MAPPING,
            EmscriptV1TypeConflictDecisions.byStableId("debug.log")?.classification,
        )
        assertEquals(
            TypeConflictClassification.SIGNATURE_DECISION,
            EmscriptV1TypeConflictDecisions.byStableId("feedback.vibrate")?.classification,
        )
        assertEquals(
            TypeConflictClassification.TYPECHECKER_RULE,
            EmscriptV1TypeConflictDecisions.byStableId("variable.set")?.classification,
        )
        assertEquals(
            TypeConflictClassification.EXPRESSION_MODEL,
            EmscriptV1TypeConflictDecisions.byStableId("variable.get")?.classification,
        )
        assertEquals(
            TypeConflictClassification.EXPRESSION_MODEL,
            EmscriptV1TypeConflictDecisions.byStableId("logic.boolean")?.classification,
        )
        assertEquals(
            TypeConflictClassification.STRUCTURAL_MIGRATION,
            EmscriptV1TypeConflictDecisions.byStableId("input.touch")?.classification,
        )
        assertEquals(CommandMigrationClass.STRUCTURAL_MIGRATION, audit.result("input.touch").migrationClass)
    }

    @Test
    fun `live flags require confirmed dispatch evidence`() {
        val liveWithoutDispatch = audit.results.filter { it.hasIssue(CommandBridgeIssueType.LIVE_FLAG_WITHOUT_DISPATCH) }

        assertFalse(liveWithoutDispatch.isEmpty())
        assertTrue(liveWithoutDispatch.any { it.sourceEntry.pluginOwner == "visualtasker.charts" })
        assertTrue(liveWithoutDispatch.any { it.sourceEntry.id.startsWith("rem.") })
        assertEquals(CommandLiveStatus.LIVE_CONFIRMED, audit.result("action.wait").dispatchEvidence.status)
        assertEquals(CommandLiveStatus.LIVE_PROVIDER_DEPENDENT, audit.result("action.clickText").dispatchEvidence.status)
    }

    @Test
    fun `wait uses native V1 truth and retains complete legacy parity`() {
        val wait = audit.result("action.wait")
        val legacy = wait.sourceEntry
        val native = wait.definition!!

        assertEquals(EmscriptV1Commands.WAIT, native)
        assertEquals(CommandMigrationClass.NATIVE_V1, wait.migrationClass)
        assertEquals(CommandBridgeStatus.CLEAN, wait.status)
        assertTrue(WaitCommandCompatibility.parityIssues(legacy, native).isEmpty())
        assertEquals(CommandArgumentType.DURATION_MS, legacy.arguments.single().type)
        assertEquals("500", legacy.arguments.single().defaultValue)
        assertEquals(CoreTypes.NUMBER.ref, native.parameters.single().type)
        assertEquals(ContractValue.NumberValue("500"), native.parameters.single().defaultValue)
        assertEquals(CommandLiveStatus.LIVE_CONFIRMED, wait.dispatchEvidence.status)
    }

    @Test
    fun `wait mismatch cannot be silently accepted by dual read`() {
        val mismatched = WaitCommandCompatibility.legacyEntry.copy(canonicalName = "delay")
        val customEntries = catalogEntries.map { if (it.id == mismatched.id) mismatched else it }
        val result = LegacyCommandDefinitionBridge(entries = customEntries).analyze().result("action.wait")

        assertEquals(CommandBridgeStatus.CONFLICT, result.status)
        assertTrue(result.hasIssue(CommandBridgeIssueType.NATIVE_DEFINITION_PARITY_MISMATCH))
    }

    @Test
    fun `beep uses native V1 truth and retains complete legacy parity`() {
        val beep = audit.result("feedback.beep")
        val legacy = beep.sourceEntry
        val native = beep.definition!!

        assertEquals(EmscriptV1Commands.BEEP, native)
        assertEquals(CommandMigrationClass.NATIVE_V1, beep.migrationClass)
        assertEquals(CommandBridgeStatus.CLEAN, beep.status)
        assertTrue(BeepCommandCompatibility.parityIssues(legacy, native).isEmpty())
        assertEquals(
            listOf(CommandArgumentType.FREQUENCY_HZ, CommandArgumentType.DURATION_MS, CommandArgumentType.PERCENT),
            legacy.arguments.map { it.type },
        )
        assertEquals(listOf("1000", "200", "100"), legacy.arguments.map { it.defaultValue })
        assertEquals(
            listOf(
                ContractValue.NumberValue("1000"),
                ContractValue.NumberValue("200"),
                ContractValue.NumberValue("100"),
            ),
            native.parameters.map { it.defaultValue },
        )
        assertEquals(CommandLiveStatus.LIVE_CONFIRMED, beep.dispatchEvidence.status)
    }

    @Test
    fun `beep mismatch cannot be silently accepted by dual read`() {
        val mismatchedArguments = BeepCommandCompatibility.legacyEntry.arguments.toMutableList().also {
            it[2] = it[2].copy(defaultValue = "80")
        }
        val mismatched = BeepCommandCompatibility.legacyEntry.copy(arguments = mismatchedArguments)
        val customEntries = catalogEntries.map { if (it.id == mismatched.id) mismatched else it }
        val result = LegacyCommandDefinitionBridge(entries = customEntries).analyze().result("feedback.beep")

        assertEquals(CommandBridgeStatus.CONFLICT, result.status)
        assertTrue(result.hasIssue(CommandBridgeIssueType.NATIVE_DEFINITION_PARITY_MISMATCH))
    }

    @Test
    fun `projection commands are never reported as clean runtime commands`() {
        val projections = audit.results.filter { it.sourceEntry.id.startsWith("rem.") }

        assertFalse(projections.isEmpty())
        assertTrue(projections.all { it.hasIssue(CommandBridgeIssueType.PROJECTION_MODELED_AS_COMMAND) })
        assertTrue(projections.all { it.status == CommandBridgeStatus.CONFLICT })
        assertTrue(projections.all { it.migrationClass == CommandMigrationClass.PROJECTION_MIGRATION })
    }

    @Test
    fun `reports contain one csv row per catalog entry and all mandatory sections`() {
        val csv = CommandBridgeReportRenderer.renderCsv(audit)
        val markdown = CommandBridgeReportRenderer.renderMarkdown(audit)

        assertEquals(catalogEntries.size + 1, csv.lineSequence().count { it.isNotBlank() })
        assertTrue(markdown.contains("## Command Family Naming Audit"))
        assertTrue(markdown.contains("## Alias Resolution Collisions"))
        assertTrue(markdown.contains("## Query Return Audit"))
        assertTrue(markdown.contains("## Live Dispatch Audit"))
        assertTrue(markdown.contains("findTemplate` is **NORMALIZABLE** to `templateFind"))
        assertTrue(markdown.contains("`event.start`, `action.wait` and `feedback.beep` are the M1B-2D native commands"))
        assertTrue(markdown.contains("## M1B-3A Naming Normalization"))
        assertTrue(markdown.contains("## M1B-3B Type-Conflict Decisions"))
        assertTrue(markdown.contains("## M1B-3E datastorePut String Contract"))
        assertTrue(markdown.contains("## M1B-3H-A Legacy input.touch Structural Classification"))
        assertTrue(markdown.contains("## M1B-3U Shizuku Availability Convergence"))
        assertTrue(markdown.contains("CONTRACT_DECIDED"))
        assertTrue(markdown.contains("RESOLVED_WITH_LEGACY_ALIAS"))
        assertTrue(csv.lineSequence().first().contains("naming_status"))
        assertTrue(csv.lineSequence().first().contains("type_status"))
        assertTrue(csv.lineSequence().first().contains("expression_status"))
        assertTrue(csv.lineSequence().first().contains("type_classification"))
        assertTrue(csv.lineSequence().first().contains("catalog_role"))
        assertTrue(markdown.contains("VariableReference != Command"))
        assertTrue(markdown.contains("LEGACY_EXPRESSION_ALIAS"))
        assertTrue(markdown.contains("CANONICAL_EXPRESSION_PROJECTION"))
        assertTrue(csv.lineSequence().drop(1).none { it.contains(",,", ignoreCase = false) })
    }

    @Test
    fun `optional report generation writes deterministic artifacts`() {
        val reportDirectory = (
            System.getProperty(REPORT_DIRECTORY_PROPERTY)
                ?: System.getenv(REPORT_DIRECTORY_ENV)
            )?.let(::File) ?: return
        reportDirectory.mkdirs()
        val markdown = CommandBridgeReportRenderer.renderMarkdown(audit)
        val csv = CommandBridgeReportRenderer.renderCsv(audit)
        val queryCsv = QueryReturnContractAudit.renderCsv(audit)
        val queryMarkdown = QueryReturnContractAudit.renderMarkdown(audit)

        File(reportDirectory, "EMSCRIPT_V1_COMMAND_BRIDGE_REPORT.md").writeText(markdown)
        File(reportDirectory, "EMSCRIPT_V1_COMMAND_BRIDGE.csv").writeText(csv)
        File(reportDirectory, "EMSCRIPT_V1_QUERY_RETURN_DECISIONS.csv").writeText(queryCsv)
        File(reportDirectory, "EMSCRIPT_V1_QUERY_RETURN_AUDIT.md").writeText(queryMarkdown)

        assertNotNull(markdown)
        assertNotNull(csv)
        assertNotNull(queryCsv)
        assertNotNull(queryMarkdown)
    }

    private fun LegacyCommandBridgeAudit.result(id: String): CommandBridgeResult =
        results.single { it.sourceEntry.id == id }

    private fun CommandBridgeResult.hasIssue(type: CommandBridgeIssueType): Boolean =
        issues.any { it.type == type }

    companion object {
        private const val REPORT_DIRECTORY_PROPERTY = "commandBridgeReportDir"
        private const val REPORT_DIRECTORY_ENV = "COMMAND_BRIDGE_REPORT_DIR"
    }
}
