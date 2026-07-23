package de.visualtasker.blockeditor.domain

data class Connection(
    val id: ConnectionId,
    val owner: BlockId,
    val kind: ConnectionKind,
    val accepts: Set<String> = emptySet(),
    val provides: String? = null,
    val connectedTo: ConnectionId? = null,
    val slotName: String? = null,
)

data class ValueInput(
    val name: String,
    val connection: Connection,
)

data class StatementInput(
    val name: String,
    val connection: Connection,
)

data class BlockNode(
    val id: BlockId,
    val type: String,
    val fields: Map<String, FieldValue> = emptyMap(),
    val previous: Connection? = null,
    val next: Connection? = null,
    val output: Connection? = null,
    val valueInputs: List<ValueInput> = emptyList(),
    val statementInputs: List<StatementInput> = emptyList(),
    val collapsed: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
)

enum class VariableScope {
    Global,
    Script,
    Local,
}

data class VariableDefinition(
    val id: String,
    val name: String,
    val type: String,
    val scope: VariableScope,
    val defaultValue: String? = null,
)

data class VariableRegistry(
    val variables: Map<String, VariableDefinition> = emptyMap(),
)

data class WorkspaceDocument(
    val id: String,
    val version: Long = 0L,
    val blocks: Map<BlockId, BlockNode> = emptyMap(),
    val rootBlocks: List<BlockId> = emptyList(),
    val rootPositions: Map<BlockId, WorkspacePoint> = emptyMap(),
    val variables: VariableRegistry = VariableRegistry(),
)

const val META_ROOT_X = "rootX"
const val META_ROOT_Y = "rootY"

data class WorkspacePoint(
    val x: Float,
    val y: Float,
) {
    fun toOffset(): Offset2 = Offset2(x, y)
}

fun BlockNode.rootOffset(): Offset2? {
    val x = metadata[META_ROOT_X]?.toFloatOrNull() ?: return null
    val y = metadata[META_ROOT_Y]?.toFloatOrNull() ?: return null
    return Offset2(x, y)
}

fun BlockNode.withRootOffset(x: Float, y: Float): BlockNode =
    copy(metadata = metadata + (META_ROOT_X to x.toString()) + (META_ROOT_Y to y.toString()))

fun BlockNode.withoutLegacyRootOffset(): BlockNode =
    copy(metadata = metadata - META_ROOT_X - META_ROOT_Y)

fun WorkspaceDocument.rootOffset(blockId: BlockId): Offset2? =
    rootPositions[blockId]?.toOffset() ?: blocks[blockId]?.rootOffset()

fun WorkspaceDocument.withRootOffset(blockId: BlockId, x: Float, y: Float): WorkspaceDocument =
    copy(
        blocks = blocks[blockId]?.let { block ->
            blocks + (blockId to block.withoutLegacyRootOffset())
        } ?: blocks,
        rootPositions = rootPositions + (blockId to WorkspacePoint(x, y)),
    )

fun WorkspaceDocument.withoutRootOffsets(blockIds: Set<BlockId>): WorkspaceDocument =
    copy(
        blocks = blocks.mapValues { (id, block) ->
            if (id in blockIds) block.withoutLegacyRootOffset() else block
        },
        rootPositions = rootPositions - blockIds,
    )

fun BlockNode.allConnections(): List<Connection> = buildList {
    previous?.let { add(it) }
    next?.let { add(it) }
    output?.let { add(it) }
    valueInputs.forEach { add(it.connection) }
    statementInputs.forEach { add(it.connection) }
}

fun BlockNode.withConnectionUpdated(connectionId: ConnectionId, transform: (Connection) -> Connection): BlockNode {
    fun Connection.updateIfMatch(): Connection = if (id == connectionId) transform(this) else this
    return copy(
        previous = previous?.updateIfMatch(),
        next = next?.updateIfMatch(),
        output = output?.updateIfMatch(),
        valueInputs = valueInputs.map { input ->
            input.copy(connection = input.connection.updateIfMatch())
        },
        statementInputs = statementInputs.map { input ->
            input.copy(connection = input.connection.updateIfMatch())
        },
    )
}
