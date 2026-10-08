package de.visualtasker.workflow.semantics

import de.visualtasker.workflow.core.BlockId
import de.visualtasker.workflow.core.BlockNode
import de.visualtasker.workflow.core.WorkspaceDocument
import de.visualtasker.workflow.core.CanonicalWorkspaceMigration
import de.visualtasker.workflow.core.withConnectionUpdated
import de.visualtasker.workflow.core.FieldValue
import de.visualtasker.workflow.core.SemanticEntityId
import de.visualtasker.workflow.core.SemanticEntityKind
import de.visualtasker.workflow.core.SemanticPropertyConstraint
import de.visualtasker.workflow.core.SemanticPropertyId
import de.visualtasker.workflow.core.WorkspaceOperation
import de.visualtasker.workflow.core.WorkspaceOperationDiagnosticCode
import de.visualtasker.workflow.core.WorkspaceOperationExecutor
import de.visualtasker.workflow.core.WorkspaceOperationId
import de.visualtasker.workflow.core.WorkspaceOperationResult
import de.visualtasker.workflow.core.WorkspacePropertyValue
import de.visualtasker.workflow.semantics.ir.IrGenerator
import de.visualtasker.workflow.semantics.ir.IrGraphGenerator
import de.visualtasker.workflow.semantics.ir.IrStatement
import de.visualtasker.workflow.semantics.validation.TypeMismatch
import de.visualtasker.workflow.semantics.validation.Validator
import de.visualtasker.workflow.serialization.WorkflowSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedSemanticServicesTest {
    @Test
    fun neutralConsumerCanLoadTypecheckValidateAndLowerWithoutEditorPresentation() {
        val start = node(WorkflowElementTypes.EVENT_START, "start")
        val wait = node(WorkflowElementTypes.ACTION_WAIT, "wait")
        val document = WorkspaceDocument(
            id = "neutral-consumer",
            blocks = linkSequence(start, wait),
            rootBlocks = listOf(start.id),
        )

        val encoded = WorkflowSerializer.serialize(document)
        val decoded = WorkflowSerializer.deserialize(encoded)
        val validation = Validator.validate(decoded)
        val ir = IrGenerator().generate(decoded)

        assertEquals(CanonicalWorkspaceMigration.toCurrent(document).document, decoded)
        assertTrue(validation.errors.toString(), validation.isValid)
        assertEquals(listOf(IrStatement.Wait(500L)), ir.statements)
    }

    @Test
    fun catalogAndTypeCompatibilityAreNeutralAndComplete() {
        assertEquals(127, VisualTaskerCommandCatalog.allEntries().size)
        assertTrue(VisualTaskerCommandCatalog.validate().isEmpty())
        assertTrue(WorkspaceValueTypeSystem.isCompatible("Number", setOf("Number", "Any")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("Number", setOf("Boolean")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("String", setOf("Number")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("String?", setOf("String")))
        assertTrue(WorkspaceValueTypeSystem.isCompatible("List<String>", setOf("List<String>")))
    }

    @Test
    fun validatorRejectsNonBooleanIfConditionUsingSharedTypeContract() {
        val start = node(WorkflowElementTypes.EVENT_START, "start")
        val decision = node(WorkflowElementTypes.CONTROL_IF, "if")
        val number = node(WorkflowElementTypes.LITERAL_NUMBER, "number")
        val sequence = linkSequence(start, decision)
        val condition = decision.valueInputs.single { it.name == "CONDITION" }.connection
        val output = requireNotNull(number.output)
        val connectedDecision = decision.withConnectionUpdated(condition.id) { it.copy(connectedTo = output.id) }
        val connectedNumber = number.withConnectionUpdated(output.id) { it.copy(connectedTo = condition.id) }
        val document = WorkspaceDocument(
            id = "invalid-if-condition",
            blocks = sequence + (decision.id to connectedDecision) + (number.id to connectedNumber),
            rootBlocks = listOf(start.id),
        )

        val result = Validator.validate(document)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { error ->
            error is TypeMismatch && error.blockId == decision.id && error.inputName == "CONDITION"
        })
    }

    @Test
    fun commandCatalogProvidesDeterministicTypedPropertyDefinitions() {
        val wait = VisualTaskerCommandCatalog.findByCanonicalName("wait")!!
        val first = wait.semanticPropertyDefinitions(SemanticEntityKind.Statement)
        val second = wait.semanticPropertyDefinitions(SemanticEntityKind.Statement)

        assertEquals(first, second)
        val duration = first.single { it.propertyId.value == "ms" }
        assertEquals("action.wait", duration.commandId)
        assertEquals("Number", WorkspaceValueTypeSystem.workspaceName(duration.valueType))
        assertTrue(duration.writable)
        assertFalse(duration.nullable)
        assertTrue(duration.constraints.any { it is SemanticPropertyConstraint.NumberRange })
    }

    @Test
    fun catalogSchemaValidatesAndAppliesCommandProperty() {
        val wait = node(WorkflowElementTypes.ACTION_WAIT, "wait")
        val document = CanonicalWorkspaceMigration.toCurrent(
            WorkspaceDocument(
                id = "catalog-property",
                blocks = mapOf(wait.id to wait),
                rootBlocks = listOf(wait.id),
            ),
        ).document
        val target = SemanticEntityId("block:wait")
        val definitions = VisualTaskerSemanticPropertySchema.propertiesFor(document, target)
        assertEquals(definitions, VisualTaskerSemanticPropertySchema.propertiesFor(document, target))
        assertEquals("action.wait", definitions.single { it.propertyId.value == "ms" }.commandId)

        val applied = WorkspaceOperationExecutor.execute(
            document,
            WorkspaceOperation.SetProperty(
                WorkspaceOperationId("set-wait"),
                target,
                SemanticPropertyId("ms"),
                WorkspacePropertyValue.Number(750.0),
            ),
            VisualTaskerSemanticPropertySchema,
        ) as WorkspaceOperationResult.Success
        assertEquals(FieldValue.Number(750.0), applied.document.blocks.getValue(wait.id).fields["ms"])

        val rejected = WorkspaceOperationExecutor.execute(
            applied.document,
            WorkspaceOperation.SetProperty(
                WorkspaceOperationId("set-wait-invalid"),
                target,
                SemanticPropertyId("ms"),
                WorkspacePropertyValue.Text("fast"),
            ),
            VisualTaskerSemanticPropertySchema,
        ) as WorkspaceOperationResult.Failure
        assertEquals(WorkspaceOperationDiagnosticCode.INVALID_PROPERTY_TYPE, rejected.diagnostic.code)
        assertEquals(applied.document, rejected.document)
    }

    @Test
    fun customDisplayLabelSurvivesSerializationAndFeedsSharedIrProjection() {
        val defaultWait = node(WorkflowElementTypes.ACTION_WAIT, "wait")
        val wait = defaultWait.copy(
            fields = defaultWait.fields + ("displayLabel" to FieldValue.Text("Shared wait")),
        )
        val document = WorkspaceDocument(
            id = "shared-display-label",
            blocks = mapOf(wait.id to wait),
            rootBlocks = listOf(wait.id),
        )

        val reloaded = WorkflowSerializer.deserialize(WorkflowSerializer.serialize(document))
        val projected = IrGraphGenerator().generate(reloaded)

        assertEquals("Shared wait", projected.nodes.single { it.source.blockId == wait.id.value }.label)
    }

    private fun node(type: String, id: String): BlockNode =
        requireNotNull(DefaultWorkflowSemanticDefinitions.semanticDefinition(type))
            .createNode(BlockId(id))

    private fun linkSequence(first: BlockNode, second: BlockNode): Map<BlockId, BlockNode> {
        val next = requireNotNull(first.next)
        val previous = requireNotNull(second.previous)
        return mapOf(
            first.id to first.withConnectionUpdated(next.id) { it.copy(connectedTo = previous.id) },
            second.id to second.withConnectionUpdated(previous.id) { it.copy(connectedTo = next.id) },
        )
    }
}
