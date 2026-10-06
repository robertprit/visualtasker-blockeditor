package de.visualtasker.workflow.core

import kotlinx.serialization.Serializable

/** Stable identity of an authoring-level semantic entity, independent of any editor projection. */
@JvmInline
@Serializable
value class SemanticEntityId(val value: String) {
    init {
        require(value.isNotBlank()) { "Semantic entity ID must not be blank." }
    }
}

/** Stable identity of an authoring-level structural relation. */
@JvmInline
@Serializable
value class SemanticRelationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Semantic relation ID must not be blank." }
    }
}

@Serializable
enum class SemanticEntityKind {
    Workflow,
    Statement,
    Expression,
    Branch,
    Variable,
}

@Serializable
data class SemanticEntityRef(
    val id: SemanticEntityId,
    val kind: SemanticEntityKind,
)

/**
 * The deliberately small authoring relation algebra. Flow merges, routes and connector geometry are projections.
 */
@Serializable
enum class SemanticRelationKind {
    Sequence,
    Expression,
    Containment,
    Reference,
}

@Serializable
enum class SemanticRelationRoleKind {
    Next,
    Root,
    Value,
    Condition,
    Argument,
    Operand,
    Branch,
    Body,
    Variable,
}

@Serializable
enum class SemanticBranchRole {
    Then,
    ElseIf,
    Else,
    LoopBody,
}

/**
 * Qualifies a fundamental relation without turning every slot or branch variant into a relation kind.
 * [name] carries stable language/catalog slot names. [branchRole] is only valid for [SemanticRelationRoleKind.Branch].
 */
@Serializable
data class SemanticRelationRole(
    val kind: SemanticRelationRoleKind,
    val name: String? = null,
    val branchRole: SemanticBranchRole? = null,
) {
    init {
        if (kind == SemanticRelationRoleKind.Argument) {
            require(!name.isNullOrBlank()) { "Argument roles require a stable slot name." }
        }
        if (kind == SemanticRelationRoleKind.Branch) {
            require(branchRole != null) { "Branch roles require a semantic branch role." }
        } else {
            require(branchRole == null) { "branchRole is only valid for Branch roles." }
        }
    }
}

/**
 * A projection-independent structural fact. [order] is deliberately separate from [id]: reordering must not
 * change relation or entity identity. Source and target direction follows authoring ownership (owner -> member).
 */
@Serializable
data class SemanticRelation(
    val id: SemanticRelationId,
    val kind: SemanticRelationKind,
    val source: SemanticEntityRef,
    val target: SemanticEntityRef,
    val role: SemanticRelationRole,
    val order: Int? = null,
) {
    init {
        require(order == null || order >= 0) { "Relation order must be null or non-negative." }
    }
}
