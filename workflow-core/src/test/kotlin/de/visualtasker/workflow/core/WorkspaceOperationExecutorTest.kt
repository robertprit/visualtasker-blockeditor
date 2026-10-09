package de.visualtasker.workflow.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceOperationExecutorTest {
    @Test
    fun createAndDeleteEntityUseCanonicalMutationEngine() {
        val document = fixture()
        val entity = SemanticEntity(
            SemanticEntityRef(SemanticEntityId("branch:detached"), SemanticEntityKind.Branch),
            SemanticIdentityOrigin.Native,
        )

        val created = success(document, WorkspaceOperation.CreateEntity(id("create"), entity))
        assertTrue(created.document.canonical!!.entities.contains(entity))
        assertEquals(document.version + 1, created.resultingRevision)

        val deleted = success(created.document, WorkspaceOperation.DeleteEntity(id("delete"), entity.ref.id))
        assertFalse(deleted.document.canonical!!.entities.contains(entity))
    }

    @Test
    fun createRemoveAndOrderRelationsAreNeutralAndStable() {
        val document = fixture()
        val canonical = document.canonical!!
        val wait = canonical.entity(SemanticEntityId("block:wait"))!!.ref
        val spare = canonical.entity(SemanticEntityId("block:spare"))!!.ref
        val relation = SemanticRelation(
            id = SemanticRelationId("operation:sequence:wait-spare"),
            kind = SemanticRelationKind.Sequence,
            source = wait,
            target = spare,
            role = SemanticRelationRole(SemanticRelationRoleKind.Next),
        )

        val inserted = success(document, WorkspaceOperation.CreateRelation(id("insert"), relation))
        assertTrue(inserted.document.canonical!!.relations.contains(relation))
        val removed = success(inserted.document, WorkspaceOperation.RemoveRelation(id("remove"), relation.id))
        assertFalse(removed.document.canonical!!.relations.any { it.id == relation.id })

        val spareRoot = document.canonical.relations.single {
            it.role.kind == SemanticRelationRoleKind.Root && it.target.id == spare.id
        }
        val startRoot = document.canonical.relations.single {
            it.role.kind == SemanticRelationRoleKind.Root && it.target.id == SemanticEntityId("block:start")
        }
        val reordered = WorkspaceOperationExecutor.execute(
            document,
            WorkspaceTransaction(
                transactionId = id("reorder-roots"),
                operations = listOf(
                    WorkspaceOperation.SetOrder(id("order-spare"), spareRoot.id, 0),
                    WorkspaceOperation.SetOrder(id("order-start"), startRoot.id, 1),
                ),
            ),
        ) as WorkspaceOperationResult.Success
        assertEquals(0, reordered.document.canonical!!.relations.single { it.id == spareRoot.id }.order)
    }

    @Test
    fun stringNumberBoolAndNullablePropertiesAreTyped() {
        val document = fixture()
        val click = SemanticEntityId("block:click")
        val wait = SemanticEntityId("block:wait")

        val text = success(document, setProperty("text", click, "text", WorkspacePropertyValue.Text("Continue")))
        assertEquals(FieldValue.Text("Continue"), text.document.blocks.getValue(BlockId("click")).fields["text"])

        val number = success(text.document, setProperty("number", wait, "duration", WorkspacePropertyValue.Number(750.0)))
        assertEquals(FieldValue.Number(750.0), number.document.blocks.getValue(BlockId("wait")).fields["duration"])

        val bool = success(number.document, setProperty("bool", click, "active", WorkspacePropertyValue.Bool(false)))
        assertEquals(FieldValue.Bool(false), bool.document.blocks.getValue(BlockId("click")).fields["active"])

        val nullable = success(bool.document, setProperty("null", click, "note", WorkspacePropertyValue.Null))
        assertFalse(nullable.document.blocks.getValue(BlockId("click")).fields.containsKey("note"))
    }

    @Test
    fun invalidTargetPropertyAndTypeReturnTypedDiagnosticsWithoutMutation() {
        val document = fixture()
        val missingTarget = failure(
            document,
            setProperty("missing-target", SemanticEntityId("block:missing"), "text", WorkspacePropertyValue.Text("x")),
        )
        assertEquals(WorkspaceOperationDiagnosticCode.ENTITY_NOT_FOUND, missingTarget.diagnostic.code)
        assertEquals(document, missingTarget.document)

        val missingProperty = failure(
            document,
            setProperty("missing-property", SemanticEntityId("block:click"), "doesNotExist", WorkspacePropertyValue.Text("x")),
        )
        assertEquals(WorkspaceOperationDiagnosticCode.PROPERTY_NOT_FOUND, missingProperty.diagnostic.code)
        assertEquals(document, missingProperty.document)

        val invalidType = failure(
            document,
            setProperty("wrong-type", SemanticEntityId("block:wait"), "duration", WorkspacePropertyValue.Text("fast")),
        )
        assertEquals(WorkspaceOperationDiagnosticCode.INVALID_PROPERTY_TYPE, invalidType.diagnostic.code)
        assertEquals(document, invalidType.document)
    }

    @Test
    fun threeOperationTransactionPublishesOneRevision() {
        val document = fixture()
        val transaction = WorkspaceTransaction(
            transactionId = id("valid-transaction"),
            expectedRevision = document.version,
            operations = listOf(
                setProperty("one", SemanticEntityId("block:click"), "text", WorkspacePropertyValue.Text("Continue")),
                setProperty("two", SemanticEntityId("block:wait"), "duration", WorkspacePropertyValue.Number(750.0)),
                setProperty("three", SemanticEntityId("block:click"), "active", WorkspacePropertyValue.Bool(false)),
            ),
        )

        val result = WorkspaceOperationExecutor.execute(document, transaction) as WorkspaceOperationResult.Success

        assertEquals(document.version + 1, result.resultingRevision)
        assertEquals(FieldValue.Text("Continue"), result.document.blocks.getValue(BlockId("click")).fields["text"])
        assertEquals(FieldValue.Number(750.0), result.document.blocks.getValue(BlockId("wait")).fields["duration"])
        assertEquals(setOf(SemanticEntityId("block:click"), SemanticEntityId("block:wait")), result.affectedEntityIds)
    }

    @Test
    fun failedSecondOperationRollsBackWholeTransaction() {
        val document = fixture()
        val transaction = WorkspaceTransaction(
            transactionId = id("rollback"),
            operations = listOf(
                setProperty("one", SemanticEntityId("block:click"), "text", WorkspacePropertyValue.Text("Changed")),
                setProperty("two", SemanticEntityId("block:wait"), "missing", WorkspacePropertyValue.Number(1.0)),
                setProperty("three", SemanticEntityId("block:wait"), "duration", WorkspacePropertyValue.Number(900.0)),
            ),
        )

        val result = WorkspaceOperationExecutor.execute(document, transaction) as WorkspaceOperationResult.Failure

        assertEquals(document, result.document)
        assertEquals(id("two"), result.diagnostic.operationId)
        assertEquals(WorkspaceOperationDiagnosticCode.PROPERTY_NOT_FOUND, result.diagnostic.code)
    }

    @Test
    fun stagedSourcePayloadPublishesOnlyWithSuccessfulTransaction() {
        val document = fixture()
        val clickId = BlockId("click")
        val stagedClick = document.blocks.getValue(clickId).copy(
            fields = document.blocks.getValue(clickId).fields +
                ("text" to FieldValue.Text("Prepared")) +
                ("displayLabel" to FieldValue.Text("Preserved label")),
        )
        val staged = document.copy(blocks = document.blocks + (clickId to stagedClick))
        val valid = WorkspaceTransaction(
            transactionId = id("staged-valid"),
            expectedRevision = document.version,
            operations = listOf(
                setProperty("staged-text", SemanticEntityId("block:click"), "text", WorkspacePropertyValue.Text("Prepared")),
            ),
        )

        val success = WorkspaceOperationExecutor.executeWithStagedPayload(document, valid, staged)
            as WorkspaceOperationResult.Success

        assertEquals(document.version + 1, success.resultingRevision)
        assertEquals(FieldValue.Text("Prepared"), success.document.blocks.getValue(clickId).fields["text"])
        assertEquals(FieldValue.Text("Preserved label"), success.document.blocks.getValue(clickId).fields["displayLabel"])

        val invalid = WorkspaceTransaction(
            transactionId = id("staged-invalid"),
            expectedRevision = document.version,
            operations = listOf(
                setProperty("missing-property", SemanticEntityId("block:click"), "missing", WorkspacePropertyValue.Text("x")),
            ),
        )
        val failure = WorkspaceOperationExecutor.executeWithStagedPayload(document, invalid, staged)
            as WorkspaceOperationResult.Failure

        assertEquals(document, failure.document)
        assertEquals(WorkspaceOperationDiagnosticCode.PROPERTY_NOT_FOUND, failure.diagnostic.code)
    }

    @Test
    fun payloadReplacementRequiresChangedStagedPayload() {
        val document = fixture()
        val operation = WorkspaceOperation.ReplaceEntityPayload(
            id("replace-payload"),
            SemanticEntityId("block:click"),
        )

        val withoutPayload = WorkspaceOperationExecutor.execute(document, operation) as WorkspaceOperationResult.Failure
        assertEquals(WorkspaceOperationDiagnosticCode.VALIDATION_FAILED, withoutPayload.diagnostic.code)
        assertEquals(document, withoutPayload.document)

        val unchanged = WorkspaceOperationExecutor.executeWithStagedPayload(
            document,
            WorkspaceTransaction(id("unchanged-payload"), listOf(operation)),
            document,
        ) as WorkspaceOperationResult.Failure
        assertEquals(WorkspaceOperationDiagnosticCode.VALIDATION_FAILED, unchanged.diagnostic.code)

        val clickId = BlockId("click")
        val changedBlock = document.blocks.getValue(clickId).copy(type = "alternate-click")
        val staged = document.copy(blocks = document.blocks + (clickId to changedBlock))
        val changed = WorkspaceOperationExecutor.executeWithStagedPayload(
            document,
            WorkspaceTransaction(id("changed-payload"), listOf(operation)),
            staged,
        ) as WorkspaceOperationResult.Success
        assertEquals(document.version + 1, changed.resultingRevision)
        assertEquals("alternate-click", changed.document.blocks.getValue(clickId).type)
    }

    @Test
    fun identicalOperationReplayIsDeterministic() {
        val firstDocument = fixture()
        val secondDocument = fixture()
        val operation = setProperty(
            "deterministic",
            SemanticEntityId("block:wait"),
            "duration",
            WorkspacePropertyValue.Number(750.0),
        )

        val first = success(firstDocument, operation)
        val second = success(secondDocument, operation)

        assertEquals(first.document, second.document)
        assertEquals(first.affectedEntityIds, second.affectedEntityIds)
        assertEquals(first.affectedRelationIds, second.affectedRelationIds)
    }

    @Test
    fun transactionContractIsSerializableForFutureTransport() {
        val transaction = WorkspaceTransaction(
            transactionId = id("serialized"),
            expectedRevision = 12,
            operations = listOf(
                setProperty("property", SemanticEntityId("block:wait"), "duration", WorkspacePropertyValue.Number(750.0)),
            ),
        )

        val encoded = Json.encodeToString(transaction)
        val decoded = Json.decodeFromString<WorkspaceTransaction>(encoded)

        assertEquals(transaction, decoded)
    }

    @Test
    fun expectedRevisionAndDuplicateOperationIdsAreRejected() {
        val document = fixture()
        val stale = WorkspaceOperationExecutor.execute(
            document,
            WorkspaceTransaction(id("stale"), emptyList(), expectedRevision = document.version - 1),
        ) as WorkspaceOperationResult.Failure
        assertEquals(WorkspaceOperationDiagnosticCode.REVISION_MISMATCH, stale.diagnostic.code)

        val operation = setProperty("duplicate", SemanticEntityId("block:click"), "text", WorkspacePropertyValue.Text("x"))
        val duplicate = WorkspaceOperationExecutor.execute(
            document,
            WorkspaceTransaction(id("duplicate-transaction"), listOf(operation, operation)),
        ) as WorkspaceOperationResult.Failure
        assertEquals(WorkspaceOperationDiagnosticCode.DUPLICATE_ID, duplicate.diagnostic.code)
        assertEquals(document, duplicate.document)
    }

    @Test
    fun expressionAndStatementInsertionCanBeRepresentedStructurally() {
        val document = fixture()
        val condition = document.canonical!!.entity(SemanticEntityId("block:condition"))!!.ref
        val bool = document.canonical.entity(SemanticEntityId("block:bool"))!!.ref
        val expression = SemanticRelation(
            id = SemanticRelationId("operation:expression:condition"),
            kind = SemanticRelationKind.Expression,
            source = condition,
            target = bool,
            role = SemanticRelationRole(SemanticRelationRoleKind.Condition),
        )

        val attached = success(document, WorkspaceOperation.CreateRelation(id("attach-expression"), expression))
        assertTrue(attached.document.canonical!!.relations.contains(expression))

        val wait = attached.document.canonical.entity(SemanticEntityId("block:wait"))!!.ref
        val spare = attached.document.canonical.entity(SemanticEntityId("block:spare"))!!.ref
        val insertion = SemanticRelation(
            id = SemanticRelationId("operation:statement:insert"),
            kind = SemanticRelationKind.Sequence,
            source = wait,
            target = spare,
            role = SemanticRelationRole(SemanticRelationRoleKind.Next),
        )
        val inserted = success(attached.document, WorkspaceOperation.CreateRelation(id("insert-statement"), insertion))
        val removed = success(inserted.document, WorkspaceOperation.RemoveRelation(id("remove-statement"), insertion.id))
        assertNotEquals(inserted.document, removed.document)
        assertFalse(removed.document.canonical!!.relations.any { it.id == insertion.id })
    }

    @Test
    fun requiredStructuralDiagnosticsAreMachineReadable() {
        val document = fixture()
        val duplicateEntity = document.canonical!!.entity(SemanticEntityId("block:click"))!!
        assertFailureCode(
            document,
            WorkspaceOperation.CreateEntity(id("duplicate-entity"), duplicateEntity),
            WorkspaceOperationDiagnosticCode.DUPLICATE_ID,
        )
        assertFailureCode(
            document,
            WorkspaceOperation.RemoveRelation(id("missing-relation"), SemanticRelationId("missing")),
            WorkspaceOperationDiagnosticCode.RELATION_NOT_FOUND,
        )
        val spareRoot = document.canonical.relations.single {
            it.role.kind == SemanticRelationRoleKind.Root && it.target.id == SemanticEntityId("block:spare")
        }
        assertFailureCode(
            document,
            WorkspaceOperation.SetOrder(id("invalid-order"), spareRoot.id, -1),
            WorkspaceOperationDiagnosticCode.INVALID_ORDER,
        )
        val click = document.canonical.entity(SemanticEntityId("block:click"))!!.ref
        val missing = SemanticEntityRef(SemanticEntityId("block:missing"), SemanticEntityKind.Statement)
        assertFailureCode(
            document,
            WorkspaceOperation.CreateRelation(
                id("dangling"),
                SemanticRelation(
                    SemanticRelationId("dangling"),
                    SemanticRelationKind.Sequence,
                    click,
                    missing,
                    SemanticRelationRole(SemanticRelationRoleKind.Next),
                ),
            ),
            WorkspaceOperationDiagnosticCode.DANGLING_REFERENCE,
        )
        val bool = document.canonical.entity(SemanticEntityId("block:bool"))!!.ref
        assertFailureCode(
            document,
            WorkspaceOperation.CreateRelation(
                id("invalid-relation"),
                SemanticRelation(
                    SemanticRelationId("invalid-relation"),
                    SemanticRelationKind.Sequence,
                    bool,
                    click,
                    SemanticRelationRole(SemanticRelationRoleKind.Next),
                ),
            ),
            WorkspaceOperationDiagnosticCode.INVALID_RELATION,
        )
    }

    @Test
    fun deletingStatementEntityRemovesItsCompatibilityPayload() {
        val document = fixture()
        val result = success(
            document,
            WorkspaceOperation.DeleteEntity(id("delete-spare"), SemanticEntityId("block:spare")),
        )

        assertFalse(result.document.blocks.containsKey(BlockId("spare")))
        assertFalse(result.document.canonical!!.entities.any { it.ref.id == SemanticEntityId("block:spare") })
    }

    @Test
    fun schemaRejectsWrongOwnerTypeAndNullWithoutChangingRevision() {
        val document = fixture()
        val variable = SemanticEntityId("variable:counter")
        val wait = SemanticEntityId("block:wait")
        val click = SemanticEntityId("block:click")
        val cases = listOf(
            setProperty("wrong-owner", variable, "duration", WorkspacePropertyValue.Number(1.0)) to
                WorkspaceOperationDiagnosticCode.PROPERTY_NOT_FOUND,
            setProperty("wrong-type-schema", wait, "duration", WorkspacePropertyValue.Text("fast")) to
                WorkspaceOperationDiagnosticCode.INVALID_PROPERTY_TYPE,
            setProperty("null-required", click, "text", WorkspacePropertyValue.Null) to
                WorkspaceOperationDiagnosticCode.INVALID_PROPERTY_TYPE,
        )

        cases.forEach { (operation, expectedCode) ->
            val result = failure(document, operation)
            assertEquals(expectedCode, result.diagnostic.code)
            assertEquals(document.version, result.document.version)
            assertEquals(document, result.document)
        }
    }

    @Test
    fun schemaRejectsReadOnlyStructuralAndConstraintProperties() {
        val document = fixture()
        val click = SemanticEntityId("block:click")
        val variable = SemanticEntityId("variable:counter")

        assertFailureCode(
            document,
            setProperty("readonly", click, "entityId", WorkspacePropertyValue.Text("other")),
            WorkspaceOperationDiagnosticCode.PROPERTY_NOT_WRITABLE,
        )
        assertFailureCode(
            document,
            setProperty("structural", click, "relationOrder", WorkspacePropertyValue.Number(4.0)),
            WorkspaceOperationDiagnosticCode.STRUCTURAL_PROPERTY_REQUIRED,
        )
        assertFailureCode(
            document,
            setProperty("constraint", variable, "name", WorkspacePropertyValue.Text(" ")),
            WorkspaceOperationDiagnosticCode.PROPERTY_CONSTRAINT_VIOLATION,
        )
    }

    @Test
    fun nullableAndDisplayLabelPropertiesAreDiscoverableAndWritable() {
        val document = fixture()
        val click = SemanticEntityId("block:click")
        val properties = CanonicalSemanticPropertySchema.propertiesFor(document, click)
        assertEquals(properties, CanonicalSemanticPropertySchema.propertiesFor(document, click))
        assertTrue(properties.single { it.propertyId.value == "note" }.nullable)
        assertTrue(properties.single { it.propertyId.value == "displayLabel" }.writable)

        val withoutNote = success(document, setProperty("nullable", click, "note", WorkspacePropertyValue.Null))
        val labeled = success(
            withoutNote.document,
            setProperty("display-label", click, "displayLabel", WorkspacePropertyValue.Text("Continue")),
        )
        assertFalse(labeled.document.blocks.getValue(BlockId("click")).fields.containsKey("note"))
        assertEquals(
            FieldValue.Text("Continue"),
            labeled.document.blocks.getValue(BlockId("click")).fields["displayLabel"],
        )
    }

    private fun success(document: WorkspaceDocument, operation: WorkspaceOperation): WorkspaceOperationResult.Success =
        WorkspaceOperationExecutor.execute(document, operation) as WorkspaceOperationResult.Success

    private fun failure(document: WorkspaceDocument, operation: WorkspaceOperation): WorkspaceOperationResult.Failure =
        WorkspaceOperationExecutor.execute(document, operation) as WorkspaceOperationResult.Failure

    private fun assertFailureCode(
        document: WorkspaceDocument,
        operation: WorkspaceOperation,
        expected: WorkspaceOperationDiagnosticCode,
    ) {
        val result = failure(document, operation)
        assertEquals(expected, result.diagnostic.code)
        assertEquals(document, result.document)
    }

    private fun setProperty(
        operationId: String,
        target: SemanticEntityId,
        property: String,
        value: WorkspacePropertyValue,
    ): WorkspaceOperation.SetProperty = WorkspaceOperation.SetProperty(
        operationId = id(operationId),
        target = target,
        property = SemanticPropertyId(property),
        value = value,
    )

    private fun id(value: String) = WorkspaceOperationId(value)

    private fun fixture(): WorkspaceDocument {
        val startId = BlockId("start")
        val clickId = BlockId("click")
        val waitId = BlockId("wait")
        val spareId = BlockId("spare")
        val conditionId = BlockId("condition")
        val boolId = BlockId("bool")
        val blocks = linkedMapOf(
            startId to statement("start", "event.start", previous = false, nextTarget = "click.previous"),
            clickId to statement(
                "click",
                "action.clickText",
                previousTarget = "start.next",
                nextTarget = "wait.previous",
                fields = mapOf(
                    "text" to FieldValue.Text("Login"),
                    "active" to FieldValue.Bool(true),
                    "note" to FieldValue.Text("optional"),
                ),
            ),
            waitId to statement(
                "wait",
                "action.wait",
                previousTarget = "click.next",
                fields = mapOf("duration" to FieldValue.Number(500.0)),
            ),
            spareId to statement("spare", "debug.log", fields = mapOf("message" to FieldValue.Text("spare"))),
            conditionId to statement(
                "condition",
                "control.condition",
                valueInputs = listOf(
                    ValueInput(
                        "CONDITION",
                        Connection(ConnectionId("condition.CONDITION"), conditionId, ConnectionKind.ValueInput, accepts = setOf("Boolean"), slotName = "CONDITION"),
                    ),
                ),
            ),
            boolId to BlockNode(
                id = boolId,
                type = "literal.boolean",
                fields = mapOf("value" to FieldValue.Bool(true)),
                output = Connection(ConnectionId("bool.output"), boolId, ConnectionKind.Output, provides = "Boolean"),
            ),
        )
        val legacy = WorkspaceDocument(
            id = "m3-operation-fixture",
            version = 7,
            blocks = blocks,
            rootBlocks = listOf(startId, spareId, conditionId, boolId),
            variables = VariableRegistry(
                mapOf(
                    "counter" to VariableDefinition(
                        id = "counter",
                        name = "counter",
                        type = "Number",
                        scope = VariableScope.Script,
                        defaultValue = "0",
                    ),
                ),
            ),
        )
        return CanonicalWorkspaceMigration.toCurrent(legacy).document
    }

    private fun statement(
        id: String,
        type: String,
        previous: Boolean = true,
        previousTarget: String? = null,
        nextTarget: String? = null,
        fields: Map<String, FieldValue> = emptyMap(),
        valueInputs: List<ValueInput> = emptyList(),
    ): BlockNode {
        val blockId = BlockId(id)
        return BlockNode(
            id = blockId,
            type = type,
            fields = fields,
            previous = if (previous) Connection(
                ConnectionId("$id.previous"),
                blockId,
                ConnectionKind.Previous,
                connectedTo = previousTarget?.let(::ConnectionId),
            ) else null,
            next = Connection(
                ConnectionId("$id.next"),
                blockId,
                ConnectionKind.Next,
                connectedTo = nextTarget?.let(::ConnectionId),
            ),
            valueInputs = valueInputs,
        )
    }
}
