package de.visualtasker.blockeditor.serialization

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.WorkspacePoint
import de.visualtasker.blockeditor.domain.asString
import de.visualtasker.blockeditor.registry.WorkspaceBootstrap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyBlocklyImportFixtureTest {
    @Test
    fun workspaceJsonRoutesToWorkspaceSerializer() {
        val original = WorkspaceBootstrap.starter().copy(id = "native-json")
        val document = BlockEditorDocumentImporter.import(
            raw = WorkspaceSerializer.serialize(original),
            mimeType = BlockEditorDocumentFormats.WORKSPACE_JSON,
        )

        assertEquals("native-json", document.id)
        assertEquals(original.rootBlocks, document.rootBlocks)
    }

    @Test
    fun blocklyXmlRoutesToLegacyBlocklyXmlImporter() {
        val document = BlockEditorDocumentImporter.import(
            raw = fixture("legacy/macrorify_macro.ems-4.xml"),
            fileName = "macrorify_macro.ems (4).xml",
        )

        assertEquals("macrorify_macro", document.id)
        assertEquals(BlockId("start"), document.rootBlocks.single())
        assertEquals("em_on_start", document.blocks.getValue(BlockId("start")).type)
    }

    @Test
    fun macrorifyEmscriptBlocklyFixtureImportsAsWorkspaceDocument() {
        val document = BlockEditorDocumentImporter.import(
            raw = fixture("legacy/macrorify_macro.ems-4.xml"),
            fileName = "macrorify_macro.ems (4).xml",
        )

        val start = document.blocks.getValue(BlockId("start"))
        assertEquals("em_on_start", start.type)
        assertEquals("EVENT.ON_START", start.metadata["macro.canonical.command"])
        assertEquals("absent", start.metadata["macro.import.runtimeAuthority"])
        assertEquals("workspace-only", start.metadata["macro.import.representation"])

        val scan = document.blocks.getValue(BlockId("scan"))
        assertEquals("em_scan_element_tree", scan.type)
        assertEquals(start.statementInputs.single { it.name == "DO" }.connection.id, scan.previous!!.connectedTo)
        assertEquals(ConnectionKind.StatementInput, start.statementInputs.single { it.name == "DO" }.connection.kind)

        val click = document.blocks.getValue(BlockId("click-login"))
        assertEquals("em_click_text", click.type)
        assertEquals(scan.next!!.id, click.previous!!.connectedTo)
        assertEquals("Login", click.fields.getValue("TEXT").asString())

        val loginText = document.blocks.getValue(BlockId("login-text"))
        assertEquals("em_text", loginText.type)
        assertEquals(FieldValue.Text("Login"), loginText.fields["field:TEXT"])
        assertEquals(click.valueInputs.single { it.name == "TEXT" }.connection.id, loginText.output!!.connectedTo)

        val screenshot = document.blocks.getValue(BlockId("screenshot"))
        assertEquals("em_screenshot", screenshot.type)
        assertEquals(click.next!!.id, screenshot.previous!!.connectedTo)
        assertEquals("/sdcard/screen.png", screenshot.fields.getValue("PATH").asString())

        val screenshotPath = document.blocks.getValue(BlockId("screenshot-path"))
        assertEquals("em_text", screenshotPath.type)
        assertEquals(FieldValue.Text("/sdcard/screen.png"), screenshotPath.fields["field:TEXT"])
        assertEquals(screenshot.valueInputs.single { it.name == "PATH" }.connection.id, screenshotPath.output!!.connectedTo)
    }

    @Test
    fun rootPositionFromXmlAttributesIsPreserved() {
        val document = BlockEditorDocumentImporter.import(
            raw = """<xml xmlns="https://developers.google.com/blockly/xml"><block type="em_on_start" id="start" x="12" y="34"/></xml>""",
            fileName = "position.xml",
        )

        assertEquals(WorkspacePoint(12f, 34f), document.rootPositions[BlockId("start")])
    }

    @Test
    fun blankInputFailsClearly() {
        val failure = failure { BlockEditorDocumentImporter.import("   ") }

        assertEquals("Blockeditor document is blank.", failure.message)
    }

    @Test
    fun malformedXmlFailsClearly() {
        val failure = failure {
            BlockEditorDocumentImporter.import("<xml><block type=\"em_on_start\"></xml>", fileName = "broken.xml")
        }

        assertTrue(failure.message!!.contains("Malformed Blockly XML"))
    }

    @Test
    fun unsupportedEmscriptImportFailsClearly() {
        val failure = failure {
            BlockEditorDocumentImporter.import("click text Login", fileName = "script.ems")
        }

        assertEquals("EMScript import is not implemented yet.", failure.message)
    }

    @Test
    fun emscriptFileNameFailsClearlyEvenWhenContentLooksLikeXml() {
        val failure = failure {
            BlockEditorDocumentImporter.import(
                raw = """<xml xmlns="https://developers.google.com/blockly/xml"><block type="em_on_start" id="start"/></xml>""",
                fileName = "script.ems",
            )
        }

        assertEquals("EMScript import is not implemented yet.", failure.message)
    }

    @Test
    fun unknownXmlBlockRemainsVisibleAsUnsupportedLegacyBlock() {
        val document = BlockEditorDocumentImporter.import(
            raw = """<xml xmlns="https://developers.google.com/blockly/xml"><block type="vendor_custom" id="custom"><field name="LABEL">Keep me</field></block></xml>""",
            fileName = "unknown.xml",
        )
        val block = document.blocks.getValue(BlockId("custom"))

        assertEquals("vendor_custom", block.type)
        assertEquals(FieldValue.Text("Keep me"), block.fields["field:LABEL"])
        assertEquals("vendor_custom", block.metadata["macro.originalType"])
        assertEquals("true", block.metadata["macro.unsupported"])
        assertEquals("unknown", block.metadata["macro.import.status"])
        assertNotEquals(null, block.metadata["macro.import.warning"])
    }

    private fun fixture(resourcePath: String): String =
        javaClass.classLoader
            ?.getResourceAsStream(resourcePath)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Missing test fixture $resourcePath")

    private fun failure(block: () -> Unit): WorkspaceSerializationException {
        val failure = try {
            block()
            null
        } catch (error: WorkspaceSerializationException) {
            error
        }
        assertNotEquals(null, failure)
        return failure!!
    }
}
