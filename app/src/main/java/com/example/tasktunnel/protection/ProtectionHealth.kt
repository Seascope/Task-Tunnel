package com.example.tasktunnel.protection

enum class ProtectionLevel { ACTIVE, LIMITED, OFF }

enum class AppCompatibility {
    NOT_INSTALLED,
    VERSION_NOT_RECORDED,
    VERIFIED,
    KNOWN_COMPATIBILITY_PROBLEM,
}

data class InstalledAppStatus(
    val packageName: String,
    val displayName: String,
    val versionName: String?,
    val compatibility: AppCompatibility,
)

data class ProtectionHealthInput(
    val accessibilityEnabled: Boolean,
    val serviceConnected: Boolean,
    val lastServiceActivityMillis: Long?,
    val apps: List<InstalledAppStatus>,
    val persistentDetectorConcernApps: Set<String> = emptySet(),
    // UNKNOWN is normal fail-open behavior and is deliberately not a health signal.
    val unknownSurfaceObservations: Int = 0,
)

data class ProtectionHealth(
    val level: ProtectionLevel,
    val title: String,
    val summary: String,
    val backgroundConcern: Boolean = false,
)

object ProtectionHealthEvaluator {
    fun evaluate(input: ProtectionHealthInput): ProtectionHealth {
        if (!input.accessibilityEnabled) {
            return ProtectionHealth(
                ProtectionLevel.OFF,
                "Protection off",
                "Accessibility access is off. Task Tunnel cannot recognize supported app surfaces.",
            )
        }

        if (!input.serviceConnected) {
            return ProtectionHealth(
                ProtectionLevel.LIMITED,
                "Limited protection",
                "Task Tunnel may be restricted in the background. Some interventions may not appear.",
                backgroundConcern = true,
            )
        }

        val compatibilityConcern = input.apps.firstOrNull {
            it.compatibility == AppCompatibility.KNOWN_COMPATIBILITY_PROBLEM
        }
        val detectorConcernPackage = input.persistentDetectorConcernApps.firstOrNull()
        if (compatibilityConcern != null || detectorConcernPackage != null) {
            val appName = compatibilityConcern?.displayName
                ?: input.apps.firstOrNull { it.packageName == detectorConcernPackage }?.displayName
                ?: "A supported app"
            return ProtectionHealth(
                ProtectionLevel.LIMITED,
                "Limited protection",
                "$appName may have changed. Some interventions may not appear.",
            )
        }

        return ProtectionHealth(
            ProtectionLevel.ACTIVE,
            "Protection active",
            "Task Tunnel is ready for Instagram and YouTube.",
        )
    }
}

class CompatibilityRegistry(
    private val verifiedVersions: Map<String, Set<String>> = emptyMap(),
    private val knownIncompatibleVersions: Map<String, Set<String>> = emptyMap(),
) {
    fun statusFor(packageName: String, installedVersion: String?): AppCompatibility = when {
        installedVersion == null -> AppCompatibility.NOT_INSTALLED
        installedVersion in knownIncompatibleVersions[packageName].orEmpty() ->
            AppCompatibility.KNOWN_COMPATIBILITY_PROBLEM
        installedVersion in verifiedVersions[packageName].orEmpty() -> AppCompatibility.VERIFIED
        else -> AppCompatibility.VERSION_NOT_RECORDED
    }

    companion object {
        // Physical validation records did not capture exact app versions. Keep the
        // mechanism explicit and empty until a tested version is documented.
        val current = CompatibilityRegistry()
    }
}

object DeveloperDiagnosticsGate {
    fun isAvailable(debugBuild: Boolean): Boolean = debugBuild
}
