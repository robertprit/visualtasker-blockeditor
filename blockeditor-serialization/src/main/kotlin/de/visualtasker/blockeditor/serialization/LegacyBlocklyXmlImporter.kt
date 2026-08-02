package de.visualtasker.blockeditor.serialization

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.Connection
import de.visualtasker.blockeditor.domain.ConnectionId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.StatementInput
import de.visualtasker.blockeditor.domain.ValueInput
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspacePoint
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException

object LegacyBlocklyXmlImporter {
    private const val FIELD_PREFIX = "field:"
    private const val META_XML_TAG = "macro.xmlTag"
    private const val META_XML_TYPE = "macro.originalType"
    private const val META_XML_ID = "macro.originalId"
    private const val META_XML_PATH = "macro.xmlPath"
    private const val META_UNSUPPORTED = "macro.unsupported"
    private const val META_KNOWN = "macro.known"
    private const val META_IMPORT_STATUS = "macro.import.status"
    private const val META_IMPORT_CANONICAL_COMMAND = "macro.import.canonicalCommand"
    private const val META_IMPORT_RUNTIME_AUTHORITY = "macro.import.runtimeAuthority"
    private const val META_IMPORT_REPRESENTATION = "macro.import.representation"
    private const val META_IMPORT_WARNING = "macro.import.warning"
    private const val RUNTIME_AUTHORITY_ABSENT = "absent"
    private const val WORKSPACE_ONLY = "workspace-only"

