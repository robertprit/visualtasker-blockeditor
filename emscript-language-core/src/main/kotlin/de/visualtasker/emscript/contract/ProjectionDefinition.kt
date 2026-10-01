package de.visualtasker.emscript.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class ProjectionExtent {
    POINT,
    RANGE_START,
    RANGE_END,
}

@Serializable
data class ProjectionDefinition(
    val id: ProjectionId,
    val canonicalName: String,
    val extent: ProjectionExtent,
    val stablePairIdRequired: Boolean,
    val lifecycle: DefinitionLifecycle,
    val documentation: String,
) {
    @Transient
    val semanticClass: SemanticClass = SemanticClass.PROJECTION

    @Transient
    val runtimeDispatchable: Boolean = false
}
