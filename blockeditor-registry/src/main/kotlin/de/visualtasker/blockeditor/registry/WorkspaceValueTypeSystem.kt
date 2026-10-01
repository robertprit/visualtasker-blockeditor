package de.visualtasker.blockeditor.registry

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.asString
import de.visualtasker.emscript.contract.CoreTypes
import de.visualtasker.emscript.contract.LanguageTypeCompatibility
import de.visualtasker.emscript.contract.LanguageTypeRef

object WorkspaceValueTypeSystem {
    fun variableId(block: BlockNode): String? =
        block.fields["variableId"]?.asString()?.takeIf(String::isNotBlank)
            ?: block.fields["variable"]?.asString()?.takeIf(String::isNotBlank)

    fun variableType(document: WorkspaceDocument, variableId: String): LanguageTypeRef? =
        document.variables.variables[variableId]
            ?.type
            ?.let(LanguageTypeCompatibility::fromWorkspaceName)

    fun expressionType(
        document: WorkspaceDocument,
        blockId: BlockId,
        registry: BlockRegistry,
    ): LanguageTypeRef? {
        val block = document.blocks[blockId] ?: return null
        if (block.type == BlockTypes.VARIABLE_GET || block.type.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX)) {
            return variableId(block)?.let { variableType(document, it) }
        }
        when (block.type) {
            BlockTypes.LOGIC_OPERATE,
            BlockTypes.LITERAL_NUMBER,
            -> return CoreTypes.NUMBER.ref
            BlockTypes.LOGIC_AND,
            BlockTypes.LOGIC_OR,
            BlockTypes.LOGIC_COMPARE,
            BlockTypes.LOGIC_BOOLEAN,
            BlockTypes.LITERAL_BOOLEAN,
            -> return CoreTypes.BOOL.ref
            BlockTypes.LITERAL_STRING -> return CoreTypes.STRING.ref
        }
        return registry.getDefinition(block.type)
            ?.outputType
            ?.let(LanguageTypeCompatibility::fromWorkspaceName)
    }

    fun connectedExpressionType(
        document: WorkspaceDocument,
        block: BlockNode,
        inputName: String,
        registry: BlockRegistry,
    ): LanguageTypeRef? {
        val connected = block.valueInputs
            .firstOrNull { it.name.equals(inputName, ignoreCase = true) }
            ?.connection
            ?.connectedTo
            ?: return null
        val expressionBlockId = WorkspaceGraph.findConnection(document, connected)?.first ?: return null
        return expressionType(document, expressionBlockId, registry)
    }

    fun expectedInputTypes(
        document: WorkspaceDocument,
        block: BlockNode,
        inputName: String,
        declaredAccepts: Set<String>,
    ): Set<LanguageTypeRef> {
        if (block.type == BlockTypes.VARIABLE_SET && inputName.equals(VARIABLE_SET_VALUE_INPUT, ignoreCase = true)) {
            val expected = variableId(block)?.let { variableType(document, it) }
            if (expected != null) return setOf(expected)
        }
        return declaredAccepts.mapNotNullTo(linkedSetOf(), LanguageTypeCompatibility::fromWorkspaceName)
    }

    fun isCompatible(actual: LanguageTypeRef?, expected: Set<LanguageTypeRef>): Boolean {
        if (expected.isEmpty()) return true
        if (actual == null) return expected == setOf(CoreTypes.ANY.ref)
        return expected.any { LanguageTypeCompatibility.isAssignable(actual, it) }
    }

    fun isCompatible(actual: String?, expected: Set<String>): Boolean = isCompatible(
        actual = LanguageTypeCompatibility.fromWorkspaceName(actual),
        expected = expected.mapNotNullTo(linkedSetOf(), LanguageTypeCompatibility::fromWorkspaceName),
    )

    fun workspaceName(type: LanguageTypeRef?): String? = LanguageTypeCompatibility.workspaceName(type)

    const val VARIABLE_SET_VALUE_INPUT = "value"
}
