package de.visualtasker.blockeditor.validation

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.ConnectionId

sealed interface ValidationError {
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
    override val message: String = "Block ${blockId.value} input '$inputName' expects $expected but got $actual",
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
