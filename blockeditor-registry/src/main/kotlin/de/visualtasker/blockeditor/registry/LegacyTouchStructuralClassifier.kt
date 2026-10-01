package de.visualtasker.blockeditor.registry

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

enum class LegacyTouchClassification {
    LOSSLESS,
    PARTIAL,
    UNKNOWN,
}

enum class LegacyTouchEvidence {
    HAS_COORDINATES,
    HAS_DOWN,
    HAS_MOVE,
    HAS_UP,
    HAS_TIMING,
    HAS_POINTER_ID,
    HAS_MULTI_POINTER_STRUCTURE,
    HAS_SEQUENCE_BOUNDARIES,
    HAS_UNKNOWN_TOKEN,
}

enum class LegacyTouchDiagnosticCode {
    UNKNOWN_ALIAS,
    EMPTY_PAYLOAD,
    MALFORMED_PAYLOAD,
    UNKNOWN_COORDINATE_SPACE,
    UNKNOWN_TOKEN,
    AMBIGUOUS_STRUCTURE,
    MISSING_TIMING,
    MISSING_POINTER_ID,
    ODD_COORDINATE_COUNT,
}

enum class LegacyTouchMigrationReadiness {
    LOSSLESS,
    REQUIRES_STRUCTURAL_MIGRATION,
    PRESERVE_LEGACY,
}

enum class LegacyTouchTokenKind {
    NUMBER,
    STRING,
    BOOLEAN,
    NULL,
    ARRAY_START,
    ARRAY_END,
    OBJECT_START,
    OBJECT_END,
}

data class LegacyTouchToken(
    val kind: LegacyTouchTokenKind,
    val raw: String,
)

data class LegacyTouchCoordinate(
    val x: Double,
    val y: Double,
    val rawX: String,
    val rawY: String,
)

data class LegacyTouchDiagnostic(
    val code: LegacyTouchDiagnosticCode,
    val detail: String,
)

data class LegacyTouchStructure(
    val rawPayload: String,
    val alias: String,
    val tokens: List<LegacyTouchToken>,
    val coordinates: List<LegacyTouchCoordinate>,
    val evidence: Set<LegacyTouchEvidence>,
    val classification: LegacyTouchClassification,
    val migrationReadiness: LegacyTouchMigrationReadiness,
    val diagnostics: List<LegacyTouchDiagnostic>,
)

object LegacyTouchStructuralClassifier {
    val acceptedAliases: Set<String> = linkedSetOf("touch", "Touch.dispatch", "TOUCH")

    private val json = Json { isLenient = false }
    private val atomicSequence = Regex(
        pattern = """(?i)^\s*(down|move|up)\s*\(\s*(-?(?:\d+(?:\.\d+)?|\.\d+))\s*,\s*(-?(?:\d+(?:\.\d+)?|\.\d+))\s*\)(?:\s*;\s*(down|move|up)\s*\(\s*(-?(?:\d+(?:\.\d+)?|\.\d+))\s*,\s*(-?(?:\d+(?:\.\d+)?|\.\d+))\s*\))*\s*$""",
    )
    private val atomicCall = Regex(
        pattern = """(?i)(down|move|up)\s*\(\s*(-?(?:\d+(?:\.\d+)?|\.\d+))\s*,\s*(-?(?:\d+(?:\.\d+)?|\.\d+))\s*\)""",
    )

    fun classify(alias: String, rawPayload: String): LegacyTouchStructure {
        val accumulator = Accumulator()
        val diagnostics = mutableListOf<LegacyTouchDiagnostic>()

        if (alias !in acceptedAliases) {
            diagnostics += diagnostic(LegacyTouchDiagnosticCode.UNKNOWN_ALIAS, "Unrecognized legacy touch alias: $alias")
        }
        if (rawPayload.isBlank()) {
            diagnostics += diagnostic(LegacyTouchDiagnosticCode.EMPTY_PAYLOAD, "Legacy touch payload is empty.")
            return result(alias, rawPayload, accumulator, diagnostics)
        }

        val element = runCatching { json.parseToJsonElement(rawPayload) }.getOrElse {
            diagnostics += diagnostic(LegacyTouchDiagnosticCode.MALFORMED_PAYLOAD, "Legacy touch payload is not valid JSON-like argument data.")
            return result(alias, rawPayload, accumulator, diagnostics)
        }
        inspect(element, accumulator, diagnostics, keyHint = null)
        if (accumulator.numberTokens.size % 2 != 0) {
            diagnostics += diagnostic(LegacyTouchDiagnosticCode.ODD_COORDINATE_COUNT, "Numeric payload members cannot all form coordinate pairs.")
        }
        accumulator.numberTokens.chunked(2).forEach { pair ->
            if (pair.size == 2) {
                accumulator.coordinates += LegacyTouchCoordinate(
                    x = pair[0].value,
                    y = pair[1].value,
                    rawX = pair[0].raw,
                    rawY = pair[1].raw,
                )
            }
        }
        return result(alias, rawPayload, accumulator, diagnostics)
    }

