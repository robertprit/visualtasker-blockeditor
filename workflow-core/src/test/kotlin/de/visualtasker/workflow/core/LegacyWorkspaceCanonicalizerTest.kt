package de.visualtasker.workflow.core

import de.visualtasker.workflow.serialization.WorkflowSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyWorkspaceCanonicalizerTest {
    @Test
    fun fixturesAThroughJCanonicalizeDeterministically() {
        val fixtures = listOf(
            linearFixture(),
            letExpressionFixture(),
            nestedExpressionFixture(),
            ifFixture(),
            multipleElseIfFixture(),
            whileFixture(),
            commandReporterFixture(),
            variableReferenceFixture(),
            multipleRootsFixture(),
        )

        fixtures.forEach { fixture ->
            val first = LegacyWorkspaceCanonicalizer.canonicalize(fixture)
            val second = LegacyWorkspaceCanonicalizer.canonicalize(fixture)
            assertTrue("${fixture.id}: ${first.diagnostics}", first.isValid)
            assertEquals(fixture.id, first.document, second.document)
            assertTrue(fixture.id, CanonicalWorkspaceValidator.validate(first.document).isEmpty())
        }
    }

    @Test
    fun schemaOneSaveReloadPreservesDerivedSemanticIdentitiesAndRelations() {
        val original = multipleElseIfFixture()
        val before = LegacyWorkspaceCanonicalizer.canonicalize(original)
        val restored = WorkflowSerializer.deserialize(WorkflowSerializer.serialize(original))
        val after = LegacyWorkspaceCanonicalizer.canonicalize(restored)

        assertTrue(before.isValid)
        assertTrue(after.isValid)
        assertEquals(before.document.entities.map { it.ref.id }, after.document.entities.map { it.ref.id })
        assertEquals(before.document.relations, after.document.relations)
    }

    @Test
    fun expressionTreeUsesExplicitOrderedRelations() {
        val canonical = LegacyWorkspaceCanonicalizer.canonicalize(nestedExpressionFixture()).document
        val expressions = canonical.relations.filter { it.kind == SemanticRelationKind.Expression }
        val compare = SemanticEntityId("block:compare")
        val plus = SemanticEntityId("block:plus")
        val multiply = SemanticEntityId("block:multiply")

        assertEquals(
            listOf(plus, multiply),
            expressions.filter { it.source.id == compare }.sortedBy { it.order }.map { it.target.id },
        )
        assertEquals(listOf(0, 1), expressions.filter { it.source.id == plus }.sortedBy { it.order }.map { it.order })
    }

    @Test
    fun branchesAreEntitiesAndConditionBelongsToItsBranch() {
        val canonical = LegacyWorkspaceCanonicalizer.canonicalize(multipleElseIfFixture()).document
        val branches = canonical.entities.filter { it.ref.kind == SemanticEntityKind.Branch }
        val elseIfBranches = canonical.relations.filter {
            it.role.branchRole == SemanticBranchRole.ElseIf
        }.sortedBy { it.order }

        assertEquals(4, branches.size)
        assertEquals(2, elseIfBranches.size)
        assertEquals("branch:if:ELIF", elseIfBranches[0].target.id.value)
        assertEquals("branch:if:ELIF_1", elseIfBranches[1].target.id.value)
        assertTrue(
            canonical.relations.any {
                it.source.id == elseIfBranches[1].target.id &&
                    it.role.kind == SemanticRelationRoleKind.Condition
            },
        )
    }

    @Test
    fun nativeBranchRemovalAndReorderPreserveRemainingIdentity() {
        val workflow = entity("workflow", SemanticEntityKind.Workflow)
        val decision = entity("if", SemanticEntityKind.Statement)
        val first = entity("branch-a", SemanticEntityKind.Branch)
        val second = entity("branch-b", SemanticEntityKind.Branch)
        val firstRelation = branchRelation("rel-a", decision.ref, first.ref, 1)
        val secondRelation = branchRelation("rel-b", decision.ref, second.ref, 2)
        val before = CanonicalWorkspaceDocument(
            id = "native",
            revision = 1,
            entities = listOf(workflow, decision, first, second),
            relations = listOf(firstRelation, secondRelation),
        )
        val after = before.copy(
            revision = 2,
            entities = before.entities - first,
            relations = listOf(secondRelation.copy(order = 1)),
        )

        assertNotNull(after.entity(second.ref.id))
        assertEquals(second.ref.id, after.relations.single().target.id)
        assertEquals(secondRelation.id, after.relations.single().id)
        assertEquals(1, after.relations.single().order)
    }

    @Test
    fun nativeExpressionReorderPreservesEntityAndRelationIdentity() {
        val parent = entity("operator", SemanticEntityKind.Expression)
        val left = entity("left", SemanticEntityKind.Expression)
        val right = entity("right", SemanticEntityKind.Expression)
        val leftRelation = relation(
            "operator-left",
            SemanticRelationKind.Expression,
            parent.ref,
            left.ref,
            SemanticRelationRoleKind.Operand,
            0,
        )
        val rightRelation = relation(
            "operator-right",
            SemanticRelationKind.Expression,
            parent.ref,
            right.ref,
            SemanticRelationRoleKind.Operand,
            1,
        )
        val reordered = listOf(leftRelation.copy(order = 1), rightRelation.copy(order = 0))

        assertEquals(setOf(left.ref.id, right.ref.id), reordered.map { it.target.id }.toSet())
        assertEquals(setOf(leftRelation.id, rightRelation.id), reordered.map { it.id }.toSet())
    }

    @Test
    fun rootOrderUsesRootListAndIgnoresPresentationCoordinates() {
        val original = multipleRootsFixture()
        val moved = original.copy(
            rootPositions = mapOf(
                BlockId("a") to WorkspacePoint(999f, 999f),
                BlockId("b") to WorkspacePoint(-500f, -500f),
            ),
        )
        val before = LegacyWorkspaceCanonicalizer.canonicalize(original).document
        val after = LegacyWorkspaceCanonicalizer.canonicalize(moved).document

        assertEquals(before.entities, after.entities)
        assertEquals(before.relations, after.relations)
        assertEquals(
            listOf("block:a", "block:b"),
            after.relations.filter { it.role.kind == SemanticRelationRoleKind.Root }
                .sortedBy { it.order }
                .map { it.target.id.value },
        )
    }

    @Test
    fun oneSidedLegacyConnectionIsRecoveredWithDiagnostic() {
        val fixture = linearFixture().let { document ->
            val b = document.blocks.getValue(BlockId("b"))
            document.copy(blocks = document.blocks + (b.id to b.copy(previous = b.previous?.copy(connectedTo = null))))
        }
        val result = LegacyWorkspaceCanonicalizer.canonicalize(fixture)

        assertTrue(result.isValid)
        assertTrue(result.diagnostics.any { it.code == "legacy.connection.one-sided" })
        assertTrue(result.document.relations.any { it.kind == SemanticRelationKind.Sequence })
    }

    @Test
    fun conflictingLegacyConnectionIsInvalidAndNeverSilentlyWins() {
        val fixture = linearFixture().let { document ->
            val b = document.blocks.getValue(BlockId("b"))
            val c = document.blocks.getValue(BlockId("c"))
            document.copy(
                blocks = document.blocks +
                    (b.id to b.copy(previous = b.previous?.copy(connectedTo = c.next?.id))) +
                    (c.id to c.copy(next = c.next?.copy(connectedTo = b.previous?.id))),
            )
        }
        val result = LegacyWorkspaceCanonicalizer.canonicalize(fixture)

        assertFalse(result.isValid)
        assertTrue(result.diagnostics.any { it.code == "legacy.connection.conflict" })
        assertFalse(
            result.document.relations.any {
                it.kind == SemanticRelationKind.Sequence &&
                    it.source.id == SemanticEntityId("block:a") &&
                    it.target.id == SemanticEntityId("block:b")
            },
        )
    }

    @Test
    fun validatorRejectsDuplicateDanglingIncompatibleAndDuplicateSequence() {
        val a = entity("a", SemanticEntityKind.Statement)
        val b = entity("b", SemanticEntityKind.Statement)
        val expression = entity("expression", SemanticEntityKind.Expression)
        val next = relation("next", SemanticRelationKind.Sequence, a.ref, b.ref, SemanticRelationRoleKind.Next)
        val document = CanonicalWorkspaceDocument(
            id = "invalid",
            revision = 1,
            entities = listOf(a, a, b, expression),
            relations = listOf(
                next,
                next,
                relation("next-2", SemanticRelationKind.Sequence, a.ref, expression.ref, SemanticRelationRoleKind.Next),
                relation(
                    "dangling",
                    SemanticRelationKind.Expression,
                    b.ref,
                    SemanticEntityRef(SemanticEntityId("missing"), SemanticEntityKind.Expression),
                    SemanticRelationRoleKind.Value,
                ),
            ),
        )
        val codes = CanonicalWorkspaceValidator.validate(document).map { it.code }.toSet()

        assertTrue("duplicate.entity" in codes)
        assertTrue("duplicate.relation" in codes)
        assertTrue("relation.target.missing" in codes)
        assertTrue("relation.incompatible" in codes)
        assertTrue("sequence.outgoing.duplicate" in codes)
        assertTrue("relation.semantic.duplicate" in codes)
    }

    @Test
    fun negativeOrderIsRejectedByTheContract() {
        val a = entity("a", SemanticEntityKind.Expression)
        val b = entity("b", SemanticEntityKind.Expression)
        assertThrows(IllegalArgumentException::class.java) {
            relation("bad", SemanticRelationKind.Expression, a.ref, b.ref, SemanticRelationRoleKind.Operand, -1)
        }
    }

    private fun linearFixture(): WorkspaceDocument {
        val a = statement("a", previous = null, next = "b.previous")
        val b = statement("b", previous = "a.next", next = "c.previous")
        val c = statement("c", previous = "b.next", next = null)
        return document("linear", listOf(a, b, c), listOf("a"))
    }

    private fun letExpressionFixture(): WorkspaceDocument {
        val let = statement("let", valueInputs = listOf(valueInput("let", "VALUE", "plus.output")))
        val plus = reporter(
            "plus",
            inputs = listOf(valueInput("plus", "LEFT", "one.output"), valueInput("plus", "RIGHT", "two.output")),
            outputTarget = "let.VALUE",
        )
        return document(
            "let",
            listOf(let, plus, reporter("one", outputTarget = "plus.LEFT"), reporter("two", outputTarget = "plus.RIGHT")),
            listOf("let"),
        )
    }

    private fun nestedExpressionFixture(): WorkspaceDocument {
        val compare = reporter(
            "compare",
            inputs = listOf(valueInput("compare", "LEFT", "plus.output"), valueInput("compare", "RIGHT", "multiply.output")),
        )
        val plus = reporter(
            "plus",
            inputs = listOf(valueInput("plus", "LEFT", "a.output"), valueInput("plus", "RIGHT", "b.output")),
            outputTarget = "compare.LEFT",
        )
        val multiply = reporter(
            "multiply",
            inputs = listOf(valueInput("multiply", "LEFT", "c.output"), valueInput("multiply", "RIGHT", "d.output")),
            outputTarget = "compare.RIGHT",
        )
        return document(
            "nested-expression",
            listOf(
                compare,
                plus,
                multiply,
                reporter("a", outputTarget = "plus.LEFT"),
                reporter("b", outputTarget = "plus.RIGHT"),
                reporter("c", outputTarget = "multiply.LEFT"),
                reporter("d", outputTarget = "multiply.RIGHT"),
            ),
            listOf("compare"),
        )
    }

    private fun ifFixture(): WorkspaceDocument = ifDocument(elseIfCount = 1)

    private fun multipleElseIfFixture(): WorkspaceDocument = ifDocument(elseIfCount = 2)

    private fun ifDocument(elseIfCount: Int): WorkspaceDocument {
        val statementSlots = buildList {
            add(statementInput("if", "THEN", "then.previous"))
            repeat(elseIfCount) { index ->
                val suffix = if (index == 0) "" else "_$index"
                add(statementInput("if", "ELIF$suffix", "elif$index.previous"))
            }
            add(statementInput("if", "ELSE", "else.previous"))
        }
        val conditionInputs = buildList {
            add(valueInput("if", "CONDITION", "condition.output"))
            repeat(elseIfCount) { index ->
                val suffix = if (index == 0) "" else "_$index"
                add(valueInput("if", "ELIF_CONDITION$suffix", "elif-condition$index.output"))
            }
        }
        val control = statement("if", valueInputs = conditionInputs, statementInputs = statementSlots)
        val blocks = buildList {
            add(control)
            add(reporter("condition", outputTarget = "if.CONDITION"))
            add(statement("then", previous = "if.THEN"))
            repeat(elseIfCount) { index ->
                val suffix = if (index == 0) "" else "_$index"
                add(reporter("elif-condition$index", outputTarget = "if.ELIF_CONDITION$suffix"))
                add(statement("elif$index", previous = "if.ELIF$suffix"))
            }
            add(statement("else", previous = "if.ELSE"))
        }
        return document("if-$elseIfCount", blocks, listOf("if"))
    }

    private fun whileFixture(): WorkspaceDocument {
        val loop = statement(
            "while",
            valueInputs = listOf(valueInput("while", "CONDITION", "condition.output")),
            statementInputs = listOf(statementInput("while", "BODY", "body.previous")),
        )
        return document(
            "while",
            listOf(loop, reporter("condition", outputTarget = "while.CONDITION"), statement("body", previous = "while.BODY")),
            listOf("while"),
        )
    }

    private fun commandReporterFixture(): WorkspaceDocument {
        val log = statement("log", valueInputs = listOf(valueInput("log", "VALUE", "query.output")))
        return document("command-reporter", listOf(log, reporter("query", outputTarget = "log.VALUE")), listOf("log"))
    }

    private fun variableReferenceFixture(): WorkspaceDocument {
        val variable = VariableDefinition("score-id", "score", "Number", VariableScope.Script)
        val reporter = reporter("score-ref").copy(
            fields = mapOf("variableId" to FieldValue.Text("score-id")),
        )
        return document("variable", listOf(reporter), listOf("score-ref")).copy(
            variables = VariableRegistry(mapOf(variable.id to variable)),
        )
    }

    private fun multipleRootsFixture(): WorkspaceDocument = document(
        "roots",
        listOf(statement("a"), statement("b")),
        listOf("a", "b"),
    ).copy(
        rootPositions = mapOf(
            BlockId("a") to WorkspacePoint(0f, 500f),
            BlockId("b") to WorkspacePoint(0f, 0f),
        ),
    )

    private fun document(id: String, blocks: List<BlockNode>, roots: List<String>) = WorkspaceDocument(
        id = id,
        version = 7,
        blocks = blocks.associateBy(BlockNode::id),
        rootBlocks = roots.map(::BlockId),
    )

    private fun statement(
        id: String,
        previous: String? = null,
        next: String? = null,
        valueInputs: List<ValueInput> = emptyList(),
        statementInputs: List<StatementInput> = emptyList(),
    ) = BlockNode(
        id = BlockId(id),
        type = "statement.$id",
        previous = connection(id, "previous", ConnectionKind.Previous, previous),
        next = connection(id, "next", ConnectionKind.Next, next),
        valueInputs = valueInputs,
        statementInputs = statementInputs,
    )

    private fun reporter(
        id: String,
        inputs: List<ValueInput> = emptyList(),
        outputTarget: String? = null,
    ) = BlockNode(
        id = BlockId(id),
        type = "expression.$id",
        output = connection(id, "output", ConnectionKind.Output, outputTarget),
        valueInputs = inputs,
    )

    private fun valueInput(owner: String, name: String, target: String) = ValueInput(
        name,
        connection(owner, name, ConnectionKind.ValueInput, target, slotName = name),
    )

    private fun statementInput(owner: String, name: String, target: String) = StatementInput(
        name,
        connection(owner, name, ConnectionKind.StatementInput, target, slotName = name),
    )

    private fun connection(
        owner: String,
        suffix: String,
        kind: ConnectionKind,
        target: String? = null,
        slotName: String? = null,
    ) = Connection(
        id = ConnectionId("$owner.$suffix"),
        owner = BlockId(owner),
        kind = kind,
        connectedTo = target?.let(::ConnectionId),
        slotName = slotName,
    )

    private fun entity(id: String, kind: SemanticEntityKind) = SemanticEntity(
        ref = SemanticEntityRef(SemanticEntityId(id), kind),
        origin = SemanticIdentityOrigin.Native,
    )

    private fun branchRelation(
        id: String,
        owner: SemanticEntityRef,
        target: SemanticEntityRef,
        order: Int,
    ) = SemanticRelation(
        id = SemanticRelationId(id),
        kind = SemanticRelationKind.Containment,
        source = owner,
        target = target,
        role = SemanticRelationRole(SemanticRelationRoleKind.Branch, branchRole = SemanticBranchRole.ElseIf),
        order = order,
    )

    private fun relation(
        id: String,
        kind: SemanticRelationKind,
        source: SemanticEntityRef,
        target: SemanticEntityRef,
        role: SemanticRelationRoleKind,
        order: Int? = null,
    ) = SemanticRelation(
        id = SemanticRelationId(id),
        kind = kind,
        source = source,
        target = target,
        role = SemanticRelationRole(role),
        order = order,
    )
}
