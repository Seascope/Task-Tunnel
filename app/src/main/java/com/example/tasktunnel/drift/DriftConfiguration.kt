package com.example.tasktunnel.drift

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

data class KnownDriftApp(
    val packageName: String,
    val displayName: String,
)

object DriftAppCatalog {
    /** Default starter set only. Drift itself is not limited to these packages. */
    val apps = listOf(
        KnownDriftApp("com.instagram.android", "Instagram"),
        KnownDriftApp("com.google.android.youtube", "YouTube"),
        KnownDriftApp("com.zhiliaoapp.musically", "TikTok"),
        KnownDriftApp("com.reddit.frontpage", "Reddit"),
    )
    val knownPackages: Set<String> = apps.mapTo(linkedSetOf()) { it.packageName }

    /**
     * Apps the user can reasonably launch and bounce between.
     *
     * This intentionally avoids QUERY_ALL_PACKAGES. The manifest declares launcher-intent
     * visibility, which is enough for the Drift picker without asking for broad package access.
     */
    fun installedLaunchableApps(context: Context): List<KnownDriftApp> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        return resolved.asSequence()
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName?.takeIf { it != context.packageName }
                    ?: return@mapNotNull null
                val displayName = runCatching { info.loadLabel(packageManager).toString().trim() }
                    .getOrNull()
                    .takeUnless { it.isNullOrBlank() }
                    ?: apps.firstOrNull { it.packageName == packageName }?.displayName
                    ?: packageName.substringAfterLast('.')
                KnownDriftApp(packageName, displayName)
            }
            .distinctBy(KnownDriftApp::packageName)
            .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
            .toList()
    }

    fun labelFor(context: Context, packageName: String): String =
        apps.firstOrNull { it.packageName == packageName }?.displayName
            ?: loadApplicationLabel(context, packageName)
            ?: packageName.substringAfterLast('.').ifBlank { packageName }

    fun defaultSelection(context: Context): Set<String> {
        val installedDefaults = installedLaunchableApps(context)
            .asSequence()
            .map(KnownDriftApp::packageName)
            .filter { it in knownPackages }
            .toCollection(linkedSetOf())
        return installedDefaults.ifEmpty { knownPackages }
    }

    @Suppress("DEPRECATION")
    private fun loadApplicationLabel(context: Context, packageName: String): String? = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString().trim().takeIf(String::isNotBlank)
    }.getOrNull()
}

object DriftPoolPreferences {
    private const val PREFERENCES_NAME = "drift_pool"
    private const val SELECTED_PACKAGES_KEY = "selected_packages"
    private const val LAST_NON_EMPTY_SELECTION_KEY = "last_non_empty_selection"

    fun load(context: Context): Set<String> {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (!preferences.contains(SELECTED_PACKAGES_KEY)) return DriftAppCatalog.defaultSelection(context)
        return sanitize(preferences.getStringSet(SELECTED_PACKAGES_KEY, emptySet()).orEmpty())
    }

    fun save(context: Context, packages: Set<String>) {
        val sanitized = sanitize(packages)
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val previous = sanitize(preferences.getStringSet(SELECTED_PACKAGES_KEY, emptySet()).orEmpty())
        preferences.edit()
            .putStringSet(SELECTED_PACKAGES_KEY, sanitized)
            .apply {
                val selectionToRemember = sanitized.ifEmpty { previous }
                if (selectionToRemember.isNotEmpty()) {
                    putStringSet(LAST_NON_EMPTY_SELECTION_KEY, selectionToRemember)
                }
            }
            .apply()
    }

    fun restoreSelection(context: Context): Set<String> {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val saved = sanitize(preferences.getStringSet(LAST_NON_EMPTY_SELECTION_KEY, emptySet()).orEmpty())
        return saved.ifEmpty { DriftAppCatalog.defaultSelection(context) }
    }

    private fun sanitize(packages: Set<String>): Set<String> = packages
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toCollection(linkedSetOf())
}
