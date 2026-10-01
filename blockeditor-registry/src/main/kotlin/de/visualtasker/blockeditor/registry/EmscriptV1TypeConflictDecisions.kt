package de.visualtasker.blockeditor.registry

enum class TypeConflictClassification {
    TYPE_MAPPING,
    TYPECHECKER_RULE,
    SIGNATURE_DECISION,
    EXPRESSION_MODEL,
    STRUCTURAL_MIGRATION,
    COMMAND_READY,
}

enum class ExpressionProjectionRole {
    NOT_APPLICABLE,
    CANONICAL_PROJECTION,
    LEGACY_ALIAS,
}

enum class CommandTypeStatus {
    CANONICAL,
    CONTRACT_DECIDED,
    RESOLVED,
    CONFLICT,
}

data class CommandTypeConflictDecision(
    val stableId: String,
    val legacyType: String,
    val normativeV1Model: String,
    val classification: TypeConflictClassification,
    val typeResolved: Boolean,
    val implemented: Boolean,
    val evidence: String,
    val remainingBlocker: String,
    val projectionRole: ExpressionProjectionRole = ExpressionProjectionRole.NOT_APPLICABLE,
)

/**
 * M1B-3B contract decisions. These entries describe the normative treatment
 * of historical catalog shapes; they are not additional command definitions.
 */
object EmscriptV1TypeConflictDecisions {
    val ALL: List<CommandTypeConflictDecision> = listOf(
        CommandTypeConflictDecision(
            stableId = "system.datastorePut",
            legacyType = "key: TEXT, value: ANY = String literal",
            normativeV1Model = "datastorePut(key: String, value: String)",
            classification = TypeConflictClassification.TYPE_MAPPING,
            typeResolved = true,
            implemented = true,
            evidence = "Catalog parameters, semantic block inputs, WorkspaceValueTypeSystem and LanguageTypeCompatibility enforce String/String before apply; the runtime datastore remains Map<String, String> and datastoreGet returns String?.",
            remainingBlocker = "None for static type checking and expression transport; NATIVE_V1 migration remains a separate decision.",
        ),
        CommandTypeConflictDecision(
            stableId = "debug.log",
            legacyType = "message: TEXT",
            normativeV1Model = "log(value: Any): Void with deterministic scalar text rendering",
            classification = TypeConflictClassification.TYPE_MAPPING,
            typeResolved = true,
            implemented = true,
            evidence = "The parser accepts an expression and DryRun renders String, Number and Bool deterministically.",
            remainingBlocker = "None for expression transport; NATIVE_V1 migration remains a separate decision.",
        ),
        CommandTypeConflictDecision(
            stableId = "feedback.vibrate",
            legacyType = "pattern: DURATION_MS = 80",
            normativeV1Model = "vibrate(patternMs: Number...): Void; one value is a duration, multiple values alternate delay/vibration phases",
            classification = TypeConflictClassification.SIGNATURE_DECISION,
            typeResolved = true,
            implemented = true,
            evidence = "The catalog exposes one required variadic Number parameter without a language default; parser, Workspace and IR preserve every argument expression losslessly while the existing runtime keeps its one-shot/waveform behavior.",
            remainingBlocker = "None for the V1 signature or expression transport; the single text field remains a legacy visual fallback until a later block mutator slice.",
        ),
        CommandTypeConflictDecision(
            stableId = "variable.set",
            legacyType = "variable: VARIABLE_REF, value: ANY",
            normativeV1Model = "SET value must be assignable to the referenced variable's declared or inferred type; Any only accepts unrestricted values when the variable itself is Any",
            classification = TypeConflictClassification.TYPECHECKER_RULE,
            typeResolved = true,
            implemented = true,
            evidence = "LanguageTypeCompatibility and WorkspaceValueTypeSystem resolve VariableReference types by variableId; importer, SnapEngine, Validator and IR use the same assignment rule.",
            remainingBlocker = "None for assignment type checking; NATIVE_V1 migration remains a separate decision.",
        ),
        CommandTypeConflictDecision(
            stableId = "variable.get",
            legacyType = "VARIABLE_REF reporter returning Any",
            normativeV1Model = "VariableReference expression whose result type is the referenced variable's declared or inferred type",
            classification = TypeConflictClassification.EXPRESSION_MODEL,
            typeResolved = true,
            implemented = true,
            evidence = "Source reads serialize as variable references; legacy variable.get blocks normalize idempotently to variable.reporter.<id> while preserving block, connection and variable identity.",
            remainingBlocker = "None for expression ownership or V1.x legacy workspace migration.",
            projectionRole = ExpressionProjectionRole.LEGACY_ALIAS,
        ),
        CommandTypeConflictDecision(
            stableId = "logic.boolean",
            legacyType = "BOOL reporter command boolean(value)",
            normativeV1Model = "Bool literal expression owned by grammar/AST/IR; logic.boolean and literal.boolean are historical visual projections",
            classification = TypeConflictClassification.EXPRESSION_MODEL,
            typeResolved = true,
            implemented = true,
            evidence = "The parser owns true/false literals; legacy logic.boolean normalizes to literal.boolean and both project to IrExpression.LiteralBoolean.",
            remainingBlocker = "None for expression ownership or V1.x legacy workspace migration.",
            projectionRole = ExpressionProjectionRole.LEGACY_ALIAS,
        ),
        CommandTypeConflictDecision(
            stableId = "literal.boolean",
            legacyType = "BOOL reporter catalog entry",
            normativeV1Model = "Canonical visual projection of the Bool literal expression owned by grammar/AST/IR",
            classification = TypeConflictClassification.EXPRESSION_MODEL,
            typeResolved = true,
            implemented = true,
            evidence = "Source true/false imports as literal.boolean and IR serializes it as a grammar literal, never as boolean(...).",
            remainingBlocker = "None; it remains a visual projection rather than a V1 command.",
            projectionRole = ExpressionProjectionRole.CANONICAL_PROJECTION,
        ),
        CommandTypeConflictDecision(
            stableId = "input.touch",
            legacyType = "sequence: ANY raw payload",
            normativeV1Model = "Legacy structural migration into typed touch primitives, PointerPath, MultiPath or GestureSequence according to recoverable payload semantics",
            classification = TypeConflictClassification.STRUCTURAL_MIGRATION,
            typeResolved = true,
            implemented = false,
            evidence = "Frozen V1 separates timeless Path geometry from PointerPath timing. M1B-3H-A classifies all three repository-backed raw payload forms as PARTIAL while preserving their exact source text.",
            remainingBlocker = "No fixture proves timing, pointer identity, coordinate space, multi-pointer structure or complete gesture boundaries; no single typed replacement is lossless.",
        ),
    )

    private val byStableId = ALL.associateBy(CommandTypeConflictDecision::stableId)

    init {
        require(ALL.size == 8)
        require(byStableId.size == ALL.size)
    }

    fun byStableId(id: String): CommandTypeConflictDecision? = byStableId[id]
}
