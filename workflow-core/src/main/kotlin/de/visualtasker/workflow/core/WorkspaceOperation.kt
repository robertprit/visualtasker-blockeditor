package de.visualtasker.workflow.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class WorkspaceOperationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Workspace operation ID must not be blank." }
    }
}

@JvmInline
@Serializable
value class SemanticPropertyId(val value: String) {
    init {
        require(value.isNotBlank()) { "Semantic property ID must not be blank." }
    }
}

@Serializable
sealed interface WorkspacePropertyValue {
    @Serializable
    @SerialName("text")
    data class Text(val value: String) : WorkspacePropertyValue

    @Serializable
    @SerialName("number")
    data class Number(val value: Double) : WorkspacePropertyValue

    @Serializable
    @SerialName("bool")
    data class Bool(val value: Boolean) : WorkspacePropertyValue

    @Serializable
    @SerialName("null")
    data object Null : WorkspacePropertyValue
}

@Serializable
sealed interface WorkspaceOperation {
    val operationId: WorkspaceOperationId

    @Serializable
    @SerialName("createEntity")
    data class CreateEntity(
        override val operationId: WorkspaceOperationId,
        val entity: SemanticEntity,
    ) : WorkspaceOperation

    @Serializable
    @SerialName("deleteEntity")
    data class DeleteEntity(
        override val operationId: WorkspaceOperationId,
        val entityId: SemanticEntityId,
    ) : WorkspaceOperation

    @Serializable
    @SerialName("createRelation")
    data class CreateRelation(
        override val operationId: WorkspaceOperationId,
        val relation: SemanticRelation,
    ) : WorkspaceOperation

    @Serializable
    @SerialName("removeRelation")
    data class RemoveRelation(
        override val operationId: WorkspaceOperationId,
        val relationId: SemanticRelationId,
    ) : WorkspaceOperation

    @Serializable
    @SerialName("setOrder")
    data class SetOrder(
        override val operationId: WorkspaceOperationId,
        val relationId: SemanticRelationId,
        val order: Int,
    ) : WorkspaceOperation

    @Serializable
    @SerialName("setProperty")
    data class SetProperty(
        override val operationId: WorkspaceOperationId,
        val target: SemanticEntityId,
        val property: SemanticPropertyId,
        val value: WorkspacePropertyValue,
    ) : WorkspaceOperation
}

@Serializable
data class WorkspaceTransaction(
    val transactionId: WorkspaceOperationId,
    val operations: List<WorkspaceOperation>,
    val expectedRevision: Long? = null,
)

@Serializable
enum class WorkspaceOperationDiagnosticCode {
    ENTITY_NOT_FOUND,
    RELATION_NOT_FOUND,
    PROPERTY_NOT_FOUND,
    INVALID_PROPERTY_TYPE,
    PROPERTY_NOT_WRITABLE,
    STRUCTURAL_PROPERTY_REQUIRED,
    PROPERTY_CONSTRAINT_VIOLATION,
    INVALID_RELATION,
    DANGLING_REFERENCE,
    INVALID_ORDER,
    DUPLICATE_ID,
    VALIDATION_FAILED,
    REVISION_MISMATCH,
}

data class WorkspaceOperationDiagnostic(
    val code: WorkspaceOperationDiagnosticCode,
    val message: String,
    val operationId: WorkspaceOperationId? = null,
    val entityId: SemanticEntityId? = null,
    val relationId: SemanticRelationId? = null,
)

sealed interface WorkspaceOperationResult {
    data class Success(
        val document: WorkspaceDocument,
        val resultingRevision: Long,
        val affectedEntityIds: Set<SemanticEntityId>,
        val affectedRelationIds: Set<SemanticRelationId>,
        val diagnostics: List<WorkspaceOperationDiagnostic> = emptyList(),
    ) : WorkspaceOperationResult

    data class Failure(
        val document: WorkspaceDocument,
        val diagnostic: WorkspaceOperationDiagnostic,
    ) : WorkspaceOperationResult
}

/** Public local command boundary over the M2 canonical mutation engine. */
object WorkspaceOperationExecutor {
    fun execute(
        document: WorkspaceDocument,
        operation: WorkspaceOperation,
        propertySchema: SemanticPropertySchema = CanonicalSemanticPropertySchema,
    ): WorkspaceOperationResult =
        execute(
            document,
            WorkspaceTransaction(
                transactionId = operation.operationId,
                operations = listOf(operation),
            ),
            propertySchema,
        )

