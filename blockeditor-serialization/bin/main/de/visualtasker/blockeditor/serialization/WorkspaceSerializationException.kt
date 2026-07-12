package de.visualtasker.blockeditor.serialization

class WorkspaceSerializationException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