    fun classifyInvocation(invocation: String): LegacyTouchStructure {
        val opening = invocation.indexOf('(')
        val closing = invocation.lastIndexOf(')')
        if (opening <= 0 || closing <= opening || invocation.substring(closing + 1).isNotBlank()) {
            return classify("", invocation).copy(
                diagnostics = listOf(
                    diagnostic(LegacyTouchDiagnosticCode.MALFORMED_PAYLOAD, "Legacy touch invocation is malformed."),
                ),
            )
        }
        return classify(
            alias = invocation.substring(0, opening).trim(),
            rawPayload = invocation.substring(opening + 1, closing),
        )
    }

    private fun inspect(
        element: JsonElement,
        accumulator: Accumulator,
        diagnostics: MutableList<LegacyTouchDiagnostic>,
        keyHint: String?,
    ) {
        when (element) {
            JsonNull -> accumulator.tokens += LegacyTouchToken(LegacyTouchTokenKind.NULL, "null")
            is JsonArray -> {
                accumulator.tokens += LegacyTouchToken(LegacyTouchTokenKind.ARRAY_START, "[")
                element.forEach { inspect(it, accumulator, diagnostics, keyHint) }
                accumulator.tokens += LegacyTouchToken(LegacyTouchTokenKind.ARRAY_END, "]")
            }
            is JsonObject -> {
                accumulator.tokens += LegacyTouchToken(LegacyTouchTokenKind.OBJECT_START, "{")
                element.forEach { (key, value) ->
                    val normalizedKey = key.lowercase()
                    when (normalizedKey) {
                        "duration", "durationms", "delay", "delayms", "hold", "holdms", "timestamp", "timestampms", "time", "speed" ->
                            accumulator.evidence += LegacyTouchEvidence.HAS_TIMING
                        "pointerid", "pointer_id", "fingerindex", "touchindex" ->
                            accumulator.evidence += LegacyTouchEvidence.HAS_POINTER_ID
                        "pointers", "multipointer", "multitouch" ->
                            accumulator.evidence += LegacyTouchEvidence.HAS_MULTI_POINTER_STRUCTURE
                        "gestures", "sequenceboundaries", "gesturesequence" ->
                            accumulator.evidence += LegacyTouchEvidence.HAS_SEQUENCE_BOUNDARIES
                    }
                    inspect(value, accumulator, diagnostics, normalizedKey)
                }
                accumulator.tokens += LegacyTouchToken(LegacyTouchTokenKind.OBJECT_END, "}")
            }
            is JsonPrimitive -> inspectPrimitive(element, accumulator, diagnostics, keyHint)
        }
    }

    private fun inspectPrimitive(
        primitive: JsonPrimitive,
        accumulator: Accumulator,
        diagnostics: MutableList<LegacyTouchDiagnostic>,
        keyHint: String?,
    ) {
        primitive.doubleOrNull?.let { value ->
            val raw = primitive.toString()
            accumulator.tokens += LegacyTouchToken(LegacyTouchTokenKind.NUMBER, raw)
            if (!keyHint.isMetadataKey()) accumulator.numberTokens += NumberToken(value, raw)
            return
        }
        primitive.booleanOrNull?.let {
            accumulator.tokens += LegacyTouchToken(LegacyTouchTokenKind.BOOLEAN, primitive.toString())
            markUnknown(primitive.toString(), accumulator, diagnostics)
            return
        }
        val content = primitive.contentOrNull.orEmpty()
        accumulator.tokens += LegacyTouchToken(LegacyTouchTokenKind.STRING, primitive.toString())
        when (content.lowercase()) {
            "down" -> accumulator.evidence += LegacyTouchEvidence.HAS_DOWN
            "move" -> accumulator.evidence += LegacyTouchEvidence.HAS_MOVE
            "up" -> accumulator.evidence += LegacyTouchEvidence.HAS_UP
            else -> if (!inspectAtomicSequence(content, accumulator)) {
                markUnknown(content, accumulator, diagnostics)
            }
        }
    }

    private fun inspectAtomicSequence(content: String, accumulator: Accumulator): Boolean {
        if (!atomicSequence.matches(content)) return false
        atomicCall.findAll(content).forEach { match ->
            when (match.groupValues[1].lowercase()) {
                "down" -> accumulator.evidence += LegacyTouchEvidence.HAS_DOWN
                "move" -> accumulator.evidence += LegacyTouchEvidence.HAS_MOVE
                "up" -> accumulator.evidence += LegacyTouchEvidence.HAS_UP
            }
            accumulator.coordinates += LegacyTouchCoordinate(
                x = match.groupValues[2].toDouble(),
                y = match.groupValues[3].toDouble(),
                rawX = match.groupValues[2],
                rawY = match.groupValues[3],
            )
        }
        return true
    }

