package de.visualtasker.blockeditor.registry

/** M1B-3S freezes provider Boolean meaning without migrating any command. */
data class ProviderBoolSemanticDecision(
    val commandId: String,
    val currentCanonicalName: String,
    val aliases: List<String>,
    val parameters: List<String>,
    val currentReturnDeclaration: String,
    val trueMeaning: String,
    val falseMeaning: String,
    val legitimateAbsent: Boolean,
    val failureStates: List<String>,
    val currentBoolSource: String,
    val firstLossPoint: String,
    val falseCollision: List<String>,
    val permissionDependent: Boolean,
    val serviceDependent: Boolean,
    val binderDependent: Boolean,
    val proposedReturnType: String,
    val valueSourceReady: Boolean,
    val failureSemanticsReady: Boolean,
    val typedTransportReady: Boolean,
    val workspaceReady: Boolean,
    val irReady: Boolean,
    val migrationReady: Boolean,
    val diagnostics: List<String>,
    val evidence: List<String>,
)

object ProviderBoolSemanticsFreeze {
    const val SLICE = "M1B-3S"

    val decisions: List<ProviderBoolSemanticDecision> = listOf(
        ProviderBoolSemanticDecision(
            commandId = "tasker.isInstalled",
            currentCanonicalName = "Tasker.isInstalled",
            aliases = emptyList(),
            parameters = emptyList(),
            currentReturnDeclaration = "Void/unspecified",
            trueMeaning = "PackageManager resolved net.dinglisch.android.tasker or net.dinglisch.android.taskerm.",
            falseMeaning = "PackageManager definitively found neither supported Tasker package.",
            legitimateAbsent = false,
            failureStates = listOf("adapter unavailable", "package inspection failed technically"),
            currentBoolSource = "TaskerRegistrationStatus.installed",
            firstLossPoint = "TaskerRegistration.inspect converts every getPackageInfo exception to an unsuccessful candidate lookup; WorkspaceScreen later flattens the surviving Boolean into message text.",
            falseCollision = listOf("package absent", "package visibility or package-manager inspection failure"),
            permissionDependent = false,
            serviceDependent = false,
            binderDependent = false,
            proposedReturnType = "Bool",
            valueSourceReady = true,
            failureSemanticsReady = false,
            typedTransportReady = true,
            workspaceReady = true,
            irReady = true,
            migrationReady = false,
            diagnostics = listOf("TASKER_ADAPTER_UNAVAILABLE", "TASKER_INSTALLATION_CHECK_FAILED"),
            evidence = listOf(
                "TaskerRegistration.inspect checks two declared package IDs with PackageManager.getPackageInfo.",
                "TaskerRegistrationStatus.available separately combines permission, enabled preference, external access and receiver availability.",
            ),
        ),
        ProviderBoolSemanticDecision(
            commandId = "shizuku.isInstalled",
            currentCanonicalName = "Shizuku.isInstalled",
            aliases = emptyList(),
            parameters = emptyList(),
            currentReturnDeclaration = "Void/unspecified",
            trueMeaning = "PackageManager resolved moe.shizuku.privileged.api.",
            falseMeaning = "PackageManager definitively found no Shizuku package.",
            legitimateAbsent = false,
            failureStates = listOf("adapter unavailable", "package inspection failed technically"),
            currentBoolSource = "ShizukuRegistrationStatus.installed",
            firstLossPoint = "ShizukuRegistration.inspect converts every getPackageInfo exception to false; WorkspaceScreen later flattens the surviving Boolean into message text.",
            falseCollision = listOf("package absent", "package visibility or package-manager inspection failure"),
            permissionDependent = false,
            serviceDependent = false,
            binderDependent = false,
            proposedReturnType = "Bool",
            valueSourceReady = true,
            failureSemanticsReady = false,
            typedTransportReady = true,
            workspaceReady = true,
            irReady = true,
            migrationReady = false,
            diagnostics = listOf("SHIZUKU_ADAPTER_UNAVAILABLE", "SHIZUKU_INSTALLATION_CHECK_FAILED"),
            evidence = listOf(
                "ShizukuRegistration.inspect checks only SHIZUKU_PACKAGE for installed.",
                "Permission and binder state are separate fields and do not participate in installed.",
            ),
        ),
        ProviderBoolSemanticDecision(
            commandId = "shizuku.isAvailable",
            currentCanonicalName = "Shizuku.isAvailable",
            aliases = emptyList(),
            parameters = emptyList(),
            currentReturnDeclaration = "Void/unspecified",
            trueMeaning = "Shizuku is installed, its binder responds, and this app has Shizuku permission.",
            falseMeaning = "At least one successfully checked prerequisite is negative: package absent, binder inactive, or permission not granted.",
            legitimateAbsent = false,
            failureStates = listOf("adapter unavailable", "package inspection failed", "binder probe failed technically", "permission inspection failed technically"),
            currentBoolSource = "ShizukuRegistrationStatus.available = installed && permissionGranted && binderAlive",
            firstLossPoint = "ShizukuRegistration.inspect maps package and binder probe exceptions to false and permission probe exceptions to false on the live-binder path; WorkspaceScreen also uses the composite Boolean as execution success.",
            falseCollision = listOf("package absent", "binder inactive", "permission missing or denied", "package inspection failure", "binder API failure", "permission API failure"),
            permissionDependent = true,
            serviceDependent = true,
            binderDependent = true,
            proposedReturnType = "Bool",
            valueSourceReady = true,
            failureSemanticsReady = false,
            typedTransportReady = true,
            workspaceReady = true,
            irReady = true,
            migrationReady = false,
            diagnostics = listOf("SHIZUKU_ADAPTER_UNAVAILABLE", "SHIZUKU_AVAILABILITY_CHECK_FAILED"),
            evidence = listOf(
                "ShizukuRegistrationStatus.available is exactly installed && permissionGranted && binderAlive.",
                "ShizukuRegistrationStatusTest proves that permission without a live binder is not available.",
                "No shell command or productive capability is executed by the availability query.",
            ),
        ),
        ProviderBoolSemanticDecision(
            commandId = "termux.isInstalled",
            currentCanonicalName = "Termux.isInstalled",
            aliases = emptyList(),
            parameters = emptyList(),
            currentReturnDeclaration = "Void/unspecified",
            trueMeaning = "PackageManager resolved com.termux.",
            falseMeaning = "PackageManager definitively found no com.termux package.",
            legitimateAbsent = false,
            failureStates = listOf("adapter unavailable", "package inspection failed technically"),
            currentBoolSource = "TermuxRegistrationStatus.installed",
            firstLossPoint = "PackageManager.isPackageInstalled converts every getPackageInfo exception to false; WorkspaceScreen later flattens the surviving Boolean into message text.",
            falseCollision = listOf("package absent", "package visibility or package-manager inspection failure"),
            permissionDependent = false,
            serviceDependent = false,
            binderDependent = false,
            proposedReturnType = "Bool",
            valueSourceReady = true,
            failureSemanticsReady = false,
            typedTransportReady = true,
            workspaceReady = true,
            irReady = true,
            migrationReady = false,
            diagnostics = listOf("TERMUX_ADAPTER_UNAVAILABLE", "TERMUX_INSTALLATION_CHECK_FAILED"),
            evidence = listOf(
                "TermuxRegistration.inspect checks com.termux independently from com.termux.api.",
                "RUN_COMMAND permission and canRunCommands are separate status fields.",
            ),
        ),
    )

    fun validate() {
        require(decisions.map { it.commandId } == listOf(
            "tasker.isInstalled",
            "shizuku.isInstalled",
            "shizuku.isAvailable",
            "termux.isInstalled",
        ))
        require(decisions.all { it.parameters.isEmpty() })
        require(decisions.all { it.proposedReturnType == "Bool" && !it.legitimateAbsent })
        require(decisions.all { it.valueSourceReady && it.typedTransportReady && it.workspaceReady && it.irReady })
        require(decisions.none { it.failureSemanticsReady || it.migrationReady })
        require(decisions.all { it.falseCollision.isNotEmpty() && it.diagnostics.size == 2 })
        require(decisions.single { it.commandId == "shizuku.isAvailable" }.let {
            it.permissionDependent && it.serviceDependent && it.binderDependent
        })
        require(decisions.filterNot { it.commandId == "shizuku.isAvailable" }.all {
            !it.permissionDependent && !it.serviceDependent && !it.binderDependent
        })
    }
}
