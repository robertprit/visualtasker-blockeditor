package de.visualtasker.blockeditor.serialization

import de.visualtasker.blockeditor.domain.WorkspaceDocument

enum class WorkspaceCompatibilitySeverity {
    INFO,
    WARNING,
    ERROR,
}

data class WorkspaceCompatibilityDiagnostic(
    val severity: WorkspaceCompatibilitySeverity,
    val code: String,
    val message: String,
    val blockId: String? = null,
)

sealed interface WorkspaceDecodeResult {
    val diagnostics: List<WorkspaceCompatibilityDiagnostic>

    data class Decoded(
        val document: WorkspaceDocument,
        override val diagnostics: List<WorkspaceCompatibilityDiagnostic> = emptyList(),
    ) : WorkspaceDecodeResult

    data class UnsupportedSchema(
        val version: Int?,
        override val diagnostics: List<WorkspaceCompatibilityDiagnostic>,
    ) : WorkspaceDecodeResult

    data class Malformed(
        val reason: String,
        override val diagnostics: List<WorkspaceCompatibilityDiagnostic>,
    ) : WorkspaceDecodeResult
}
