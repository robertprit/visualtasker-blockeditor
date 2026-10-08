package de.visualtasker.workflow.semantics.validation

import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.StaticBlockRegistry
import de.visualtasker.blockeditor.registry.ValueInputDefinition
import de.visualtasker.blockeditor.registry.createNode
import de.visualtasker.workflow.core.BlockId
import de.visualtasker.workflow.core.BlockNode
import de.visualtasker.workflow.core.ConnectionId
import de.visualtasker.workflow.core.WorkspaceDocument
import de.visualtasker.workflow.core.withConnectionUpdated
import de.visualtasker.workflow.semantics.DefaultWorkflowSemanticDefinitions
import de.visualtasker.workflow.semantics.WorkflowElementTypes
import de.visualtasker.workflow.semantics.createNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorGraphTraversalTest {
    @Test
    fun validLinearChainHasNoDiagnostics() {
        val start = node(WorkflowElementTypes.EVENT_START, "start")
        val first = node(WorkflowElementTypes.ACTION_WAIT, "first")
        val second = node(WorkflowElementTypes.ACTION_WAIT, "second")
        val document = documentOf(start, first, second)
            .connect(start.nextId(), first.previousId())
            .connect(first.nextId(), second.previousId())

        assertTrue(Validator.validate(document).errors.toString(), Validator.validate(document).isValid)
    }

    @Test
    fun branchAndNestedReportersAreReachableFromSingleRoot() {
        val start = node(WorkflowElementTypes.EVENT_START, "start")
        val decision = node(WorkflowElementTypes.CONTROL_IF, "decision")
        val compare = node(WorkflowElementTypes.LOGIC_COMPARE, "compare")
        val left = node(WorkflowElementTypes.LITERAL_NUMBER, "left")
        val right = node(WorkflowElementTypes.LITERAL_NUMBER, "right")
        val branch = node(WorkflowElementTypes.ACTION_WAIT, "branch")
        val after = node(WorkflowElementTypes.ACTION_WAIT, "after")
        val document = documentOf(start, decision, compare, left, right, branch, after)
            .connect(start.nextId(), decision.previousId())
            .connect(decision.inputId("CONDITION"), compare.outputId())
            .connect(compare.inputId("LEFT"), left.outputId())
            .connect(compare.inputId("RIGHT"), right.outputId())
            .connect(decision.statementId(WorkflowElementTypes.SLOT_THEN), branch.previousId())
            .connect(decision.nextId(), after.previousId())

        assertEquals(emptyList<ValidationError>(), Validator.validate(document).errors)
    }

    @Test
    fun independentRootsDoNotCreateOrphans() {
        val firstStart = node(WorkflowElementTypes.EVENT_START, "first-start")
        val firstWait = node(WorkflowElementTypes.ACTION_WAIT, "first-wait")
        val secondStart = node(WorkflowElementTypes.EVENT_START, "second-start")
        val secondWait = node(WorkflowElementTypes.ACTION_WAIT, "second-wait")
        val document = documentOf(
            firstStart,
            firstWait,
            secondStart,
            secondWait,
            roots = listOf(firstStart.id, secondStart.id),
        ).connect(firstStart.nextId(), firstWait.previousId())
            .connect(secondStart.nextId(), secondWait.previousId())

        assertEquals(emptyList<ValidationError>(), Validator.validate(document).errors)
    }

    @Test
    fun orphanDiagnosticsFollowBlockOrder() {
        val root = node(WorkflowElementTypes.EVENT_START, "root")
        val firstOrphan = node(WorkflowElementTypes.ACTION_WAIT, "orphan-a")
        val secondOrphan = node(WorkflowElementTypes.ACTION_WAIT, "orphan-b")
        val document = documentOf(root, firstOrphan, secondOrphan)

        assertEquals(
            listOf(OrphanBlock(firstOrphan.id), OrphanBlock(secondOrphan.id)),
            Validator.validate(document).errors,
        )
    }

    @Test
    fun invalidEndpointPrecedesDisconnectedChain() {
        val wait = node(WorkflowElementTypes.ACTION_WAIT, "wait")
        val missing = ConnectionId("missing:next")
        val broken = wait.withConnectionUpdated(wait.previousId()) { it.copy(connectedTo = missing) }
        val document = documentOf(broken)

        assertEquals(
            listOf(
                InvalidConnection(wait.previousId(), missing, "partner connection missing"),
                DisconnectedChain(wait.id),
            ),
            Validator.validate(document).errors,
        )
    }

    @Test
    fun selfCycleMarksCycleMember() {
        val fixture = cycleFixture("self-cycle")
        val sink = fixture.node("sink")
        val cyclic = fixture.node("cycle")
        val document = fixture.document(sink, cyclic)
            .connect(sink.inputId("value"), cyclic.outputId())
            .connect(cyclic.inputId("value"), cyclic.outputId())

        assertEquals(
            listOf(CycleDetected(sink.id), CycleDetected(cyclic.id)),
            Validator.validate(document, fixture.registry).errors,
        )
    }

    @Test
    fun twoNodeCycleMarksMembersAndEveryPredecessor() {
        val fixture = cycleFixture("two-node-cycle")
        val sink = fixture.node("sink")
        val predecessor = fixture.node("predecessor")
        val first = fixture.node("first")
        val second = fixture.node("second")
        val document = fixture.document(sink, predecessor, first, second)
            .connect(sink.inputId("value"), predecessor.outputId())
            .connect(predecessor.inputId("value"), first.outputId())
            .connect(first.inputId("value"), second.outputId())
            .connect(second.inputId("value"), first.outputId())

        assertEquals(
            listOf(
                CycleDetected(sink.id),
                CycleDetected(predecessor.id),
                CycleDetected(first.id),
                CycleDetected(second.id),
            ),
            Validator.validate(document, fixture.registry).errors,
        )
    }

    @Test
    fun nextConnectionCycleTerminatesAndMarksAllReachableMembers() {
        val start = node(WorkflowElementTypes.EVENT_START, "start")
        val first = node(WorkflowElementTypes.ACTION_WAIT, "first")
        val second = node(WorkflowElementTypes.ACTION_WAIT, "second")
        val document = documentOf(start, first, second)
            .connect(start.nextId(), first.previousId())
            .connect(first.nextId(), second.previousId())
            .connect(second.nextId(), first.previousId())

        assertEquals(
            listOf(CycleDetected(start.id), CycleDetected(first.id), CycleDetected(second.id)),
            Validator.validate(document).errors,
        )
    }

    private fun node(type: String, id: String): BlockNode =
        requireNotNull(DefaultWorkflowSemanticDefinitions.semanticDefinition(type)).createNode(BlockId(id))

    private fun documentOf(
        vararg blocks: BlockNode,
        roots: List<BlockId> = listOf(blocks.first().id),
    ): WorkspaceDocument = WorkspaceDocument(
        id = "validator-graph",
        blocks = blocks.associateBy(BlockNode::id),
        rootBlocks = roots,
    )

    private fun WorkspaceDocument.connect(first: ConnectionId, second: ConnectionId): WorkspaceDocument {
        val firstOwner = blocks.values.single { block -> block.allConnectionIds().contains(first) }
        val secondOwner = blocks.values.single { block -> block.allConnectionIds().contains(second) }
        val firstUpdated = firstOwner.withConnectionUpdated(first) { it.copy(connectedTo = second) }
        val blocksAfterFirst = blocks + (firstOwner.id to firstUpdated)
        val currentSecondOwner = blocksAfterFirst.getValue(secondOwner.id)
        val secondUpdated = currentSecondOwner.withConnectionUpdated(second) { it.copy(connectedTo = first) }
        return copy(blocks = blocksAfterFirst + (secondOwner.id to secondUpdated))
    }

    private fun BlockNode.allConnectionIds(): Set<ConnectionId> = buildSet {
        previous?.let { add(it.id) }
        next?.let { add(it.id) }
        output?.let { add(it.id) }
        valueInputs.forEach { add(it.connection.id) }
        statementInputs.forEach { add(it.connection.id) }
    }

    private fun BlockNode.previousId() = requireNotNull(previous).id
    private fun BlockNode.nextId() = requireNotNull(next).id
    private fun BlockNode.outputId() = requireNotNull(output).id
    private fun BlockNode.inputId(name: String) = valueInputs.single { it.name == name }.connection.id
    private fun BlockNode.statementId(name: String) = statementInputs.single { it.name == name }.connection.id

    private fun cycleFixture(prefix: String): CycleFixture {
        val definition = BlockDefinition(
            id = "custom.$prefix",
            label = "Cycle reporter",
            category = "custom",
            hasPrevious = false,
            hasNext = false,
            outputType = "Boolean",
            isReporter = true,
            valueInputs = listOf(
                ValueInputDefinition(
                    name = "value",
                    label = "value",
                    accepts = setOf("Boolean"),
                    required = false,
                ),
            ),
        )
        return CycleFixture(
            definition = definition,
            registry = StaticBlockRegistry(listOf(definition) + DefaultBlockRegistry.allDefinitions()),
        )
    }

    private data class CycleFixture(
        val definition: BlockDefinition,
        val registry: StaticBlockRegistry,
    ) {
        fun node(id: String): BlockNode = definition.createNode(BlockId(id))

        fun document(vararg blocks: BlockNode): WorkspaceDocument = WorkspaceDocument(
            id = "cycle-fixture",
            blocks = blocks.associateBy(BlockNode::id),
            rootBlocks = listOf(blocks.first().id),
        )
    }
}
