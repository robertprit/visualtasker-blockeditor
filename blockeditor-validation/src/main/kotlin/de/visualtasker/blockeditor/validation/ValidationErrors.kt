package de.visualtasker.blockeditor.validation

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.ConnectionId

sealed interface ValidationError {
    val code: String
        get() = "WORKSPACE_VALIDATION"
    val message: String
}

data class MissingRequiredInput(
    val blockId: BlockId,
    val inputName: String,
    override val message: String = "Block ${blockId.value} is missing required input '$inputName'",
) : ValidationError

data class TypeMismatch(
    val blockId: BlockId,
    val inputName: String,
    val expected: Set<String>,
    val actual: String?,
    val commandId: String? = null,
    override val code: String = "EMSCRIPT_ARGUMENT_TYPE_MISMATCH",
    override val message: String = buildString {
        commandId?.let { append("Command '$it' ") }
        append("block ${blockId.value} input '$inputName' expects $expected but got $actual")
    },
) : ValidationError

data class AssignmentTypeMismatch(
    val blockId: BlockId,
    val variableId: String,
    val expectedType: String,
    val actualType: String?,
    override val code: String = "EMSCRIPT_ASSIGNMENT_TYPE_MISMATCH",
    override val message: String =
        "Variable '$variableId' expects $expectedType but got ${actualType ?: "Unknown"}",
) : ValidationError

data class CycleDetected(
    val blockId: BlockId,
    override val message: String = "Cycle detected involving block ${blockId.value}",
) : ValidationError

data class OrphanBlock(
    val blockId: BlockId,
    override val message: String = "Block ${blockId.value} is not connected to any script root",
) : ValidationError

data class InvalidConnection(
    val source: ConnectionId,
    val target: ConnectionId,
    val reason: String,
    override val message: String = "Invalid connection ${source.value} -> ${target.value}: $reason",
) : ValidationError

data class UnknownBlockType(
    val blockId: BlockId,
    val type: String,
    override val message: String = "Block ${blockId.value} has unknown type '$type'",
) : ValidationError

data class DisconnectedChain(
    val blockId: BlockId,
    override val message: String = "Block ${blockId.value} has a broken previous/next chain",
) : ValidationError

data class ValidationResult(
    val errors: List<ValidationError>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}