    fun import(raw: String, documentId: String = "legacy-blockly-import"): WorkspaceDocument {
        if (raw.isBlank()) {
            throw WorkspaceSerializationException("Legacy Blockly XML document is blank.")
        }
        val xml = try {
            parse(raw)
        } catch (error: SAXException) {
            throw WorkspaceSerializationException("Malformed Blockly XML.", error)
        } catch (error: RuntimeException) {
            throw WorkspaceSerializationException("Malformed Blockly XML.", error)
        }
        val root = xml.documentElement
        if (root.localTagName() != "xml") {
            throw WorkspaceSerializationException("Malformed Blockly XML: expected <xml> root.")
        }
        val context = ImportContext()
        root.childElements("block").forEach { block ->
            context.importBlock(block, ParentLink.Root)
        }
        val roots = context.blocks.values
            .filter { block -> block.output == null && block.previous?.connectedTo == null }
            .map { it.id }
        return WorkspaceDocument(
            id = documentId,
            blocks = context.blocks.toMap(),
            rootBlocks = roots,
            rootPositions = roots.mapIndexed { index, blockId ->
                blockId to (context.rootPositions[blockId] ?: WorkspacePoint(48f, 48f + index * 128f))
            }.toMap(),
        )
    }

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    private class ImportContext {
        val blocks = linkedMapOf<BlockId, BlockNode>()
        val rootPositions = linkedMapOf<BlockId, WorkspacePoint>()
        private val ids = mutableMapOf<String, Int>()
        private var generatedIndex = 0

        fun importBlock(element: Element, parentLink: ParentLink): BlockId {
            val originalType = element.attr("type")
            val type = originalType.ifBlank { "legacy.missing-type" }
            val descriptor = LegacyBlocklyKnownBlockRegistry.get(type)
            val originalId = element.attr("id")
            val blockId = uniqueBlockId(originalId.ifBlank { "${element.localTagName()}-${generatedIndex++}" })
            if (parentLink == ParentLink.Root) {
                element.floatAttr("x")?.let { x ->
                    element.floatAttr("y")?.let { y ->
                        rootPositions[blockId] = WorkspacePoint(x, y)
                    }
                }
            }

            val valueInputs = mutableListOf<ValueInput>()
            val statementInputs = mutableListOf<StatementInput>()
            val fields = linkedMapOf<String, FieldValue>()
            element.childElements("field").forEach { field ->
                val name = field.attr("name")
                fields["$FIELD_PREFIX$name"] = FieldValue.Text(field.textContent)
            }

            var node = BlockNode(
                id = blockId,
                type = type,
                fields = fields.toMap(),
                previous = previousConnection(blockId, parentLink, descriptor),
                next = if (element.firstChildElement("next") != null || descriptor?.hasNext == true) {
                    Connection(ConnectionId("${blockId.value}:next"), blockId, ConnectionKind.Next)
                } else {
                    null
                },
                output = outputConnection(blockId, parentLink, descriptor),
                metadata = metadata(element, type, originalId, descriptor),
            )
            blocks[blockId] = node

            element.childElements("value").forEach { valueElement ->
                val name = valueElement.attr("name").ifBlank { "VALUE" }
                val connection = Connection(
                    id = ConnectionId("${blockId.value}:value:$name"),
                    owner = blockId,
                    kind = ConnectionKind.ValueInput,
                    accepts = setOf("Any"),
                    slotName = name,
                )
                val child = valueElement.firstChildElement("block") ?: valueElement.firstChildElement("shadow")
                val childId = child?.let { importBlock(it, ParentLink.Value(connection.id)) }
                child?.shadowTextValue()?.let { fields[name] = FieldValue.Text(it) }
                valueInputs += ValueInput(
                    name = name,
                    connection = connection.copy(connectedTo = childId?.let { blocks.getValue(it).output?.id }),
                )
            }

            element.childElements("statement").forEach { statementElement ->
                val name = statementElement.attr("name").ifBlank { "DO" }
                val connection = Connection(
                    id = ConnectionId("${blockId.value}:statement:$name"),
                    owner = blockId,
                    kind = ConnectionKind.StatementInput,
                    slotName = name,
                )
                val child = statementElement.firstChildElement("block")
                val childId = child?.let { importBlock(it, ParentLink.Statement(connection.id)) }
                statementInputs += StatementInput(
                    name = name,
                    connection = connection.copy(connectedTo = childId?.let { blocks.getValue(it).previous?.id }),
                )
            }

            val nextBlock = element.firstChildElement("next")?.firstChildElement("block")
            val nextConnection = node.next ?: if (nextBlock != null) {
                Connection(ConnectionId("${blockId.value}:next"), blockId, ConnectionKind.Next)
            } else {
                null
            }
            val nextChild = nextBlock?.let { importBlock(it, ParentLink.Next(nextConnection!!.id)) }
            node = blocks.getValue(blockId).copy(
                fields = fields.toMap(),
                next = nextConnection?.copy(connectedTo = nextChild?.let { blocks.getValue(it).previous?.id }),
                valueInputs = valueInputs,
                statementInputs = statementInputs,
            )
            blocks[blockId] = node
            return blockId
        }

        private fun previousConnection(
            blockId: BlockId,
            parentLink: ParentLink,
            descriptor: LegacyBlocklyDescriptor?,
        ): Connection? = when (parentLink) {
            is ParentLink.Next -> Connection(
                ConnectionId("${blockId.value}:previous"),
                blockId,
                ConnectionKind.Previous,
                connectedTo = parentLink.sourceConnectionId,
            )
            is ParentLink.Statement -> Connection(
                ConnectionId("${blockId.value}:previous"),
                blockId,
                ConnectionKind.Previous,
                connectedTo = parentLink.sourceConnectionId,
            )
            ParentLink.Root,
            is ParentLink.Value -> null
        } ?: if (descriptor?.hasPrevious == true) {
            Connection(ConnectionId("${blockId.value}:previous"), blockId, ConnectionKind.Previous)
        } else {
            null
        }

        private fun outputConnection(
            blockId: BlockId,
            parentLink: ParentLink,
            descriptor: LegacyBlocklyDescriptor?,
        ): Connection? = when (parentLink) {
            is ParentLink.Value -> Connection(
                ConnectionId("${blockId.value}:output"),
                blockId,
                ConnectionKind.Output,
                provides = descriptor?.outputType ?: "Any",
                accepts = setOf(descriptor?.outputType ?: "Any"),
                connectedTo = parentLink.sourceConnectionId,
            )
            ParentLink.Root,
            is ParentLink.Next,
            is ParentLink.Statement -> null
        } ?: descriptor?.outputType?.let { outputType ->
            Connection(
                ConnectionId("${blockId.value}:output"),
                blockId,
                ConnectionKind.Output,
                provides = outputType,
                accepts = setOf(outputType),
            )
        }

        private fun metadata(
            element: Element,
            type: String,
            originalId: String,
            descriptor: LegacyBlocklyDescriptor?,
        ): Map<String, String> = buildMap {
            put(META_XML_TAG, element.localTagName())
            put(META_XML_TYPE, type)
            if (originalId.isNotBlank()) put(META_XML_ID, originalId)
            put(META_XML_PATH, element.xmlPath())
            put(META_KNOWN, (descriptor != null).toString())
            put(META_UNSUPPORTED, (descriptor == null).toString())
            descriptor?.let {
                putAll(it.toMetadata())
                put(META_IMPORT_CANONICAL_COMMAND, it.canonicalCommand)
            }
            val status = if (descriptor == null) "unknown" else "known"
            put(META_IMPORT_STATUS, status)
            put(META_IMPORT_RUNTIME_AUTHORITY, RUNTIME_AUTHORITY_ABSENT)
            put(META_IMPORT_REPRESENTATION, WORKSPACE_ONLY)
            if (descriptor == null) {
                put(
                    META_IMPORT_WARNING,
                    "Unknown block type is preserved as workspace/import data only and needs a mapping before semantic import.",
                )
            }
        }

        private fun uniqueBlockId(raw: String): BlockId {
            val base = raw
                .trim()
                .ifBlank { "block-${generatedIndex++}" }
                .map { char -> if (char.isLetterOrDigit() || char in listOf('-', '_', '.', ':')) char else '-' }
                .joinToString(separator = "")
                .trim('-')
                .ifBlank { "block-${generatedIndex++}" }
            val count = ids.getOrDefault(base, 0) + 1
            ids[base] = count
            return BlockId(if (count == 1) base else "$base-$count")
        }
    }

