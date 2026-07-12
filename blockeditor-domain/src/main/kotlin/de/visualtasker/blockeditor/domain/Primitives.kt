package de.visualtasker.blockeditor.domain

@JvmInline
value class BlockId(val value: String)

@JvmInline
value class ConnectionId(val value: String)

enum class ConnectionKind {
    Previous,
    Next,
    Output,
    ValueInput,
    StatementInput,
}

data class Rect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    fun contains(px: Float, py: Float): Boolean =
        px >= x && px <= right && py >= y && py <= bottom
}

data class Offset2(val x: Float, val y: Float)

sealed interface FieldValue {
    data class Text(val value: String) : FieldValue
    data class Number(val value: Double) : FieldValue
    data class Bool(val value: kotlin.Boolean) : FieldValue
}

fun FieldValue.asString(): String = when (this) {
    is FieldValue.Text -> value
    is FieldValue.Number -> value.toString()
    is FieldValue.Bool -> value.toString()
}

fun String.toFieldValue(): FieldValue = toDoubleOrNull()?.let { FieldValue.Number(it) }
    ?: when (lowercase()) {
        "true" -> FieldValue.Bool(true)
        "false" -> FieldValue.Bool(false)
        else -> FieldValue.Text(this)
    }