    private fun result(
        alias: String,
        rawPayload: String,
        accumulator: Accumulator,
        diagnostics: MutableList<LegacyTouchDiagnostic>,
    ): LegacyTouchStructure {
        if (accumulator.coordinates.isNotEmpty()) {
            accumulator.evidence += LegacyTouchEvidence.HAS_COORDINATES
            diagnostics.addOnce(LegacyTouchDiagnosticCode.UNKNOWN_COORDINATE_SPACE, "Legacy payload does not identify its coordinate space.")
        }
        val hasAtomicState = accumulator.evidence.any {
            it in setOf(LegacyTouchEvidence.HAS_DOWN, LegacyTouchEvidence.HAS_MOVE, LegacyTouchEvidence.HAS_UP)
        }
        if (hasAtomicState && LegacyTouchEvidence.HAS_TIMING !in accumulator.evidence) {
            diagnostics.addOnce(LegacyTouchDiagnosticCode.MISSING_TIMING, "No gesture timing is present for the atomic touch states.")
        }
        if (hasAtomicState && LegacyTouchEvidence.HAS_POINTER_ID !in accumulator.evidence) {
            diagnostics.addOnce(LegacyTouchDiagnosticCode.MISSING_POINTER_ID, "No pointer identity is present for the atomic touch states.")
        }

        val hasRecognizedStructure = accumulator.coordinates.isNotEmpty() || hasAtomicState || accumulator.evidence.any {
            it in setOf(
                LegacyTouchEvidence.HAS_TIMING,
                LegacyTouchEvidence.HAS_POINTER_ID,
                LegacyTouchEvidence.HAS_MULTI_POINTER_STRUCTURE,
                LegacyTouchEvidence.HAS_SEQUENCE_BOUNDARIES,
            )
        }
        val classification = when {
            !hasRecognizedStructure -> LegacyTouchClassification.UNKNOWN
            else -> LegacyTouchClassification.PARTIAL
        }
        if (classification == LegacyTouchClassification.PARTIAL) {
            diagnostics.addOnce(
                LegacyTouchDiagnosticCode.AMBIGUOUS_STRUCTURE,
                "Recognized members do not prove one complete legacy gesture model.",
            )
        }
        val readiness = when (classification) {
            LegacyTouchClassification.LOSSLESS -> LegacyTouchMigrationReadiness.LOSSLESS
            LegacyTouchClassification.PARTIAL -> LegacyTouchMigrationReadiness.REQUIRES_STRUCTURAL_MIGRATION
            LegacyTouchClassification.UNKNOWN -> LegacyTouchMigrationReadiness.PRESERVE_LEGACY
        }
        return LegacyTouchStructure(
            rawPayload = rawPayload,
            alias = alias,
            tokens = accumulator.tokens.toList(),
            coordinates = accumulator.coordinates.toList(),
            evidence = accumulator.evidence.toSet(),
            classification = classification,
            migrationReadiness = readiness,
            diagnostics = diagnostics.distinctBy { it.code to it.detail },
        )
    }

    private fun markUnknown(
        token: String,
        accumulator: Accumulator,
        diagnostics: MutableList<LegacyTouchDiagnostic>,
    ) {
        accumulator.evidence += LegacyTouchEvidence.HAS_UNKNOWN_TOKEN
        diagnostics += diagnostic(LegacyTouchDiagnosticCode.UNKNOWN_TOKEN, "Unknown legacy touch token: $token")
    }

    private fun String?.isMetadataKey(): Boolean = this in setOf(
        "duration", "durationms", "delay", "delayms", "hold", "holdms", "timestamp", "timestampms", "time", "speed",
        "pointerid", "pointer_id", "fingerindex", "touchindex", "coordinatespace",
    )

    private fun MutableList<LegacyTouchDiagnostic>.addOnce(code: LegacyTouchDiagnosticCode, detail: String) {
        if (none { it.code == code }) add(diagnostic(code, detail))
    }

    private fun diagnostic(code: LegacyTouchDiagnosticCode, detail: String) = LegacyTouchDiagnostic(code, detail)

    private data class NumberToken(val value: Double, val raw: String)

    private class Accumulator {
        val tokens = mutableListOf<LegacyTouchToken>()
        val coordinates = mutableListOf<LegacyTouchCoordinate>()
        val numberTokens = mutableListOf<NumberToken>()
        val evidence = linkedSetOf<LegacyTouchEvidence>()
    }
}
