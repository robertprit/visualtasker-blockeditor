package de.visualtasker.blockeditor.ir

sealed interface IrExpression {
    data class ScreenContains(val text: String) : IrExpression
    data class And(val left: IrExpression, val right: IrExpression) : IrExpression
    data class Or(val left: IrExpression, val right: IrExpression) : IrExpression
    data class GetVariable(val name: String) : IrExpression
    data class LiteralBoolean(val value: Boolean) : IrExpression
    data class LiteralNumber(val value: Double) : IrExpression
    data class LiteralString(val value: String) : IrExpression
    data class LiteralText(val value: String) : IrExpression
    data class Compare(
        val operator: String,
        val left: IrExpression,
        val right: IrExpression,
    ) : IrExpression
    data class Operate(
        val operator: String,
        val a: IrExpression,
        val b: IrExpression,
        val c: IrExpression? = null,
    ) : IrExpression
}

sealed interface IrStatement {
    data class ClickText(val text: String) : IrStatement
    data class Wait(val milliseconds: Long) : IrStatement
    data class Beep(
        val frequency: Int = 1000,
        val durationMs: Int = 200,
        val volume: Int = 100,
    ) : IrStatement
    data class Vibrate(val pattern: List<Long>) : IrStatement
    data class Log(val message: String) : IrStatement
    data class SetVariable(val name: String, val value: String) : IrStatement
    data class Repeat(val times: Int, val body: List<IrStatement>) : IrStatement
    data class While(val condition: IrExpression, val body: List<IrStatement>) : IrStatement
    data class If(
        val condition: IrExpression,
        val thenBranch: List<IrStatement>,
        val elseIfBranches: List<ElseIfBranch> = emptyList(),
        val elseBranch: List<IrStatement> = emptyList(),
    ) : IrStatement
}

data class ElseIfBranch(
    val condition: IrExpression,
    val body: List<IrStatement>,
)

data class IrScript(
    val name: String,
    val statements: List<IrStatement>,
)

@JvmInline
value class IrGraphNodeId(val value: String)

@JvmInline
value class IrGraphEdgeId(val value: String)

enum class IrGraphNodeKind {
    SCRIPT_ENTRY,
    ACTION,
    ASSIGNMENT,
    DECISION,
    LOOP,
    VALUE,
    UNKNOWN,
}

enum class IrGraphEdgeKind {
    SEQUENCE,
    TRUE_BRANCH,
    FALSE_BRANCH,
    ELSE_IF_BRANCH,
    LOOP_BODY,
    LOOP_EXIT,
    CONDITION,
    DATA_FLOW,
}

enum class IrGraphScopeKind {
    SCRIPT,
    ORPHAN_ROOT,
    UNREACHABLE,
    BRANCH,
    VALUE,
}

enum class IrGraphBranchRole {
    THEN,
    ELSE_IF,
    ELSE,
    LOOP_BODY,
}

enum class IrGraphFacetKind {
    BRANCH_REGION,
    COLLAPSE_GROUP,
    COMMENT_MARKER,
    VARIABLE_BULK,
    FUNCTION_REGION,
}

data class IrGraphBranchRef(
    val id: String,
    val ownerBlockId: String,
    val role: IrGraphBranchRole,
    val index: Int,
    val slotName: String,
)

data class IrGraphSourceRef(
    val workspaceId: String,
    val workspaceVersion: Long,
    val blockId: String? = null,
    val slotName: String? = null,
    val sourceLine: Int? = null,
    val sourceColumn: Int? = null,
    val branch: IrGraphBranchRef? = null,
)

data class IrGraphScope(
    val id: String,
    val kind: IrGraphScopeKind,
    val parentId: String?,
    val label: String,
    val source: IrGraphSourceRef,
)

data class IrGraphBranch(
    val id: String,
    val ownerNodeId: IrGraphNodeId,
    val role: IrGraphBranchRole,
    val index: Int,
    val slotName: String,
    val scopeId: String,
    val conditionNodeId: IrGraphNodeId? = null,
    val bodyEntryNodeId: IrGraphNodeId? = null,
    val source: IrGraphSourceRef,
)

data class IrGraphNode(
    val id: IrGraphNodeId,
    val kind: IrGraphNodeKind,
    val label: String,
    val scopePath: List<String>,
    val source: IrGraphSourceRef,
    val properties: Map<String, String> = emptyMap(),
)

data class IrGraphEdge(
    val id: IrGraphEdgeId,
    val sourceNodeId: IrGraphNodeId,
    val targetNodeId: IrGraphNodeId,
    val kind: IrGraphEdgeKind,
    val label: String? = null,
    val source: IrGraphSourceRef,
)

data class IrGraphFacet(
    val id: String,
    val kind: IrGraphFacetKind,
    val label: String,
    val scopeId: String?,
    val ownerNodeId: IrGraphNodeId?,
    val nodeIds: List<IrGraphNodeId>,
    val source: IrGraphSourceRef,
    val properties: Map<String, String> = emptyMap(),
)

data class IrGraphDiagnostic(
    val code: String,
    val message: String,
    val source: IrGraphSourceRef,
)

data class IrGraph(
    val id: String,
    val sourceRevision: String,
    val entryNodeIds: List<IrGraphNodeId>,
    val nodes: List<IrGraphNode>,
    val edges: List<IrGraphEdge>,
    val diagnostics: List<IrGraphDiagnostic> = emptyList(),
    val scopes: List<IrGraphScope> = emptyList(),
    val branches: List<IrGraphBranch> = emptyList(),
    val facets: List<IrGraphFacet> = emptyList(),
)
