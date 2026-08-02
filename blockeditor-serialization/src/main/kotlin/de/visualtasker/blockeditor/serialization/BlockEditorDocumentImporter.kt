package de.visualtasker.blockeditor.serialization

import de.visualtasker.blockeditor.domain.WorkspaceDocument

object BlockEditorDocumentImporter {
    fun import(
        raw: String,
        fileName: String? = null,
        mimeType: String? = null,
    ): WorkspaceDocument {
        val trimmed = raw.trimStart()
        if (trimmed.isBlank()) {
            throw WorkspaceSerializationException("Blockeditor document is blank.")
        }
        return when {
            isWorkspaceJson(trimmed, mimeType) -> WorkspaceSerializer.deserialize(raw)
            isEmScript(fileName, mimeType) -> throw WorkspaceSerializationException(
                "EMScript import is not implemented yet.",
            )
            isLegacyBlocklyXml(trimmed, fileName) -> LegacyBlocklyXmlImporter.import(raw, documentId = documentId(fileName))
            else -> throw WorkspaceSerializationException(
                "Unsupported blockeditor document format: ${formatDescription(fileName, mimeType, trimmed)}",
            )
        }
    }

    private fun isWorkspaceJson(trimmed: String, mimeType: String?): Boolean =
        trimmed.startsWith("{") || mimeType.equals(BlockEditorDocumentFormats.WORKSPACE_JSON, ignoreCase = true)

    private fun isLegacyBlocklyXml(trimmed: String, fileName: String?): Boolean =
        trimmed.startsWith("<xml") ||
            fileName?.endsWith(".xml", ignoreCase = true) == true ||
            trimmed.contains("https://developers.google.com/blockly/xml")

    private fun isEmScript(fileName: String?, mimeType: String?): Boolean =
        fileName?.endsWith(".ems", ignoreCase = true) == true ||
            mimeType.equals(BlockEditorDocumentFormats.EMSCRIPT, ignoreCase = true)

    private fun documentId(fileName: String?): String =
        fileName
            ?.substringAfterLast('/')
            ?.substringBeforeLast(".xml")
            ?.substringBeforeLast(".ems")
            ?.takeIf { it.isNotBlank() }
            ?: "legacy-blockly-import"

    private fun formatDescription(fileName: String?, mimeType: String?, trimmed: String): String =
        listOfNotNull(
            fileName?.let { "fileName=$it" },
            mimeType?.let { "mimeType=$it" },
            "prefix=${trimmed.take(32).replace(Regex("\\s+"), " ")}",
        ).joinToString()
}
