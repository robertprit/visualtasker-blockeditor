package de.visualtasker.blockeditor.registry

enum class TemplateCompareResultKind { VALUE, ABSENT, FAILURE, NOT_APPLICABLE }

data class TemplateCompareResultState(
    val state: String,
    val currentBehavior: String,
    val v1Kind: TemplateCompareResultKind,
    val diagnostic: String = "",
    val evidence: String,
)

data class TemplateCompareNullOrigin(
    val source: String,
    val trigger: String,
    val intendedMeaning: String,
    val currentDownstreamInterpretation: String,
)

/** M1B-3P contract evidence for the converged template comparison query. */
object TemplateCompareSemantics {
    const val STABLE_ID = "vision.templateCompare"
    const val CANONICAL_NAME = "templateCompare"
    const val RETURN_TYPE = "Number"
    const val SEMANTIC_NAME = "normalized mean absolute grayscale similarity"
    const val FORMULA = "clamp(1 - mean(abs(process(live) - process(reference))) / 255, 0, 1)"
    const val MIN_SCORE = 0.0
    const val MAX_SCORE = 1.0
    const val INTERNAL_REPRESENTATION = "Kotlin Float; EMScript Number"
    const val THRESHOLD_CONTRACT = "No acceptance threshold. Mask preprocessing uses an internal 0.5 luminance cutoff."

    val aliases = listOf("Template.compare", "COMPARE_TEMPLATE")
    val parameters = listOf("name:String", "region:Region", "processing:String=grayscale")
    val diagnostics = linkedSetOf(
        "TEMPLATE_NOT_FOUND",
        "TEMPLATE_IMAGE_UNAVAILABLE",
        "TEMPLATE_REGION_UNAVAILABLE",
        "TEMPLATE_COMPARE_FAILED",
    )

    val states = listOf(
        TemplateCompareResultState(
            state = "valid comparison",
            currentBehavior = "Returns a clamped Float score and renders score * 100 as a percentage.",
            v1Kind = TemplateCompareResultKind.VALUE,
            evidence = "Both bitmap regions are sampled on a shared grid and compared pixel by pixel.",
        ),
        TemplateCompareResultState(
            state = "score below a caller threshold",
            currentBehavior = "No such state exists; templateCompare has no acceptance-threshold parameter.",
            v1Kind = TemplateCompareResultKind.NOT_APPLICABLE,
            evidence = "The catalog signature contains name, region and processing only.",
        ),
        TemplateCompareResultState(
            state = "no match",
            currentBehavior = "No search or match-acceptance step exists; every completed comparison yields a score.",
            v1Kind = TemplateCompareResultKind.NOT_APPLICABLE,
            evidence = "Unlike findTemplate, templateCompare never filters the score against a threshold.",
        ),
        TemplateCompareResultState(
            state = "missing template",
            currentBehavior = "Workspace adapter raises TEMPLATE_NOT_FOUND through the structured runtime failure channel.",
            v1Kind = TemplateCompareResultKind.FAILURE,
            diagnostic = "TEMPLATE_NOT_FOUND",
            evidence = "A named saved template is a required comparison input, not an optional search result.",
        ),
        TemplateCompareResultState(
            state = "missing or undecodable image/frame",
            currentBehavior = "Workspace adapter raises TEMPLATE_IMAGE_UNAVAILABLE through the structured runtime failure channel.",
            v1Kind = TemplateCompareResultKind.FAILURE,
            diagnostic = "TEMPLATE_IMAGE_UNAVAILABLE",
            evidence = "Without both image inputs no comparison was performed.",
        ),
        TemplateCompareResultState(
            state = "unusable comparison region",
            currentBehavior = "Workspace adapter raises TEMPLATE_REGION_UNAVAILABLE through the structured runtime failure channel.",
            v1Kind = TemplateCompareResultKind.FAILURE,
            diagnostic = "TEMPLATE_REGION_UNAVAILABLE",
            evidence = "Without both usable regions no comparison was performed.",
        ),
        TemplateCompareResultState(
            state = "invalid input",
            currentBehavior = "No distinct state is evidenced: dimensions are normalized, coordinates clamped and unknown processing maps to Original.",
            v1Kind = TemplateCompareResultKind.NOT_APPLICABLE,
            evidence = "The current boundary normalizes these inputs instead of producing a separate result state.",
        ),
        TemplateCompareResultState(
            state = "backend failure",
            currentBehavior = "Comparison and adapter failures raise TEMPLATE_COMPARE_FAILED without producing a value.",
            v1Kind = TemplateCompareResultKind.FAILURE,
            diagnostic = "TEMPLATE_COMPARE_FAILED",
            evidence = "Technical failure is carried by the structured runtime failure channel, never by Number or absent.",
        ),
    )

    val nullOrigins = listOf(
        TemplateCompareNullOrigin(
            source = "WorkspaceScreen template marker lookup",
            trigger = "No saved Template marker matches name or id.",
            intendedMeaning = "Required reference template is unavailable.",
            currentDownstreamInterpretation = "TEMPLATE_NOT_FOUND structured runtime failure",
        ),
        TemplateCompareNullOrigin(
            source = "decodeScreenshotBitmap / compareScreenshotRegions liveSafe",
            trigger = "No selected/live asset, missing file, decode failure or unusable live region.",
            intendedMeaning = "Required live image evidence is unavailable.",
            currentDownstreamInterpretation = "TEMPLATE_IMAGE_UNAVAILABLE or TEMPLATE_REGION_UNAVAILABLE structured runtime failure",
        ),
        TemplateCompareNullOrigin(
            source = "decodeScreenshotBitmap / compareScreenshotRegions referenceSafe",
            trigger = "No reference asset, missing file, decode failure or unusable reference region.",
            intendedMeaning = "Required reference image evidence is unavailable.",
            currentDownstreamInterpretation = "TEMPLATE_IMAGE_UNAVAILABLE or TEMPLATE_REGION_UNAVAILABLE structured runtime failure",
        ),
        TemplateCompareNullOrigin(
            source = "compareScreenshotRegions defensive sample count",
            trigger = "No samples were produced; unreachable with the current at-least-one sampling bounds.",
            intendedMeaning = "Comparison could not be performed.",
            currentDownstreamInterpretation = "TEMPLATE_COMPARE_FAILED structured runtime failure",
        ),
        TemplateCompareNullOrigin(
            source = "WorkspaceBasicRuntimeEnvironment default adapter",
            trigger = "No production environment implementation was supplied.",
            intendedMeaning = "Vision runtime adapter is unavailable.",
            currentDownstreamInterpretation = "TEMPLATE_COMPARE_FAILED structured runtime failure",
        ),
    )

    fun validate() {
        require(MIN_SCORE == 0.0 && MAX_SCORE == 1.0)
        require(states.count { it.v1Kind == TemplateCompareResultKind.VALUE } == 1)
        require(states.none { it.v1Kind == TemplateCompareResultKind.ABSENT })
        require(states.filter { it.v1Kind == TemplateCompareResultKind.FAILURE }.all { it.diagnostic.isNotBlank() })
        require(diagnostics == states.mapNotNullTo(linkedSetOf()) { it.diagnostic.takeIf(String::isNotBlank) })
    }
}
