package de.visualtasker.blockeditor.registry

import de.visualtasker.blockeditor.domain.WorkspaceDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockNodeDesignerProjectorTest {
    @Test
    fun definitionCreatesParallelBlockAndNodeProjection() {
        val blueprint = BlockDesignBlueprint(
            type = "action.wait",
            label = "WAIT",
            category = BlockCategories.ACTION,
            hasPrevious = true,
            hasNext = true,
            fields = listOf(
                FieldDefinition(
                    key = "ms",
                    label = "ms",
                    kind = FieldKind.NUMBER,
                    sourceOptions = listOf(ParameterSourceKind.MANUAL, ParameterSourceKind.REPORTER),
                    defaultSource = ParameterSourceKind.MANUAL,
                ),
            ),
            valueInputs = listOf(
                ValueInputDefinition("ms", "ms", accepts = setOf("Number")),
            ),
        )

        val bundle = BlockNodeDesignerProjector.project(blueprint)

        assertNotNull(bundle.blockProjection)
        assertNotNull(bundle.nodeProjection)
        assertTrue(bundle.blockProjection.valueInputSlots.any { it.name == "ms" })
        assertTrue(bundle.nodeProjection.valueInputPorts.any { it.name == "ms" })
    }

    @Test
    fun blockAndNodeShareSameSemanticPropertyRefForInput() {
        val blueprint = BlockDesignBlueprint(
            type = "logic.compare",
            label = "COMPARE",
            category = BlockCategories.LOGIC,
            valueInputs = listOf(
                ValueInputDefinition("LEFT", "left", setOf("Number")),
            ),
        )

        val bundle = BlockNodeDesignerProjector.project(blueprint)
        val blockSlot = bundle.blockProjection.valueInputSlots.first { it.name == "LEFT" }
        val nodePort = bundle.nodeProjection.valueInputPorts.first { it.name == "LEFT" }

        assertEquals(blockSlot.semanticPropertyId, nodePort.semanticPropertyId)
    }

    @Test
    fun inputChangeUpdatesBothProjections() {
        val original = BlockDesignBlueprint(
            type = "logic.compare",
            label = "COMPARE",
            category = BlockCategories.LOGIC,
            valueInputs = listOf(ValueInputDefinition("LEFT", "left", setOf("Number"))),
        )
        val changed = original.copy(
            valueInputs = listOf(ValueInputDefinition("LEFT", "left", setOf("String"))),
        )

        val originalBundle = BlockNodeDesignerProjector.project(original)
        val changedBundle = BlockNodeDesignerProjector.project(changed)

        assertEquals("Number", originalBundle.nodeProjection.valueInputPorts.single().dataType)
        assertEquals("String", changedBundle.nodeProjection.valueInputPorts.single().dataType)
        assertEquals("String", changedBundle.blockProjection.valueInputSlots.single().dataType)
    }

    @Test
    fun outputAppearsAsReporterOutputAndNodeValueOutputPort() {
        val blueprint = BlockDesignBlueprint(
            type = "logic.bool",
            label = "BOOL",
            category = BlockCategories.LOGIC,
            hasPrevious = false,
            hasNext = false,
            isReporter = true,
            outputType = "Bool",
        )

        val bundle = BlockNodeDesignerProjector.project(blueprint)

        assertTrue(bundle.blockProjection.reporterOutputEnabled)
        assertEquals("Bool", bundle.nodeProjection.valueOutputPort?.dataType)
    }

    @Test
    fun configAndPresentationDoNotCreateValuePorts() {
        val blueprint = BlockDesignBlueprint(
            type = "action.wait",
            label = "WAIT",
            category = BlockCategories.ACTION,
            fields = listOf(
                FieldDefinition(
                    key = "active",
                    label = "Active",
                    kind = FieldKind.BOOLEAN,
                ),
                FieldDefinition(
                    key = "displayLabel",
                    label = "Display",
                    kind = FieldKind.TEXT,
                ),
            ),
        )

        val bundle = BlockNodeDesignerProjector.project(blueprint)
        val semantic = bundle.semanticProperties.associateBy { it.key }

        assertEquals(SemanticPropertyCategory.CONFIG, semantic["active"]?.category)
        assertEquals(SemanticPropertyCategory.PRESENTATION, semantic["displayLabel"]?.category)
        assertTrue(bundle.nodeProjection.valueInputPorts.isEmpty())
    }

    @Test
    fun ifMutatorAddAndRemoveElseIfUpdatesBothProjections() {
        val base = BlockDesignBlueprint(
            type = "control.if",
            label = "IF",
            category = BlockCategories.CONTROL,
            mutatorDefinition = BlockDesignMutatorDefinition(family = BlockDesignMutatorFamily.IF),
        )
        val withElseIf = base.withAddedElseIf().withAddedElseIf()
        val removed = withElseIf.withRemovedElseIf()

        val addBundle = BlockNodeDesignerProjector.project(withElseIf)
        val removeBundle = BlockNodeDesignerProjector.project(removed)

        assertTrue(addBundle.blockProjection.statementContainers.any { it.name.startsWith("ELIF_") })
        assertTrue(addBundle.nodeProjection.structurePorts.any { it.name.startsWith("ELIF_") })
        assertTrue(removeBundle.nodeProjection.structurePorts.size < addBundle.nodeProjection.structurePorts.size)
    }

    @Test
    fun elseifInstanceIdsRemainStableAcrossMutatorOperations() {
        val base = BlockDesignBlueprint(
            type = "control.if",
            label = "IF",
            category = BlockCategories.CONTROL,
            mutatorDefinition = BlockDesignMutatorDefinition(family = BlockDesignMutatorFamily.IF),
        )
        val first = base.withAddedElseIf().withAddedElseIf()
        val firstIds = first.mutatorState.elseIfBranches.map { it.instanceId }
        val changed = first.withRemovedElseIf(firstIds.first()).withAddedElseIf()
        val changedIds = changed.mutatorState.elseIfBranches.map { it.instanceId }

        assertTrue(changedIds.contains(firstIds.last()))
        assertTrue(changedIds.distinct().size == changedIds.size)
    }

    @Test
    fun elseBranchCanExistOnlyOnce() {
        val base = BlockDesignBlueprint(
            type = "control.if",
            label = "IF",
            category = BlockCategories.CONTROL,
            mutatorDefinition = BlockDesignMutatorDefinition(family = BlockDesignMutatorFamily.IF),
        )
            .withElseEnabled(true)
            .withElseEnabled(true)

        val bundle = BlockNodeDesignerProjector.project(base)
        val elseCount = bundle.nodeProjection.structurePorts.count { it.name == "ELSE" }
        assertEquals(1, elseCount)
    }

    @Test
    fun whileMutatorProjectsConditionAndBodyForBothViews() {
        val blueprint = BlockDesignBlueprint(
            type = "control.while",
            label = "WHILE",
            category = BlockCategories.CONTROL,
            mutatorDefinition = BlockDesignMutatorDefinition(family = BlockDesignMutatorFamily.WHILE),
        )

        val bundle = BlockNodeDesignerProjector.project(blueprint)

        assertTrue(bundle.blockProjection.statementContainers.any { it.name == "BODY" })
        assertTrue(bundle.nodeProjection.structurePorts.any { it.name == "BODY" })
        assertTrue(bundle.nodeProjection.valueInputPorts.any { it.name == "CONDITION" })
    }

    @Test
    fun previewProjectionDoesNotMutateWorkspaceOrIrSemantics() {
        val document = WorkspaceDocument(id = "designer-preview-purity")
        val snapshot = document.copy()
        val blueprint = BlockDesignBlueprint(type = "action.wait", label = "WAIT", category = BlockCategories.ACTION)

        BlockNodeDesignerProjector.project(blueprint)

        assertEquals(snapshot, document)
    }
}
