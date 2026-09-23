package com.example.tasktunnel.feedback

enum class FeedbackCategory(val label: String) {
    BROKE("Something broke"),
    CONFUSING("Something was confusing"),
    WRONG_INTERRUPTION("Task Tunnel interrupted me incorrectly"),
    IDEA("I have an idea"),
}

data class FeedbackStatusReport(
    val appVersion: String,
    val androidVersion: String,
    val accessibilityEnabled: Boolean,
    val protectionEnabled: Boolean,
    val driftEnabled: Boolean,
    val notificationControlsEnabled: Boolean,
) {
    fun asText(): String = buildString {
        appendLine("Task Tunnel status")
        appendLine("App version: $appVersion")
        appendLine("Android: $androidVersion")
        appendLine("Accessibility: ${onOff(accessibilityEnabled)}")
        appendLine("Task Tunnel: ${onOff(protectionEnabled)}")
        appendLine("Drift: ${onOff(driftEnabled)}")
        append("Notification controls: ${onOff(notificationControlsEnabled)}")
    }

    private fun onOff(value: Boolean): String = if (value) "on" else "off"
}

data class FeedbackDraft(
    val category: FeedbackCategory,
    val note: String,
    val includeStatusReport: Boolean,
) {
    fun subject(): String = "Task Tunnel feedback — ${category.label}"

    fun body(statusReport: FeedbackStatusReport): String = buildString {
        appendLine(category.label)
        appendLine()
        val trimmedNote = note.trim()
        if (trimmedNote.isNotEmpty()) {
            appendLine(trimmedNote)
        }
        if (includeStatusReport) {
            appendLine()
            appendLine("---")
            appendLine(statusReport.asText())
            appendLine()
            append("This status contains setup information only. No activity history or app content is included.")
        }
    }
}