    private sealed interface ParentLink {
        data object Root : ParentLink
        data class Next(val sourceConnectionId: ConnectionId) : ParentLink
        data class Statement(val sourceConnectionId: ConnectionId) : ParentLink
        data class Value(val sourceConnectionId: ConnectionId) : ParentLink
    }

    private data class LegacyBlocklyDescriptor(
        val legacyType: String,
        val canonicalCommand: String,
        val namespace: String,
        val commandId: String,
        val category: String,
        val kind: String,
        val arguments: List<String> = emptyList(),
        val outputType: String? = null,
        val hasPrevious: Boolean = false,
        val hasNext: Boolean = false,
    ) {
        fun toMetadata(): Map<String, String> = buildMap {
            put("macro.canonical.command", canonicalCommand)
            put("macro.canonical.namespace", namespace)
            put("macro.canonical.commandId", commandId)
            put("macro.canonical.legacyType", legacyType)
            put("macro.canonical.category", category)
            put("macro.canonical.kind", kind)
            put("macro.canonical.arguments", arguments.joinToString())
            outputType?.let { put("macro.canonical.outputType", it) }
            put("macro.canonical.hasPrevious", hasPrevious.toString())
            put("macro.canonical.hasNext", hasNext.toString())
        }
    }

    private object LegacyBlocklyKnownBlockRegistry {
        private val descriptors = listOf(
            LegacyBlocklyDescriptor(
                legacyType = "em_on_start",
                canonicalCommand = "EVENT.ON_START",
                namespace = "EVENT",
                commandId = "ON_START",
                category = "event",
                kind = "EVENT",
                hasNext = true,
            ),
            LegacyBlocklyDescriptor(
                legacyType = "em_scan_element_tree",
                canonicalCommand = "ACCESSIBILITY.SCAN_ELEMENT_TREE",
                namespace = "ACCESSIBILITY",
                commandId = "SCAN_ELEMENT_TREE",
                category = "action",
                kind = "ACTION",
                hasPrevious = true,
                hasNext = true,
            ),
            LegacyBlocklyDescriptor(
                legacyType = "em_click_text",
                canonicalCommand = "UI.CLICK_TEXT",
                namespace = "UI",
                commandId = "CLICK_TEXT",
                category = "action",
                kind = "ACTION",
                arguments = listOf("TEXT:Text:value"),
                hasPrevious = true,
                hasNext = true,
            ),
            LegacyBlocklyDescriptor(
                legacyType = "em_screenshot",
                canonicalCommand = "VISION.SCREENSHOT",
                namespace = "VISION",
                commandId = "SCREENSHOT",
                category = "action",
                kind = "ACTION",
                arguments = listOf("PATH:Text:value"),
                hasPrevious = true,
                hasNext = true,
            ),
            LegacyBlocklyDescriptor(
                legacyType = "em_text",
                canonicalCommand = "TEXT_LITERAL",
                namespace = "TEXT",
                commandId = "TEXT_LITERAL",
                category = "logic",
                kind = "REPORTER",
                arguments = listOf("field:TEXT:Text:field"),
                outputType = "Text",
            ),
        ).associateBy { it.legacyType }

        fun get(type: String): LegacyBlocklyDescriptor? = descriptors[type]
    }
}

private fun Element.attr(name: String): String =
    if (hasAttribute(name)) getAttribute(name) else ""

private fun Element.floatAttr(name: String): Float? =
    attr(name).toFloatOrNull()?.takeIf { it.isFinite() }

private fun Element.localTagName(): String = localName ?: tagName.substringAfter(':')

private fun Element.childElements(name: String? = null): List<Element> =
    (0 until childNodes.length)
        .mapNotNull { index -> childNodes.item(index) as? Element }
        .filter { name == null || it.localTagName() == name }

private fun Element.firstChildElement(name: String): Element? =
    childElements(name).firstOrNull()

private fun Element.shadowTextValue(): String? =
    childElements("field")
        .firstOrNull()
        ?.textContent
        ?.takeIf { it.isNotBlank() }

private fun Node.xmlPath(): String {
    val parts = ArrayDeque<String>()
    var current: Node? = this
    while (current is Element) {
        val name = current.localTagName()
        val sameNameBefore = generateSequence(current.previousSibling) { it.previousSibling }
            .filterIsInstance<Element>()
            .count { it.localTagName() == name }
        parts.addFirst("$name[${sameNameBefore + 1}]")
        current = current.parentNode
    }
    return "/" + parts.joinToString("/")
}
