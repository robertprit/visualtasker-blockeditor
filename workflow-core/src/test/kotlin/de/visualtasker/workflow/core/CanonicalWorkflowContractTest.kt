package de.visualtasker.workflow.core

import de.visualtasker.workflow.serialization.WorkflowSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class CanonicalWorkflowContractTest {
    @Test
    fun supportedSemanticFixturesRoundTripLosslessly() {
        val original = representativeDocument()

        val serialized = WorkflowSerializer.serialize(original)
        val restored = WorkflowSerializer.deserialize(serialized)

        assertEquals(original, restored)
        assertEquals(serialized, WorkflowSerializer.serialize(restored))
        assertEquals(
            listOf("start", "let", "set", "if", "while", "repeat"),
            restored.rootBlocks.map(BlockId::value),
        )
        assertEquals("score-id", restored.variables.variables.getValue("score-id").id)
        assertEquals("String?", restored.variables.variables.getValue("maybe-id").type)
        assertEquals("List<Number>", restored.variables.variables.getValue("items-id").type)
        assertEquals(ConnectionId("operate.out"), restored.blocks.getValue(BlockId("compare")).valueInputs[0].connection.connectedTo)
    }

    @Test
    fun legacyWorkspaceJsonLoadsThroughNeutralCoreAndResavesCompatibly() {
        val legacyJson =
            """{"id":"legacy","version":0,"blocks":[{"id":"start","node":{"type":"event.start","fields":[],"previous":null,"next":{"id":"start.next","owner":"start","kind":"Next","accepts":[],"provides":null,"connectedTo":null,"slotName":null},"output":null,"valueInputs":[],"statementInputs":[],"collapsed":false,"metadata":[]}}],"rootBlocks":["start"],"rootPositions":[],"variables":[]}"""

        val loaded = WorkflowSerializer.deserialize(legacyJson)
        val saved = WorkflowSerializer.serialize(loaded)
        val reloaded = WorkflowSerializer.deserialize(saved)

        assertEquals(loaded, reloaded)
        assertEquals(BlockId("start"), reloaded.rootBlocks.single())
        assertEquals("event.start", reloaded.blocks.getValue(BlockId("start")).type)
        assertTrue(saved.contains("\"schemaVersion\":1"))
        assertEquals(saved, WorkflowSerializer.serialize(reloaded))
    }

    @Test
    fun workflowCoreDoesNotImportEditorOrComposePackages() {
        val sourceRoot = Path.of("src", "main", "kotlin")
        val forbidden = listOf(
            "de.visualtasker.blockeditor",
            "de.visualtasker.flowchart",
            "androidx.compose",
            "android.graphics",
        )
        val violations = Files.walk(sourceRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .flatMap { path ->
                    val text = Files.readString(path)
                    forbidden.filter(text::contains).map { token -> "$path -> $token" }.stream()
                }
                .toList()
        }

        assertTrue("workflow-core contains forbidden dependencies: $violations", violations.isEmpty())
    }

    private fun representativeDocument(): WorkspaceDocument {
        val blocks = linkedMapOf<BlockId, BlockNode>()

        blocks += statement("start", "event.start", hasPrevious = false, nextTarget = "wait.previous")
        blocks += statement("wait", "action.wait", previousTarget = "start.next")

        blocks += statement(
            "let",
            "variable.declare",
            fields = mapOf(
                "variableId" to FieldValue.Text("score-id"),
                "variableLabel" to FieldValue.Text("score"),
                "type" to FieldValue.Text("Number"),
            ),
        )
        blocks += statement(
            "set",
            "variable.assign",
            fields = mapOf(
                "variableId" to FieldValue.Text("score-id"),
                "variableLabel" to FieldValue.Text("score"),
            ),
            valueInputs = listOf(valueInput("set", "VALUE", "compare.out", setOf("Boolean"))),
        )

        blocks += reporter(
            "operate",
            "logic.operate",
            "Number",
            fields = mapOf("operator" to FieldValue.Text("add")),
            outputTarget = "compare.LEFT",
            valueInputs = listOf(
                valueInput("operate", "LEFT", "number-a.out", setOf("Number")),
                valueInput("operate", "RIGHT", "nested-operate.out", setOf("Number")),
            ),
        )
        blocks += reporter(
            "nested-operate",
            "logic.operate",
            "Number",
            fields = mapOf("operator" to FieldValue.Text("multiply")),
            outputTarget = "operate.RIGHT",
            valueInputs = listOf(
                valueInput("nested-operate", "LEFT", "number-b.out", setOf("Number")),
                valueInput("nested-operate", "RIGHT", "number-c.out", setOf("Number")),
            ),
        )
        blocks += reporter("number-a", "literal.number", "Number", mapOf("value" to FieldValue.Number(1.0)), "operate.LEFT")
        blocks += reporter("number-b", "literal.number", "Number", mapOf("value" to FieldValue.Number(2.0)), "nested-operate.LEFT")
        blocks += reporter("number-c", "literal.number", "Number", mapOf("value" to FieldValue.Number(3.0)), "nested-operate.RIGHT")
        blocks += reporter(
            "compare",
            "logic.compare",
            "Boolean",
            fields = mapOf("operator" to FieldValue.Text("greaterThan")),
            outputTarget = "set.VALUE",
            valueInputs = listOf(
                valueInput("compare", "LEFT", "operate.out", setOf("Number")),
                valueInput("compare", "RIGHT", "number-d.out", setOf("Number")),
            ),
        )
        blocks += reporter("number-d", "literal.number", "Number", mapOf("value" to FieldValue.Number(0.0)), "compare.RIGHT")

        blocks += control(
            id = "if",
            type = "control.ifElseIfElse",
            conditions = listOf(
                valueInput("if", "CONDITION", "bool-true.out", setOf("Boolean")),
                valueInput("if", "ELIF_CONDITION", "command-reporter.out", setOf("Boolean")),
            ),
            branches = listOf(
                statementInput("if", "THEN", "then.previous"),
                statementInput("if", "ELIF", "elseif.previous"),
                statementInput("if", "ELSE", "else.previous"),
            ),
        )
        blocks += reporter("bool-true", "literal.boolean", "Boolean", mapOf("value" to FieldValue.Bool(true)), "if.CONDITION")
        blocks += reporter(
            "command-reporter",
            "emscript.command.ui.textExists",
            "Boolean",
            fields = mapOf("text" to FieldValue.Text("Login")),
            outputTarget = "if.ELIF_CONDITION",
        )
        blocks += statement("then", "action.clickText", previousTarget = "if.THEN")
        blocks += statement("elseif", "debug.log", previousTarget = "if.ELIF")
        blocks += statement("else", "action.screenshot", previousTarget = "if.ELSE")

        blocks += control(
            id = "while",
            type = "control.while",
            conditions = listOf(valueInput("while", "CONDITION", "while-condition.out", setOf("Boolean"))),
            branches = listOf(statementInput("while", "DO", "while-body.previous")),
        )
        blocks += reporter("while-condition", "literal.boolean", "Boolean", mapOf("value" to FieldValue.Bool(false)), "while.CONDITION")
        blocks += statement("while-body", "action.wait", previousTarget = "while.DO")

        blocks += control(
            id = "repeat",
            type = "control.repeat",
            conditions = listOf(valueInput("repeat", "TIMES", "repeat-count.out", setOf("Number"))),
            branches = listOf(statementInput("repeat", "DO", "repeat-body.previous")),
        )
        blocks += reporter("repeat-count", "literal.number", "Number", mapOf("value" to FieldValue.Number(10.0)), "repeat.TIMES")
        blocks += statement("repeat-body", "feedback.beep", previousTarget = "repeat.DO")

        return WorkspaceDocument(
            id = "m2-1-semantic-fixtures",
            version = 17,
            blocks = blocks,
            rootBlocks = listOf("start", "let", "set", "if", "while", "repeat").map(::BlockId),
            rootPositions = mapOf(
                BlockId("start") to WorkspacePoint(10f, 20f),
                BlockId("if") to WorkspacePoint(200f, 300f),
            ),
            variables = VariableRegistry(
                variables = linkedMapOf(
                    "score-id" to VariableDefinition("score-id", "score", "Number", VariableScope.Script, "0"),
                    "maybe-id" to VariableDefinition("maybe-id", "maybeText", "String?", VariableScope.Local),
                    "items-id" to VariableDefinition("items-id", "items", "List<Number>", VariableScope.Global),
                ),
            ),
        )
    }

    private fun statement(
        id: String,
        type: String,
        fields: Map<String, FieldValue> = emptyMap(),
        hasPrevious: Boolean = true,
        previousTarget: String? = null,
        nextTarget: String? = null,
        valueInputs: List<ValueInput> = emptyList(),
    ): Pair<BlockId, BlockNode> {
        val blockId = BlockId(id)
        return blockId to BlockNode(
            id = blockId,
            type = type,
            fields = fields,
            previous = if (hasPrevious) connection(id, "previous", ConnectionKind.Previous, previousTarget) else null,
            next = connection(id, "next", ConnectionKind.Next, nextTarget),
            valueInputs = valueInputs,
        )
    }

    private fun control(
        id: String,
        type: String,
        conditions: List<ValueInput>,
        branches: List<StatementInput>,
    ): Pair<BlockId, BlockNode> {
        val blockId = BlockId(id)
        return blockId to BlockNode(
            id = blockId,
            type = type,
            previous = connection(id, "previous", ConnectionKind.Previous),
            next = connection(id, "next", ConnectionKind.Next),
            valueInputs = conditions,
            statementInputs = branches,
        )
    }

    private fun reporter(
        id: String,
        type: String,
        outputType: String,
        fields: Map<String, FieldValue> = emptyMap(),
        outputTarget: String? = null,
        valueInputs: List<ValueInput> = emptyList(),
    ): Pair<BlockId, BlockNode> {
        val blockId = BlockId(id)
        return blockId to BlockNode(
            id = blockId,
            type = type,
            fields = fields,
            output = connection(
                owner = id,
                suffix = "out",
                kind = ConnectionKind.Output,
                target = outputTarget,
                provides = outputType,
            ),
            valueInputs = valueInputs,
        )
    }

    private fun valueInput(
        owner: String,
        name: String,
        target: String,
        accepts: Set<String>,
    ): ValueInput = ValueInput(
        name = name,
        connection = connection(owner, name, ConnectionKind.ValueInput, target, accepts = accepts, slotName = name),
    )

    private fun statementInput(owner: String, name: String, target: String): StatementInput = StatementInput(
        name = name,
        connection = connection(owner, name, ConnectionKind.StatementInput, target, slotName = name),
    )

    private fun connection(
        owner: String,
        suffix: String,
        kind: ConnectionKind,
        target: String? = null,
        accepts: Set<String> = emptySet(),
        provides: String? = null,
        slotName: String? = null,
    ): Connection = Connection(
        id = ConnectionId("$owner.$suffix"),
        owner = BlockId(owner),
        kind = kind,
        accepts = accepts,
        provides = provides,
        connectedTo = target?.let(::ConnectionId),
        slotName = slotName,
    )
}