    fun execute(
        document: WorkspaceDocument,
        transaction: WorkspaceTransaction,
        propertySchema: SemanticPropertySchema = CanonicalSemanticPropertySchema,
    ): WorkspaceOperationResult {
        val migration = CanonicalWorkspaceMigration.toCurrent(document)
        if (!migration.isValid) {
            return failure(
                document,
                WorkspaceOperationDiagnosticCode.VALIDATION_FAILED,
                migration.issues.firstOrNull()?.message ?: "Canonical workspace migration failed.",
            )
        }
        val baseline = migration.document
        if (transaction.expectedRevision != null && transaction.expectedRevision != baseline.version) {
            return failure(
                document,
                WorkspaceOperationDiagnosticCode.REVISION_MISMATCH,
                "Expected revision ${transaction.expectedRevision}, but found ${baseline.version}.",
            )
        }
        val duplicateOperationId = transaction.operations
            .groupBy(WorkspaceOperation::operationId)
            .entries
            .firstOrNull { it.value.size > 1 }
            ?.key
        if (duplicateOperationId != null) {
            return failure(
                document,
                WorkspaceOperationDiagnosticCode.DUPLICATE_ID,
                "Operation ID ${duplicateOperationId.value} occurs more than once.",
                operationId = duplicateOperationId,
            )
        }

        transaction.operations.filterIsInstance<WorkspaceOperation.SetProperty>().forEach { operation ->
            val issue = propertySchema.validate(
                baseline,
                operation.target,
                operation.property,
                operation.value,
            ) ?: return@forEach
            return WorkspaceOperationResult.Failure(
                document = document,
                diagnostic = WorkspaceOperationDiagnostic(
                    code = issue.code.toOperationCode(),
                    message = issue.message,
                    operationId = operation.operationId,
                    entityId = operation.target,
                ),
            )
        }

        val mutations = transaction.operations.map(::toCanonicalMutation)
        return when (val result = CanonicalWorkspaceMutator.apply(baseline, mutations)) {
            is CanonicalMutationResult.Applied -> success(baseline, result.document, transaction.operations)
            is CanonicalMutationResult.Rejected -> {
                val issue = result.issues.firstOrNull()
                    ?: CanonicalValidationIssue("validation.failed", "Canonical mutation was rejected.")
                val operation = result.failedMutationIndex?.let(transaction.operations::getOrNull)
                WorkspaceOperationResult.Failure(
                    document = document,
                    diagnostic = WorkspaceOperationDiagnostic(
                        code = issue.toOperationCode(),
                        message = issue.message,
                        operationId = operation?.operationId,
                        entityId = issue.entityId ?: operation?.targetEntityId(),
                        relationId = issue.relationId ?: operation?.targetRelationId(),
                    ),
                )
            }
        }
    }

    private fun toCanonicalMutation(operation: WorkspaceOperation): CanonicalMutation = when (operation) {
        is WorkspaceOperation.CreateEntity -> CanonicalMutation.CreateEntity(operation.entity)
        is WorkspaceOperation.DeleteEntity -> CanonicalMutation.DeleteEntity(operation.entityId)
        is WorkspaceOperation.CreateRelation -> CanonicalMutation.CreateRelation(operation.relation)
        is WorkspaceOperation.RemoveRelation -> CanonicalMutation.RemoveRelation(operation.relationId)
        is WorkspaceOperation.SetOrder -> CanonicalMutation.SetRelationOrder(operation.relationId, operation.order)
        is WorkspaceOperation.SetProperty -> CanonicalMutation.SetProperty(operation.target, operation.property, operation.value)
    }

