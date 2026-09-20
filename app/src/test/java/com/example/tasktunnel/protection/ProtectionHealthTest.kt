package com.example.tasktunnel.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProtectionHealthTest {
    private val installed = InstalledAppStatus(
        packageName = "com.instagram.android",
        displayName = "Instagram",
        versionName = "1",
        compatibility = AppCompatibility.VERSION_NOT_RECORDED,
    )

    @Test
    fun enabledAndConnectedIsActive() {
        val result = ProtectionHealthEvaluator.evaluate(input(apps = listOf(installed)))
        assertEquals(ProtectionLevel.ACTIVE, result.level)
    }

    @Test
    fun disabledIsOff() {
        val result = ProtectionHealthEvaluator.evaluate(input(accessibilityEnabled = false))
        assertEquals(ProtectionLevel.OFF, result.level)
    }

    @Test
    fun persistentCompatibilityProblemIsLimited() {
        val result = ProtectionHealthEvaluator.evaluate(
            input(apps = listOf(installed.copy(compatibility = AppCompatibility.KNOWN_COMPATIBILITY_PROBLEM))),
        )
        assertEquals(ProtectionLevel.LIMITED, result.level)
    }

    @Test
    fun oneUnknownDoesNotLimitProtection() {
        val result = ProtectionHealthEvaluator.evaluate(input(unknownSurfaceObservations = 1))
        assertEquals(ProtectionLevel.ACTIVE, result.level)
    }

    @Test
    fun uninstalledAppIsRepresentedWithoutFalseFailure() {
        val missing = installed.copy(versionName = null, compatibility = AppCompatibility.NOT_INSTALLED)
        assertEquals(ProtectionLevel.ACTIVE, ProtectionHealthEvaluator.evaluate(input(apps = listOf(missing))).level)
    }

    @Test
    fun enabledButDisconnectedIsLimitedBackgroundConcern() {
        val result = ProtectionHealthEvaluator.evaluate(input(serviceConnected = false))
        assertEquals(ProtectionLevel.LIMITED, result.level)
        assertEquals(true, result.backgroundConcern)
    }

    @Test
    fun emptyRegistryDoesNotInventIncompatibility() {
        assertEquals(AppCompatibility.VERSION_NOT_RECORDED, CompatibilityRegistry().statusFor("app", "42"))
        assertEquals(AppCompatibility.NOT_INSTALLED, CompatibilityRegistry().statusFor("app", null))
    }

    @Test
    fun diagnosticReportIsSanitizedAndDebugGateClosesForRelease() {
        val secret = "private-message-secret"
        val report = DiagnosticReport(
            generatedAtMillis = 1,
            appVersion = "1.0",
            androidVersion = "16",
            device = "Test device",
            accessibilityEnabled = true,
            serviceConnected = true,
            lastServiceActivityMillis = 1,
            installedApps = listOf(installed),
            driftEnabled = true,
            driftApps = listOf("Instagram"),
            databaseStatus = "available",
        ).asText()
        val forbidden = listOf(secret, "resource id", "fingerprint", "node class", "confidence", "package=")
        assertFalse(forbidden.any { report.lowercase().contains(it.lowercase()) })
        assertFalse(DeveloperDiagnosticsGate.isAvailable(debugBuild = false))
    }

    private fun input(
        accessibilityEnabled: Boolean = true,
        serviceConnected: Boolean = true,
        apps: List<InstalledAppStatus> = listOf(installed),
        unknownSurfaceObservations: Int = 0,
    ) = ProtectionHealthInput(
        accessibilityEnabled,
        serviceConnected,
        lastServiceActivityMillis = 1,
        apps = apps,
        unknownSurfaceObservations = unknownSurfaceObservations,
    )
}
