package de.visualtasker.workflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalWorkspaceMutationTest {
    @Test
    fun schemaOneMigrationCreatesCanonicalTruthAndCompatibilityView() {
        val migrated = CanonicalWorkspaceMigration.toCurrent(linearDocument())

        assertTrue(migrated.isValid)
        assertNotNull(migrated.document.canonical)
        assertEquals(listOf(BlockId("a")), migrated.document.rootBlocks)
        assertEquals(ConnectionId("b.previous"), migrated.document.blocks.getValue(BlockId("a")).next?.connectedTo)
    }

    @Test
    fun sequenceDisconnectAndRootPromotionAreOneAtomicCanonicalTransaction() {
        val baseline = CanonicalWorkspaceMigration.toCurrent(linearDocument()).document
        val canonical = baseline.canonical!!
        val sequence = canonical.relations.single { it.kind == SemanticRelationKind.Sequence }
        val workflow = canonical.entities.single { it.ref.kind == SemanticEntityKind.Workflow }.ref
        val target = canonical.entities.single { it.legacySourceId == "b" }.ref
        val root = SemanticRelation(
            id = SemanticRelationId("native:root:b"),
            kind = SemanticRelationKind.Containment,
            source = workflow,
            target = target,
            role = SemanticRelationRole(SemanticRelationRoleKind.Root),
            order = 1,
        )

        val result = CanonicalWorkspaceMutator.apply(
            baseline,
            listOf(CanonicalMutation.RemoveRelation(sequence.id), CanonicalMutation.CreateRelation(root)),
        ) as CanonicalMutationResult.Applied

        assertNull(result.document.blocks.getValue(BlockId("a")).next?.connectedTo)
        assertNull(result.document.blocks.getValue(BlockId("b")).previous?.connectedTo)
        assertEquals(listOf(BlockId("a"), BlockId("b")), result.document.rootBlocks)
        assertTrue(result.document.canonical!!.relations.none { it.id == sequence.id })
    }

    @Test
    fun invalidMutationPublishesNothing() {
        val baseline = CanonicalWorkspaceMigration.toCurrent(linearDocument()).document
        val source = baseline.canonical!!.entities.single { it.legacySourceId == "a" }.ref
        val missing = SemanticEntityRef(SemanticEntityId("block:missing"), SemanticEntityKind.Statement)
        val invalid = SemanticRelation(
            id = SemanticRelationId("native:invalid"),
            kind = SemanticRelationKind.Sequence,
            source = source,
            target = missing,
            role = SemanticRelationRole(SemanticRelationRoleKind.Next),
        )

        val result = CanonicalWorkspaceMutator.apply(
            baseline,
            listOf(CanonicalMutation.CreateRelation(invalid)),
        ) as CanonicalMutationResult.Rejected

        assertSame(baseline, result.document)
        assertTrue(result.issues.any { it.code == "relation.target.missing" })
    }

    @Test
    fun rootReorderChangesOrderWithoutChangingIdentity() {
        val schemaOne = linearDocument().copy(
            blocks = linearDocument().blocks.mapValues { (_, block) -> block.withoutLinks() },
            rootBlocks = listOf(BlockId("a"), BlockId("b")),
        )
        val baseline = CanonicalWorkspaceMigration.toCurrent(schemaOne).document
        val roots = baseline.canonical!!.relations
            .filter { it.role.kind == SemanticRelationRoleKind.Root }
            .sortedBy { it.order }
        val ids = roots.map { it.id }

        val result = CanonicalWorkspaceMutator.apply(
            baseline,
            listOf(
                CanonicalMutation.SetRelationOrder(roots[0].id, 1),
                CanonicalMutation.SetRelationOrder(roots[1].id, 0),
            ),
        ) as CanonicalMutationResult.Applied

        assertEquals(listOf(BlockId("b"), BlockId("a")), result.document.rootBlocks)
        assertEquals(ids.toSet(), result.document.canonical!!.relations.filter { it.role.kind == SemanticRelationRoleKind.Root }.map { it.id }.toSet())
    }

    @Test
    fun expressionReplacementPreservesUnaffectedEntityIdentities() {
        val statement = statement("set", valueInput = "VALUE" to "old.output")
        val oldReporter = reporter("old", "set.VALUE")
        val newReporter = reporter("new", null)
        val schemaOne = WorkspaceDocument(
            id = "expression",
            blocks = mapOf(statement.id to statement, oldReporter.id to oldReporter, newReporter.id to newReporter),
            rootBlocks = listOf(statement.id, newReporter.id),
        )
        val baseline = CanonicalWorkspaceMigration.toCurrent(schemaOne).document
        val canonical = baseline.canonical!!
        val oldExpression = canonical.relations.single { it.kind == SemanticRelationKind.Expression }
        val newRoot = canonical.relations.single { it.role.kind == SemanticRelationRoleKind.Root && it.target.id.value == "block:new" }
        val oldRef = canonical.entities.single { it.ref.id.value == "block:old" }.ref
        val newRef = canonical.entities.single { it.ref.id.value == "block:new" }.ref
        val replacement = oldExpression.copy(
            id = SemanticRelationId("native:expression:set:VALUE:new"),
            target = newRef,
        )
        val oldRoot = SemanticRelation(
            id = SemanticRelationId("native:root:old"),
            kind = SemanticRelationKind.Containment,
            source = canonical.entities.single { it.ref.kind == SemanticEntityKind.Workflow }.ref,
            target = oldRef,
            role = SemanticRelationRole(SemanticRelationRoleKind.Root),
            order = 1,
        )

        val result = CanonicalWorkspaceMutator.apply(
            baseline,
            listOf(
                CanonicalMutation.RemoveRelation(oldExpression.id),
                CanonicalMutation.RemoveRelation(newRoot.id),
                CanonicalMutation.CreateRelation(replacement),
                CanonicalMutation.CreateRelation(oldRoot),
            ),
        ) as CanonicalMutationResult.Applied

        assertEquals(ConnectionId("new.output"), result.document.blocks.getValue(statement.id).valueInputs.single().connection.connectedTo)
        assertNull(result.document.blocks.getValue(oldReporter.id).output?.connectedTo)
        assertEquals(canonical.entities.map { it.ref.id }.toSet(), result.document.canonical!!.entities.map { it.ref.id }.toSet())
    }

    @Test
    fun branchAddReorderAndRemovePreserveSurvivingBranchIdentities() {
        val ifId = BlockId("if")
        fun input(name: String) = StatementInput(
            name,
            Connection(ConnectionId("if.$name"), ifId, ConnectionKind.StatementInput, slotName = name),
        )
        var document = WorkspaceReducer.reduce(
            WorkspaceDocument("branches"),
            WorkspaceAction.CreateBlock(
                BlockNode(
                    id = ifId,
                    type = "control.ifElseIfElse",
                    previous = Connection(ConnectionId("if.previous"), ifId, ConnectionKind.Previous),
                    next = Connection(ConnectionId("if.next"), ifId, ConnectionKind.Next),
                    statementInputs = listOf(input("THEN"), input("ELSE")),
                ),
                0f,
                0f,
            ),
        )
        val originalBranches = document.canonical!!.entities
            .filter { it.ref.kind == SemanticEntityKind.Branch }
            .associateBy { it.legacySourceId }

        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.ReplaceBlockShape(
                document.blocks.getValue(ifId).copy(
                    statementInputs = listOf(input("THEN"), input("ELIF"), input("ELSE")),
                ),
            ),
        )
        val withElseIf = document.canonical!!.entities.filter { it.ref.kind == SemanticEntityKind.Branch }
        assertEquals(3, withElseIf.size)
        assertEquals(originalBranches.getValue("if:THEN").ref.id, withElseIf.single { it.legacySourceId == "if:THEN" }.ref.id)
        assertEquals(originalBranches.getValue("if:ELSE").ref.id, withElseIf.single { it.legacySourceId == "if:ELSE" }.ref.id)
        assertEquals(listOf(0, 1, 2), document.canonical!!.relations.filter { it.role.kind == SemanticRelationRoleKind.Branch }.mapNotNull { it.order }.sorted())

        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.ReplaceBlockShape(
                document.blocks.getValue(ifId).copy(statementInputs = listOf(input("ELSE"), input("THEN"))),
            ),
        )
        val survivors = document.canonical!!.entities.filter { it.ref.kind == SemanticEntityKind.Branch }
        assertEquals(2, survivors.size)
        assertEquals(originalBranches.getValue("if:THEN").ref.id, survivors.single { it.legacySourceId == "if:THEN" }.ref.id)
        assertEquals(originalBranches.getValue("if:ELSE").ref.id, survivors.single { it.legacySourceId == "if:ELSE" }.ref.id)
        assertTrue(survivors.none { it.legacySourceId == "if:ELIF" })
    }

    @Test
    fun variableReferenceIsCreatedAndRemovedThroughCanonicalMutation() {
        val blockId = BlockId("set")
        var document = WorkspaceReducer.reduce(
            WorkspaceDocument("references"),
            WorkspaceAction.CreateVariable(VariableDefinition("score", "score", "Number", VariableScope.Script)),
        )
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.CreateBlock(
                statement("set").copy(
                    type = "variable.set",
                    fields = mapOf("variableId" to FieldValue.Text("score")),
                ),
                0f,
                0f,
            ),
        )

        val reference = document.canonical!!.relations.single { it.kind == SemanticRelationKind.Reference }
        assertEquals("block:${blockId.value}", reference.source.id.value)
        assertEquals("variable:score", reference.target.id.value)

        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.UpdateField(blockId, "variableId", FieldValue.Text("")),
        )
        assertTrue(document.canonical!!.relations.none { it.kind == SemanticRelationKind.Reference })
    }

    @Test
    fun duplicateBranchOrderRejectsWholeTransaction() {
        val ifId = BlockId("if")
        val thenInput = StatementInput(
            "THEN",
            Connection(ConnectionId("if.THEN"), ifId, ConnectionKind.StatementInput, slotName = "THEN"),
        )
        val baseline = WorkspaceReducer.reduce(
            WorkspaceDocument("invalid-order"),
            WorkspaceAction.CreateBlock(
                BlockNode(ifId, "control.if", statementInputs = listOf(thenInput)),
                0f,
                0f,
            ),
        )
        val owner = baseline.canonical!!.entities.single { it.legacySourceId == "if" }.ref
        val duplicate = SemanticEntity(
            SemanticEntityRef(SemanticEntityId("branch:if:DUPLICATE"), SemanticEntityKind.Branch),
            SemanticIdentityOrigin.Native,
            "if:DUPLICATE",
        )
        val relation = SemanticRelation(
            SemanticRelationId("native:branch:if:DUPLICATE"),
            SemanticRelationKind.Containment,
            owner,
            duplicate.ref,
            SemanticRelationRole(SemanticRelationRoleKind.Branch, branchRole = SemanticBranchRole.ElseIf),
            order = 0,
        )

        val result = CanonicalWorkspaceMutator.apply(
            baseline,
            listOf(CanonicalMutation.CreateEntity(duplicate), CanonicalMutation.CreateRelation(relation)),
        ) as CanonicalMutationResult.Rejected

        assertSame(baseline, result.document)
        assertTrue(result.issues.any { it.code == "containment.order.duplicate" })
    }

    @Test
    fun canonicalProjectionOverwritesStaleLegacyEndpointState() {
        val baseline = CanonicalWorkspaceMigration.toCurrent(linearDocument()).document
        val tampered = baseline.copy(
            blocks = baseline.blocks.mapValues { (_, block) -> block.withoutLinks() },
            rootBlocks = listOf(BlockId("b")),
        )

        val restored = CanonicalWorkspaceMigration.toCurrent(tampered)

        assertTrue(restored.isValid)
        assertEquals(baseline.canonical, restored.document.canonical)
        assertEquals(baseline.blocks, restored.document.blocks)
        assertEquals(baseline.rootBlocks, restored.document.rootBlocks)
    }

    private fun linearDocument(): WorkspaceDocument {
        val a = statement("a", nextTarget = "b.previous")
        val b = statement("b", previousTarget = "a.next")
        return WorkspaceDocument(
            id = "linear",
            version = 4,
            blocks = mapOf(a.id to a, b.id to b),
            rootBlocks = listOf(a.id),
        )
    }

    private fun statement(
        id: String,
        previousTarget: String? = null,
        nextTarget: String? = null,
        valueInput: Pair<String, String>? = null,
    ): BlockNode {
        val blockId = BlockId(id)
        return BlockNode(
            id = blockId,
            type = "action.$id",
            previous = Connection(ConnectionId("$id.previous"), blockId, ConnectionKind.Previous, connectedTo = previousTarget?.let(::ConnectionId)),
            next = Connection(ConnectionId("$id.next"), blockId, ConnectionKind.Next, connectedTo = nextTarget?.let(::ConnectionId)),
            valueInputs = valueInput?.let { (name, target) ->
                listOf(ValueInput(name, Connection(ConnectionId("$id.$name"), blockId, ConnectionKind.ValueInput, connectedTo = ConnectionId(target), slotName = name)))
            }.orEmpty(),
        )
    }

    private fun reporter(id: String, outputTarget: String?): BlockNode {
        val blockId = BlockId(id)
        return BlockNode(
            id = blockId,
            type = "literal.number",
            output = Connection(
                id = ConnectionId("$id.output"),
                owner = blockId,
                kind = ConnectionKind.Output,
                provides = "Number",
                connectedTo = outputTarget?.let(::ConnectionId),
            ),
        )
    }

    private fun BlockNode.withoutLinks(): BlockNode = copy(
        previous = previous?.copy(connectedTo = null),
        next = next?.copy(connectedTo = null),
    )
}
