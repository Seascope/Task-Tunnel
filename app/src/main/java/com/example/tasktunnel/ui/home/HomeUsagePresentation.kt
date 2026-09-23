package com.example.tasktunnel.ui.home

internal data class HomeSurfaceDisplayUsage(
    val kind: HomeSurfaceKind,
    val label: String,
    val durationMillis: Long,
    val isOther: Boolean = false,
)

internal fun HomeAppUsage.primarySurface(): HomeSurfaceUsage? = surfaceRows
    .asSequence()
    .filter { it.durationMillis > 0L }
    .filterNot { it.kind == HomeSurfaceKind.OTHER || it.kind == HomeSurfaceKind.UNCLASSIFIED }
    .maxByOrNull(HomeSurfaceUsage::durationMillis)

internal fun HomeAppUsage.compactSurfaceRows(maxNamedRows: Int = 3): List<HomeSurfaceDisplayUsage> {
    require(maxNamedRows >= 1) { "maxNamedRows must be at least 1" }

    val sorted = surfaceRows
        .filter { it.durationMillis > 0L }
        .sortedByDescending(HomeSurfaceUsage::durationMillis)
    val named = sorted.filterNot { it.kind == HomeSurfaceKind.OTHER || it.kind == HomeSurfaceKind.UNCLASSIFIED }
    val kept = named.take(maxNamedRows)
    val keptKinds = kept.map(HomeSurfaceUsage::kind).toSet()
    val remainderMillis = sorted
        .filter { it.kind !in keptKinds }
        .sumOf(HomeSurfaceUsage::durationMillis)

    return buildList {
        kept.forEach { surface ->
            add(
                HomeSurfaceDisplayUsage(
                    kind = surface.kind,
                    label = surface.kind.displayLabel,
                    durationMillis = surface.durationMillis,
                ),
            )
        }
        if (remainderMillis > 0L) {
            add(
                HomeSurfaceDisplayUsage(
                    kind = HomeSurfaceKind.OTHER,
                    label = "Other",
                    durationMillis = remainderMillis,
                    isOther = true,
                ),
            )
        }
    }
}
