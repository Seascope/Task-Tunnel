package com.example.tasktunnel.drift

import android.content.Context

data class KnownDriftApp(
    val packageName: String,
    val displayName: String,
)

object DriftAppCatalog {
    val apps = listOf(
        KnownDriftApp("com.instagram.android", "Instagram"),
        KnownDriftApp("com.google.android.youtube", "YouTube"),
        KnownDriftApp("com.reddit.frontpage", "Reddit"),
    )
    val knownPackages: Set<String> = apps.mapTo(linkedSetOf()) { it.packageName }

    fun labelFor(packageName: String): String? = apps.firstOrNull {
        it.packageName == packageName
    }?.displayName
}

object DriftPoolPreferences {
    private const val PREFERENCES_NAME = "drift_pool"
    private const val SELECTED_PACKAGES_KEY = "selected_packages"

    fun load(context: Context): Set<String> {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (!preferences.contains(SELECTED_PACKAGES_KEY)) return DriftAppCatalog.knownPackages
        return preferences.getStringSet(SELECTED_PACKAGES_KEY, emptySet())
            .orEmpty()
            .intersect(DriftAppCatalog.knownPackages)
    }

    fun save(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(SELECTED_PACKAGES_KEY, packages.intersect(DriftAppCatalog.knownPackages))
            .apply()
    }
}
