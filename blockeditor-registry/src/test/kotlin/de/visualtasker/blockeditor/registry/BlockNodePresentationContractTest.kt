package de.visualtasker.blockeditor.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockNodePresentationContractTest {
    @Test
    fun `classifies field categories for input config and presentation`() {
        val input = FieldDefinition(
            key = "ms",
            label = "Wait ms",
            sourceOptions = listOf(ParameterSourceKind.MANUAL, ParameterSourceKind.REPORTER),
            defaultSource = ParameterSourceKind.MANUAL,
        )
        val config = FieldDefinition(
            key = "active",
            label = "Active",
            kind = FieldKind.BOOLEAN,
            defaultValue = "true",
        )
        val presentation = FieldDefinition(
            key = "displayLabel",
            label = "Display",
        )

        assertEquals(SemanticPropertyCategory.INPUT, BlockNodePresentationContract.fieldCategory(input))
        assertEquals(SemanticPropertyCategory.CONFIG, BlockNodePresentationContract.fieldCategory(config))
        assertEquals(SemanticPropertyCategory.PRESENTATION, BlockNodePresentationContract.fieldCategory(presentation))
    }

    @Test
    fun `reporter slot candidate follows input and source rules`() {
        val candidate = FieldDefinition(
            key = "text",
            label = "Text",
            sourceOptions = listOf(ParameterSourceKind.MANUAL, ParameterSourceKind.REPORTER),
            defaultSource = ParameterSourceKind.MANUAL,
        )
        val nonCandidate = FieldDefinition(
            key = "displayLabel",
            label = "Display Label",
            sourceOptions = listOf(ParameterSourceKind.MANUAL, ParameterSourceKind.REPORTER),
            defaultSource = ParameterSourceKind.MANUAL,
        )

        assertTrue(BlockNodePresentationContract.fieldIsReporterSlotCandidate(candidate))
        assertFalse(BlockNodePresentationContract.fieldIsReporterSlotCandidate(nonCandidate))
    }

    @Test
    fun `semantic properties include input output and structure without duplicate truth`() {
        val definition = BlockDefinition(
            id = "control.if",
            label = "If",
            category = BlockCategories.CONTROL,
            hasPrevious = true,
            hasNext = true,
            outputType = null,
            fields = listOf(
                FieldDefinition(
                    key = "active",
                    label = "Active",
                    kind = FieldKind.BOOLEAN,
                    defaultValue = "true",
                ),
                FieldDefinition(
                    key = "displayLabel",
                    label = "Display Label",
                ),
            ),
            valueInputs = listOf(
                ValueInputDefinition(
                    name = "CONDITION",
                    label = "Condition",
                    accepts = setOf("Bool"),
                ),
            ),
            statementInputs = listOf(
                StatementInputDefinition(
                    name = "THEN",
                    label = "Then",
                ),
            ),
        )

        val refs = BlockNodePresentationContract.semanticPropertiesForDefinition(
            ownerId = "block-1",
            definition = definition,
        )
        val categories = refs.associate { it.key to it.category }
        assertEquals(SemanticPropertyCategory.CONFIG, categories["active"])
        assertEquals(SemanticPropertyCategory.PRESENTATION, categories["displayLabel"])
        assertEquals(SemanticPropertyCategory.INPUT, categories["CONDITION"])
        assertEquals(SemanticPropertyCategory.STRUCTURE, categories["THEN"])
        assertEquals(refs.size, refs.map { it.id }.distinct().size)
    }
}
