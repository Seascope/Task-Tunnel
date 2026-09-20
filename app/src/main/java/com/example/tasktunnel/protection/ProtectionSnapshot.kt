package com.example.tasktunnel.protection

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.tasktunnel.BuildConfig
import com.example.tasktunnel.accessibility.AccessibilityState
import com.example.tasktunnel.drift.DriftAppCatalog
import java.text.DateFormat
import java.util.Date

data class ProtectionSnapshot(
    val health: ProtectionHealth,
    val apps: List<InstalledAppStatus>,
    val diagnosticReport: DiagnosticReport,
)

data class DiagnosticReport(
    val generatedAtMillis: Long,
    val appVersion: String,
    val androidVersion: String,
    val device: String,
    val accessibilityEnabled: Boolean,
    val serviceConnected: Boolean,
    val lastServiceActivityMillis: Long?,
    val installedApps: List<InstalledAppStatus>,
    val driftEnabled: Boolean,
    val driftApps: List<String>,
    val databaseStatus: String,
) {
    fun asText(): String = buildString {
        appendLine("Task Tunnel diagnostics")
        appendLine("Generated: ${DateFormat.getDateTimeInstance().format(Date(generatedAtMillis))}")
        appendLine("Task Tunnel version: $appVersion")
        appendLine("Android: $androidVersion")
        appendLine("Device: $device")
        appendLine("Accessibility access: ${onOff(accessibilityEnabled)}")
        appendLine("Service: ${if (serviceConnected) "running" else "not running"}")
        appendLine(
            "Last service activity: " +
                (lastServiceActivityMillis?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "not available"),
        )
        installedApps.forEach { app ->
            val version = app.versionName ?: "not installed"
            appendLine("${app.displayName}: $version (${compatibilityLabel(app.compatibility)})")
        }
        appendLine("Drift Detection: ${onOff(driftEnabled)}")
        appendLine("Drift apps: ${driftApps.joinToString().ifBlank { "none" }}")
        appendLine("Attention database: $databaseStatus")
        append("This report contains technical status only. It contains no app content or Attention history.")
    }

    private fun onOff(value: Boolean) = if (value) "on" else "off"

    private fun compatibilityLabel(value: AppCompatibility): String = when (value) {
        AppCompatibility.NOT_INSTALLED -> "not installed"
        AppCompatibility.VERSION_NOT_RECORDED -> "version not yet verified"
        AppCompatibility.VERIFIED -> "verified version"
        AppCompatibility.KNOWN_COMPATIBILITY_PROBLEM -> "known compatibility concern"
    }
}

object ProtectionSnapshotFactory {
    private val supportedApps = listOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.reddit.frontpage" to "Reddit",
    )

    fun create(
        context: Context,
        accessibilityEnabled: Boolean,
        runtime: AccessibilityState,
        selectedDriftPackages: Set<String>,
        databaseAvailable: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): ProtectionSnapshot {
        val apps = supportedApps.map { (packageName, label) ->
            val version = installedVersion(context.packageManager, packageName)
            InstalledAppStatus(
                packageName = packageName,
                displayName = label,
                versionName = version,
                compatibility = CompatibilityRegistry.current.statusFor(packageName, version),
            )
        }
        val health = ProtectionHealthEvaluator.evaluate(
            ProtectionHealthInput(
                accessibilityEnabled = accessibilityEnabled,
                serviceConnected = runtime.connected,
                lastServiceActivityMillis = runtime.lastHeartbeatMillis,
                apps = apps.take(2),
            ),
        )
        val report = DiagnosticReport(
            generatedAtMillis = nowMillis,
            appVersion = BuildConfig.VERSION_NAME,
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = listOf(Build.MANUFACTURER, Build.MODEL).filter(String::isNotBlank).joinToString(" "),
            accessibilityEnabled = accessibilityEnabled,
            serviceConnected = runtime.connected,
            lastServiceActivityMillis = runtime.lastHeartbeatMillis,
            installedApps = apps,
            driftEnabled = selectedDriftPackages.isNotEmpty(),
            driftApps = DriftAppCatalog.apps.filter { it.packageName in selectedDriftPackages }.map { it.displayName },
            databaseStatus = if (databaseAvailable) "available" else "unavailable",
        )
        return ProtectionSnapshot(health, apps, report)
    }

    @Suppress("DEPRECATION")
    private fun installedVersion(packageManager: PackageManager, packageName: String): String? = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }
        info.versionName ?: info.longVersionCode.toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
