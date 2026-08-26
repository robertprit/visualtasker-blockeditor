package de.visualtasker.blockeditor.validation

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import de.visualtasker.blockeditor.registry.StaticBlockRegistry
import de.visualtasker.blockeditor.registry.ValueInputDefinition
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorTest {
    @Test
    fun sampleWorkspace_isValid() {
        val result = Validator.validate(SampleWorkspaceFactory.createDemo())
        assertTrue(result.errors.toString(), result.isValid)
    }

    @Test
    fun orphanBlock_isReported() {
        val document = SampleWorkspaceFactory.createDemo()
        val orphanId = document.rootBlocks.last()
        val broken = document.copy(
            rootBlocks = document.rootBlocks.dropLast(1),
        )
        val result = Validator.validate(broken)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it is OrphanBlock && it.blockId == orphanId })
    }

    @Test
    fun optionalValueInput_withoutConnection_isValid() {
        val optionalBlock = BlockDefinition(
            id = "custom.optional.input",
            label = "Optional Input",
            category = "custom",
            hasPrevious = false,
            hasNext = false,
            valueInputs = listOf(
                ValueInputDefinition(
                    name = "maybe",
                    label = "maybe",
                    accepts = setOf("Any"),
                    required = false,
                ),
            ),
        )
        val registry: BlockRegistry = StaticBlockRegistry(
            listOf(optionalBlock) + de.visualtasker.blockeditor.registry.DefaultBlockRegistry.allDefinitions(),
        )
        val node = optionalBlock.createNode(BlockId("optional"))
        val document = WorkspaceDocument(
            id = "optional-input-doc",
            blocks = mapOf(node.id to node),
            rootBlocks = listOf(node.id),
        )

        val result = Validator.validate(document, registry)

        assertTrue(result.errors.toString(), result.isValid)
    }

    @Test
    fun requiredAnyInput_acceptsNumberWithoutTypeMismatch() {
        val consumerDef = BlockDefinition(
            id = "custom.consumer.any",
            label = "Consume Any",
            category = "custom",
            hasPrevious = false,
            hasNext = false,
            valueInputs = listOf(
                ValueInputDefinition(
                    name = "value",
                    label = "value",
                    accepts = setOf("Any"),
                    required = true,
                ),
            ),
        )
        val numberDef = BlockDefinition(
            id = "custom.number.reporter",
            label = "Number",
            category = "custom",
            hasPrevious = false,
            hasNext = false,
            outputType = "Number",
            isReporter = true,
        )
        val registry: BlockRegistry = StaticBlockRegistry(
            listOf(consumerDef, numberDef) + de.visualtasker.blockeditor.registry.DefaultBlockRegistry.allDefinitions(),
        )
        val consumer = consumerDef.createNode(BlockId("consumer"))
        val number = numberDef.createNode(BlockId("number"))
        var document = WorkspaceDocument(
            id = "any-accepts-number-doc",
            blocks = mapOf(consumer.id to consumer, number.id to number),
            rootBlocks = listOf(consumer.id, number.id),
        )
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.Connect(
                source = requireNotNull(number.output).id,
                target = consumer.valueInputs.single { it.name == "value" }.connection.id,
            ),
        )

        val result = Validator.validate(document, registry)

        assertTrue(result.errors.toString(), result.isValid)
    }
}
