package de.visualtasker.blockeditor.serialization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

class LegacyBlocklyImportFixtureTest {
    @Test
    fun macrorifyEmscriptBlocklyFixture_preservesLegacyDescriptorStructure() {
        val document = parseLegacyFixture("legacy/macrorify_macro.ems-4.xml")

        assertEquals("https://developers.google.com/blockly/xml", document.xmlNamespace)
        assertEquals("start", document.root.id)
        assertEquals("em_on_start", document.root.type)
        assertTrue(document.knownLegacyDescriptors.containsKey(document.root.type))
        assertFalse(document.runtimeAuthority)
        assertFalse(document.executableIrCreated)

        val doHead = document.root.statementBlocks.getValue("DO")
        assertEquals("scan", doHead.id)
        assertEquals("em_scan_element_tree", doHead.type)
        assertTrue(document.knownLegacyDescriptors.containsKey(doHead.type))

        val click = doHead.next!!
        assertEquals("click-login", click.id)
        assertEquals("em_click_text", click.type)
        assertTrue(document.knownLegacyDescriptors.containsKey(click.type))
        assertEquals("Login", click.shadowValues.getValue("TEXT").fields.getValue("TEXT"))

        val screenshot = click.next!!
        assertEquals("screenshot", screenshot.id)
        assertEquals("em_screenshot", screenshot.type)
        assertTrue(document.knownLegacyDescriptors.containsKey(screenshot.type))
        assertEquals("/sdcard/screen.png", screenshot.shadowValues.getValue("PATH").fields.getValue("TEXT"))
        assertEquals(null, screenshot.next)

        val reporterTypes = listOf(
            click.shadowValues.getValue("TEXT").type,
            screenshot.shadowValues.getValue("PATH").type,
        )
        assertEquals(listOf("em_text", "em_text"), reporterTypes)
        reporterTypes.forEach { type ->
            assertTrue(document.knownLegacyDescriptors.containsKey(type))
        }
    }

    private fun parseLegacyFixture(resourcePath: String): LegacyBlocklyImportDocument {
        val stream = javaClass.classLoader?.getResourceAsStream(resourcePath)
        assertNotNull("Missing test fixture $resourcePath", stream)
        stream!!.use { input ->
            val root = xmlRoot(input)
            val topLevelBlock = root.childElements("block").single()
            return LegacyBlocklyImportDocument(
                xmlNamespace = root.namespaceURI,
                root = parseBlock(topLevelBlock),
            )
        }
    }

    private fun xmlRoot(input: InputStream): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(input).documentElement
    }

    private fun parseBlock(element: Element): LegacyBlock {
        val statementBlocks = element.childElements("statement").associate { statement ->
            val name = statement.getAttribute("name")
            val block = statement.childElements("block").single()
            name to parseBlock(block)
        }
        val shadowValues = element.childElements("value").mapNotNull { value ->
            val name = value.getAttribute("name")
            val shadow = value.childElements("shadow").singleOrNull() ?: return@mapNotNull null
            name to LegacyShadow(
                id = shadow.getAttribute("id"),
                type = shadow.getAttribute("type"),
                fields = shadow.childElements("field").associate { field ->
                    field.getAttribute("name") to field.textContent
                },
            )
        }.toMap()
        val nextBlock = element.childElements("next")
            .singleOrNull()
            ?.childElements("block")
            ?.singleOrNull()
            ?.let(::parseBlock)

        return LegacyBlock(
            id = element.getAttribute("id"),
            type = element.getAttribute("type"),
            statementBlocks = statementBlocks,
            shadowValues = shadowValues,
            next = nextBlock,
        )
    }

    private fun Element.childElements(localName: String): List<Element> =
        childNodes.asSequence()
            .filterIsInstance<Element>()
            .filter { it.localName == localName }
            .toList()

    private fun org.w3c.dom.NodeList.asSequence(): Sequence<Node> =
        (0 until length).asSequence().map { item(it) }
}

private data class LegacyBlocklyImportDocument(
    val xmlNamespace: String,
    val root: LegacyBlock,
    val knownLegacyDescriptors: Map<String, String> = mapOf(
        "em_on_start" to "EVENT.ON_START",
        "em_scan_element_tree" to "ACCESSIBILITY.SCAN_ELEMENT_TREE",
        "em_click_text" to "UI.CLICK_TEXT",
        "em_screenshot" to "VISION.SCREENSHOT",
        "em_text" to "TEXT_LITERAL",
    ),
    val runtimeAuthority: Boolean = false,
    val executableIrCreated: Boolean = false,
)

private data class LegacyBlock(
    val id: String,
    val type: String,
    val statementBlocks: Map<String, LegacyBlock>,
    val shadowValues: Map<String, LegacyShadow>,
    val next: LegacyBlock?,
)

private data class LegacyShadow(
    val id: String,
    val type: String,
    val fields: Map<String, String>,
)
