package de.visualtasker.blockeditor.registry

enum class ChromeTabSupportResultKind { TRUE, FALSE, FAILURE, NOT_APPLICABLE }

data class ChromeTabSupportState(
    val state: String,
    val currentBehavior: String,
    val v1Kind: ChromeTabSupportResultKind,
    val diagnostic: String = "",
    val evidence: String,
)

/** M1B-3Q evidence retained after the bounded M1B-3R convergence. */
object ChromeTabSupportedSemantics {
    const val STABLE_ID = "chromeTab.isSupported"
    const val LEGACY_CANONICAL_NAME = "ChromeTab.isSupported"
    const val PROPOSED_CANONICAL_NAME = "chromeTab.isSupported"
    const val PROPOSED_RETURN_TYPE = "Bool"
    const val SEMANTIC_DEFINITION =
        "Android can resolve at least one service for ACTION_CUSTOM_TABS_CONNECTION in the current package-manager context."
    const val FIRST_LOSS_POINT =
        "Resolved in M1B-3R: RuntimeAdapterResult now transports the Bool as EmscriptValue independently of execution success."
    const val MINIMAL_TRANSPORT_GAP =
        "Closed in M1B-3R by the additive RuntimeAdapterResult value payload and diagnostic code."

    val parameters: List<String> = emptyList()
    val diagnostics = linkedSetOf(
        "CHROME_TAB_ADAPTER_UNAVAILABLE",
        "CHROME_TAB_RESOLUTION_FAILED",
    )

    val states = listOf(
        ChromeTabSupportState(
            state = "custom tabs service resolvable",
            currentBehavior = "CustomChromeTabStatus.supported=true becomes successful EmscriptValue.BooleanValue(true).",
            v1Kind = ChromeTabSupportResultKind.TRUE,
            evidence = "CustomChromeTabRegistration.inspect found at least one service for ACTION_CUSTOM_TABS_CONNECTION.",
        ),
        ChromeTabSupportState(
            state = "no custom tabs service resolvable",
            currentBehavior = "CustomChromeTabStatus.supported=false becomes successful EmscriptValue.BooleanValue(false).",
            v1Kind = ChromeTabSupportResultKind.FALSE,
            evidence = "The package-manager query completed and returned no matching service; this is a successful negative capability answer.",
        ),
        ChromeTabSupportState(
            state = "host adapter unavailable",
            currentBehavior = "The default chromeTabCommand adapter returns a structured failure with CHROME_TAB_ADAPTER_UNAVAILABLE.",
            v1Kind = ChromeTabSupportResultKind.FAILURE,
            diagnostic = "CHROME_TAB_ADAPTER_UNAVAILABLE",
            evidence = "The capability question was not evaluated by a Custom Tabs adapter.",
        ),
        ChromeTabSupportState(
            state = "package-manager service resolution fails",
            currentBehavior = "queryIntentServices exceptions become CHROME_TAB_RESOLUTION_FAILED without a Boolean payload.",
            v1Kind = ChromeTabSupportResultKind.FAILURE,
            diagnostic = "CHROME_TAB_RESOLUTION_FAILED",
            evidence = "A technical resolver failure is not evidence that the capability is absent.",
        ),
        ChromeTabSupportState(
            state = "provider not configured",
            currentBehavior = "No selected-provider configuration participates in inspect.",
            v1Kind = ChromeTabSupportResultKind.NOT_APPLICABLE,
            evidence = "inspect queries all visible matching services and merely prefers com.android.chrome when present.",
        ),
        ChromeTabSupportState(
            state = "session or service connection absent",
            currentBehavior = "No CustomTabsSession or service binding participates in inspect.",
            v1Kind = ChromeTabSupportResultKind.NOT_APPLICABLE,
            evidence = "Support discovery is package-manager based and does not require an active connection.",
        ),
    )

    fun validate() {
        require(parameters.isEmpty())
        require(states.count { it.v1Kind == ChromeTabSupportResultKind.TRUE } == 1)
        require(states.count { it.v1Kind == ChromeTabSupportResultKind.FALSE } == 1)
        require(states.none { it.v1Kind.name == "ABSENT" })
        require(states.filter { it.v1Kind == ChromeTabSupportResultKind.FAILURE }.all { it.diagnostic.isNotBlank() })
        require(diagnostics == states.mapNotNullTo(linkedSetOf()) { it.diagnostic.takeIf(String::isNotBlank) })
    }
}