    private fun success(
        before: WorkspaceDocument,
        after: WorkspaceDocument,
        operations: List<WorkspaceOperation>,
    ): WorkspaceOperationResult.Success {
        val beforeCanonical = requireNotNull(before.canonical)
        val afterCanonical = requireNotNull(after.canonical)
        val beforeEntities = beforeCanonical.entities.associateBy { it.ref.id }
        val afterEntities = afterCanonical.entities.associateBy { it.ref.id }
        val beforeRelations = beforeCanonical.relations.associateBy(SemanticRelation::id)
        val afterRelations = afterCanonical.relations.associateBy(SemanticRelation::id)
        val explicitPropertyTargets = operations.filterIsInstance<WorkspaceOperation.SetProperty>().mapTo(mutableSetOf()) { it.target }
        val affectedEntities = (beforeEntities.keys + afterEntities.keys).filterTo(explicitPropertyTargets) {
            beforeEntities[it] != afterEntities[it]
        }
        val affectedRelations = (beforeRelations.keys + afterRelations.keys).filterTo(mutableSetOf()) {
            beforeRelations[it] != afterRelations[it]
        }
        return WorkspaceOperationResult.Success(
            document = after,
            resultingRevision = after.version,
            affectedEntityIds = affectedEntities,
            affectedRelationIds = affectedRelations,
        )
    }

    private fun failure(
        document: WorkspaceDocument,
        code: WorkspaceOperationDiagnosticCode,
        message: String,
        operationId: WorkspaceOperationId? = null,
    ): WorkspaceOperationResult.Failure = WorkspaceOperationResult.Failure(
        document = document,
        diagnostic = WorkspaceOperationDiagnostic(code, message, operationId),
    )

    private fun WorkspaceOperation.targetEntityId(): SemanticEntityId? = when (this) {
        is WorkspaceOperation.CreateEntity -> entity.ref.id
        is WorkspaceOperation.DeleteEntity -> entityId
        is WorkspaceOperation.SetProperty -> target
        else -> null
    }

    private fun WorkspaceOperation.targetRelationId(): SemanticRelationId? = when (this) {
        is WorkspaceOperation.CreateRelation -> relation.id
        is WorkspaceOperation.RemoveRelation -> relationId
        is WorkspaceOperation.SetOrder -> relationId
        else -> null
    }

    private fun CanonicalValidationIssue.toOperationCode(): WorkspaceOperationDiagnosticCode = when (code) {
        "mutation.entity.missing", "property.entity.missing" -> WorkspaceOperationDiagnosticCode.ENTITY_NOT_FOUND
        "mutation.relation.missing" -> WorkspaceOperationDiagnosticCode.RELATION_NOT_FOUND
        "property.missing" -> WorkspaceOperationDiagnosticCode.PROPERTY_NOT_FOUND
        "property.type.invalid", "property.null.invalid" -> WorkspaceOperationDiagnosticCode.INVALID_PROPERTY_TYPE
        "mutation.order.invalid", "containment.order.duplicate" -> WorkspaceOperationDiagnosticCode.INVALID_ORDER
        "mutation.entity.exists", "mutation.relation.exists", "duplicate.entity", "duplicate.relation" -> WorkspaceOperationDiagnosticCode.DUPLICATE_ID
        "relation.source.missing", "relation.target.missing", "property.reference.missing" -> WorkspaceOperationDiagnosticCode.DANGLING_REFERENCE
        "relation.incompatible", "relation.semantic.duplicate", "sequence.outgoing.duplicate", "sequence.incoming.duplicate" -> WorkspaceOperationDiagnosticCode.INVALID_RELATION
        else -> WorkspaceOperationDiagnosticCode.VALIDATION_FAILED
    }

    private fun SemanticPropertyValidationCode.toOperationCode(): WorkspaceOperationDiagnosticCode = when (this) {
        SemanticPropertyValidationCode.ENTITY_NOT_FOUND -> WorkspaceOperationDiagnosticCode.ENTITY_NOT_FOUND
        SemanticPropertyValidationCode.PROPERTY_NOT_FOUND -> WorkspaceOperationDiagnosticCode.PROPERTY_NOT_FOUND
        SemanticPropertyValidationCode.PROPERTY_NOT_WRITABLE -> WorkspaceOperationDiagnosticCode.PROPERTY_NOT_WRITABLE
        SemanticPropertyValidationCode.STRUCTURAL_PROPERTY -> WorkspaceOperationDiagnosticCode.STRUCTURAL_PROPERTY_REQUIRED
        SemanticPropertyValidationCode.INVALID_TYPE,
        SemanticPropertyValidationCode.NULL_NOT_ALLOWED,
        -> WorkspaceOperationDiagnosticCode.INVALID_PROPERTY_TYPE
        SemanticPropertyValidationCode.CONSTRAINT_VIOLATION -> WorkspaceOperationDiagnosticCode.PROPERTY_CONSTRAINT_VIOLATION
    }
}
