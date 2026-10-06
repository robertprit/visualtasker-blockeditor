package de.visualtasker.workflow.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticRelationContractTest {
    @Test
    fun fixturesAThroughGFitTheNeutralContract() {
        val fixtures = listOf(
            sequenceFixture(),
            letExpressionFixture(),
            ifFixture(),
            whileFixture(),
            nestedExpressionFixture(),
            nestedIfFixture(),
            reporterAssignmentFixture(),
        )

        fixtures.forEach { fixture ->
            assertTrue(fixture.name, validate(fixture).isEmpty())
            assertEquals(fixture.entities.size, fixture.entities.map { it.id }.distinct().size)
            assertEquals(fixture.relations.size, fixture.relations.map { it.id }.distinct().size)
        }
    }

    @Test
    fun identityDoesNotDependOnOrderPresentationOrSourceFormatting() {
        val original = ifFixture()
        val reordered = original.copy(
            relations = original.relations.map { relation ->
                if (relation.role.branchRole == SemanticBranchRole.ElseIf) relation.copy(order = 0)
                else if (relation.role.branchRole == SemanticBranchRole.Then) relation.copy(order = 1)
                else relation
            },
            presentation = mapOf("if" to "x=900,y=12,zoom=.4"),
            source = "IF   compare(a,b)\n  A\nELSEIF compare(c,d)\n  B\nEND IF",
        )

        assertEquals(original.entities.map { it.id }.toSet(), reordered.entities.map { it.id }.toSet())
        assertEquals(original.relations.map { it.id }.toSet(), reordered.relations.map { it.id }.toSet())
        assertFalse(original.presentation == reordered.presentation)
        assertFalse(original.source == reordered.source)
    }

    @Test
    fun contractRelationsSerializeLosslesslyWithoutChangingWorkspaceSchema() {
        val relations = nestedExpressionFixture().relations
        val encoded = Json.encodeToString(relations)
        val decoded = Json.decodeFromString<List<SemanticRelation>>(encoded)

        assertEquals(relations, decoded)
    }

    @Test
    fun relationValidationDetectsDuplicateDanglingAndIncompatibleTargets() {
        val fixture = sequenceFixture()
        val duplicate = fixture.copy(relations = fixture.relations + fixture.relations.first())
        val dangling = fixture.copy(
            relations = fixture.relations + relation(
                "dangling",
                SemanticRelationKind.Sequence,
                statement("missing"),
                statement("b"),
                SemanticRelationRoleKind.Next,
            ),
        )
        val incompatible = fixture.copy(
            relations = fixture.relations + relation(
                "bad-expression",
                SemanticRelationKind.Expression,
                statement("a"),
                statement("b"),
                SemanticRelationRoleKind.Value,
            ),
        )

        assertTrue(validate(duplicate).any { it.startsWith("duplicate relation") })
        assertTrue(validate(dangling).any { it.startsWith("dangling source") })
        assertTrue(validate(incompatible).any { it.startsWith("incompatible expression target") })
    }

    @Test
    fun requiredAndOptionalRelationsRemainSchemaRulesNotSyntheticEntities() {
        val fixture = whileFixture()
        val withoutRequiredCondition = fixture.copy(
            relations = fixture.relations.filterNot { it.role.kind == SemanticRelationRoleKind.Condition },
        )
        val withoutOptionalBody = fixture.copy(
            relations = fixture.relations.filterNot { it.role.kind == SemanticRelationRoleKind.Body },
            requiredRoles = fixture.requiredRoles - SemanticRelationRoleKind.Body,
        )

        assertTrue(validate(withoutRequiredCondition).any { it == "missing required role Condition" })
        assertTrue(validate(withoutOptionalBody).isEmpty())
    }

    private fun sequenceFixture(): Fixture {
        val workflow = workflow()
        val a = statement("a")
        val b = statement("b")
        return Fixture(
            name = "A sequence",
            entities = setOf(workflow, a, b),
            relations = listOf(
                relation("root-a", SemanticRelationKind.Containment, workflow, a, SemanticRelationRoleKind.Root, order = 0),
                relation("a-next-b", SemanticRelationKind.Sequence, a, b, SemanticRelationRoleKind.Next),
            ),
        )
    }

    private fun letExpressionFixture(): Fixture {
        val workflow = workflow()
        val declaration = statement("let-x")
        val plus = expression("plus")
        val one = expression("one")
        val two = expression("two")
        val variable = variable("x")
        return Fixture(
            name = "B LET expression",
            entities = setOf(workflow, declaration, plus, one, two, variable),
            relations = listOf(
                relation("root-let", SemanticRelationKind.Containment, workflow, declaration, SemanticRelationRoleKind.Root, order = 0),
                relation("let-value", SemanticRelationKind.Expression, declaration, plus, SemanticRelationRoleKind.Value),
                relation("plus-one", SemanticRelationKind.Expression, plus, one, SemanticRelationRoleKind.Operand, order = 0),
                relation("plus-two", SemanticRelationKind.Expression, plus, two, SemanticRelationRoleKind.Operand, order = 1),
                relation("let-x-ref", SemanticRelationKind.Reference, declaration, variable, SemanticRelationRoleKind.Variable),
            ),
        )
    }

    private fun ifFixture(): Fixture {
        val workflow = workflow()
        val decision = statement("if")
        val thenBranch = branch("if-then")
        val elseIfBranch = branch("if-elseif-a")
        val elseBranch = branch("if-else")
        val condition = expression("if-condition")
        val elseIfCondition = expression("elseif-condition")
        val a = statement("then-a")
        val b = statement("elseif-b")
        val c = statement("else-c")
        return Fixture(
            name = "C IF branches",
            entities = setOf(workflow, decision, thenBranch, elseIfBranch, elseBranch, condition, elseIfCondition, a, b, c),
            relations = listOf(
                relation("root-if", SemanticRelationKind.Containment, workflow, decision, SemanticRelationRoleKind.Root, order = 0),
                branchRelation("if-then-rel", decision, thenBranch, SemanticBranchRole.Then, 0),
                branchRelation("if-elseif-rel", decision, elseIfBranch, SemanticBranchRole.ElseIf, 1),
                branchRelation("if-else-rel", decision, elseBranch, SemanticBranchRole.Else, 2),
                relation("if-condition-rel", SemanticRelationKind.Expression, thenBranch, condition, SemanticRelationRoleKind.Condition),
                relation("elseif-condition-rel", SemanticRelationKind.Expression, elseIfBranch, elseIfCondition, SemanticRelationRoleKind.Condition),
                relation("then-body", SemanticRelationKind.Containment, thenBranch, a, SemanticRelationRoleKind.Body, order = 0),
                relation("elseif-body", SemanticRelationKind.Containment, elseIfBranch, b, SemanticRelationRoleKind.Body, order = 0),
                relation("else-body", SemanticRelationKind.Containment, elseBranch, c, SemanticRelationRoleKind.Body, order = 0),
            ),
        )
    }

    private fun whileFixture(): Fixture {
        val workflow = workflow()
        val loop = statement("while")
        val body = branch("while-body")
        val condition = expression("while-condition")
        val command = statement("body-command")
        return Fixture(
            name = "D WHILE",
            entities = setOf(workflow, loop, body, condition, command),
            relations = listOf(
                relation("root-while", SemanticRelationKind.Containment, workflow, loop, SemanticRelationRoleKind.Root, order = 0),
                branchRelation("while-body-rel", loop, body, SemanticBranchRole.LoopBody, 0),
                relation("while-condition-rel", SemanticRelationKind.Expression, body, condition, SemanticRelationRoleKind.Condition),
                relation("while-command", SemanticRelationKind.Containment, body, command, SemanticRelationRoleKind.Body, order = 0),
            ),
            requiredRoles = setOf(SemanticRelationRoleKind.Condition, SemanticRelationRoleKind.Body),
        )
    }

    private fun nestedExpressionFixture(): Fixture {
        val compare = expression("compare")
        val plus = expression("plus")
        val multiply = expression("multiply")
        val a = expression("a-ref")
        val b = expression("b-ref")
        val c = expression("c-ref")
        val d = expression("d-ref")
        return Fixture(
            name = "E nested expression",
            entities = setOf(compare, plus, multiply, a, b, c, d),
            relations = listOf(
                relation("compare-plus", SemanticRelationKind.Expression, compare, plus, SemanticRelationRoleKind.Operand, order = 0),
                relation("compare-multiply", SemanticRelationKind.Expression, compare, multiply, SemanticRelationRoleKind.Operand, order = 1),
                relation("plus-a", SemanticRelationKind.Expression, plus, a, SemanticRelationRoleKind.Operand, order = 0),
                relation("plus-b", SemanticRelationKind.Expression, plus, b, SemanticRelationRoleKind.Operand, order = 1),
                relation("multiply-c", SemanticRelationKind.Expression, multiply, c, SemanticRelationRoleKind.Operand, order = 0),
                relation("multiply-d", SemanticRelationKind.Expression, multiply, d, SemanticRelationRoleKind.Operand, order = 1),
            ),
        )
    }

    private fun nestedIfFixture(): Fixture {
        val outer = ifFixture()
        val inner = statement("inner-if")
        val innerBranch = branch("inner-then")
        val outerThen = outer.entities.single { it.id == SemanticEntityId("if-then") }
        return outer.copy(
            name = "F nested IF",
            entities = outer.entities + inner + innerBranch,
            relations = outer.relations + listOf(
                relation("outer-contains-inner", SemanticRelationKind.Containment, outerThen, inner, SemanticRelationRoleKind.Body, order = 1),
                branchRelation("inner-then-rel", inner, innerBranch, SemanticBranchRole.Then, 0),
            ),
        )
    }

    private fun reporterAssignmentFixture(): Fixture {
        val workflow = workflow()
        val assignment = statement("set-result")
        val reporter = expression("command-reporter")
        val variable = variable("result")
        return Fixture(
            name = "G command reporter assignment",
            entities = setOf(workflow, assignment, reporter, variable),
            relations = listOf(
                relation("root-set", SemanticRelationKind.Containment, workflow, assignment, SemanticRelationRoleKind.Root, order = 0),
                relation("set-reporter", SemanticRelationKind.Expression, assignment, reporter, SemanticRelationRoleKind.Value),
                relation("set-variable", SemanticRelationKind.Reference, assignment, variable, SemanticRelationRoleKind.Variable),
            ),
        )
    }

    private fun validate(fixture: Fixture): List<String> = buildList {
        val entityIds = fixture.entities.map { it.id }.toSet()
        fixture.relations.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            add("duplicate relation ${it.value}")
        }
        fixture.relations.forEach { relation ->
            if (relation.source.id !in entityIds) add("dangling source ${relation.source.id.value}")
            if (relation.target.id !in entityIds) add("dangling target ${relation.target.id.value}")
            if (relation.kind == SemanticRelationKind.Expression && relation.target.kind != SemanticEntityKind.Expression) {
                add("incompatible expression target ${relation.target.id.value}")
            }
            if (relation.kind == SemanticRelationKind.Sequence && relation.target.kind != SemanticEntityKind.Statement) {
                add("incompatible sequence target ${relation.target.id.value}")
            }
            if (relation.role.kind == SemanticRelationRoleKind.Branch && relation.target.kind != SemanticEntityKind.Branch) {
                add("incompatible branch target ${relation.target.id.value}")
            }
        }
        fixture.requiredRoles.forEach { required ->
            if (fixture.relations.none { it.role.kind == required }) add("missing required role $required")
        }
    }

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

    private fun branchRelation(
        id: String,
        owner: SemanticEntityRef,
        branch: SemanticEntityRef,
        role: SemanticBranchRole,
        order: Int,
    ) = SemanticRelation(
        id = SemanticRelationId(id),
        kind = SemanticRelationKind.Containment,
        source = owner,
        target = branch,
        role = SemanticRelationRole(SemanticRelationRoleKind.Branch, branchRole = role),
        order = order,
    )

    private fun workflow() = entity("workflow", SemanticEntityKind.Workflow)
    private fun statement(id: String) = entity(id, SemanticEntityKind.Statement)
    private fun expression(id: String) = entity(id, SemanticEntityKind.Expression)
    private fun branch(id: String) = entity(id, SemanticEntityKind.Branch)
    private fun variable(id: String) = entity(id, SemanticEntityKind.Variable)
    private fun entity(id: String, kind: SemanticEntityKind) = SemanticEntityRef(SemanticEntityId(id), kind)

    private data class Fixture(
        val name: String,
        val entities: Set<SemanticEntityRef>,
        val relations: List<SemanticRelation>,
        val requiredRoles: Set<SemanticRelationRoleKind> = emptySet(),
        val presentation: Map<String, String> = emptyMap(),
        val source: String = "",
    )
}
