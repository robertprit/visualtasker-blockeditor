package de.visualtasker.blockeditor.registry

enum class NullableQueryResultState {
    VALUE,
    EMPTY_VALUE,
    ABSENT,
    FAILURE,
    PERMISSION_DENIED,
    INVALID_ARGUMENT,
}

enum class NullableQueryModel {
    STRING_WITH_RUNTIME_FAILURE,
    NULLABLE_STRING,
    OPTION,
    CALLER_DEFAULT,
    STRUCTURED_RESULT,
}

data class NullableQueryContractDecision(
    val stableId: String,
    val returnType: String,
    val supportedStates: Set<NullableQueryResultState>,
    val absentMeaning: String,
    val emptyMeaning: String,
    val failureMeaning: String,
    val defaultParameter: Boolean,
    val selectedModel: NullableQueryModel,
)

/**
 * M1B-3K freezes semantics only. These decisions deliberately do not alter
 * CommandDefinition, Workspace projection, IR or runtime transport.
 */
object NullableQuerySemanticsAudit {
    val FILE_READ_TEXT = NullableQueryContractDecision(
        stableId = "file.readText",
        returnType = "String?",
        supportedStates = linkedSetOf(
            NullableQueryResultState.VALUE,
            NullableQueryResultState.EMPTY_VALUE,
            NullableQueryResultState.ABSENT,
            NullableQueryResultState.FAILURE,
            NullableQueryResultState.PERMISSION_DENIED,
            NullableQueryResultState.INVALID_ARGUMENT,
        ),
        absentMeaning = "The resolved path does not identify an existing regular file.",
        emptyMeaning = "The existing readable file contains zero characters.",
        failureMeaning = "Permission, I/O and other read failures use the runtime diagnostic/failure channel.",
        defaultParameter = false,
        selectedModel = NullableQueryModel.NULLABLE_STRING,
    )

    val DATASTORE_GET = NullableQueryContractDecision(
        stableId = "system.datastoreGet",
        returnType = "String?",
        supportedStates = linkedSetOf(
            NullableQueryResultState.VALUE,
            NullableQueryResultState.EMPTY_VALUE,
            NullableQueryResultState.ABSENT,
            NullableQueryResultState.FAILURE,
        ),
        absentMeaning = "The requested key is not present in the available datastore.",
        emptyMeaning = "The requested key is present and its stored value is the empty String.",
        failureMeaning = "Store availability and read failures use the runtime diagnostic/failure channel.",
        defaultParameter = false,
        selectedModel = NullableQueryModel.NULLABLE_STRING,
    )

    val ALL = listOf(FILE_READ_TEXT, DATASTORE_GET)

    fun validate(queryAudit: QueryReturnContractAudit = QueryReturnContractAudit) {
        require(ALL.map { it.stableId }.toSet() == setOf("file.readText", "system.datastoreGet"))
        require(ALL.all { it.returnType == "String?" })
        require(ALL.all { it.selectedModel == NullableQueryModel.NULLABLE_STRING })
        require(ALL.none { it.defaultParameter })
        require(ALL.all { NullableQueryResultState.EMPTY_VALUE in it.supportedStates })
        require(ALL.all { NullableQueryResultState.ABSENT in it.supportedStates })
        require(ALL.all { NullableQueryResultState.FAILURE in it.supportedStates })
        require(ALL.all { it.emptyMeaning != it.absentMeaning })
        require(ALL.all { it.absentMeaning != it.failureMeaning })
        require(ALL.all { decision ->
            decision.stableId in queryAudit.MIGRATED_M1B_3M &&
                VisualTaskerCommandCatalog.findById(decision.stableId)?.returnType == decision.returnType
        })
    }
}
