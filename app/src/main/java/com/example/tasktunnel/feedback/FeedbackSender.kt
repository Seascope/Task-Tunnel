package com.example.tasktunnel.feedback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed interface FeedbackSubmissionResult {
    data object Sent : FeedbackSubmissionResult
    data object NotConfigured : FeedbackSubmissionResult
    data object NetworkError : FeedbackSubmissionResult
    data object Rejected : FeedbackSubmissionResult
}

object FeedbackSender {
    private const val CONNECT_TIMEOUT_MILLIS = 10_000
    private const val READ_TIMEOUT_MILLIS = 10_000
    private val formIdPattern = Regex("^[A-Za-z0-9_-]+$")

    suspend fun submit(
        formId: String,
        draft: FeedbackDraft,
        statusReport: FeedbackStatusReport,
    ): FeedbackSubmissionResult = withContext(Dispatchers.IO) {
        val normalizedFormId = formId.trim()
        if (normalizedFormId.isEmpty() || !formIdPattern.matches(normalizedFormId)) {
            return@withContext FeedbackSubmissionResult.NotConfigured
        }

        val connection = runCatching {
            (URL("https://formspree.io/f/$normalizedFormId").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            }
        }.getOrElse {
            return@withContext FeedbackSubmissionResult.NetworkError
        }

        try {
            val payload = draft.fields(statusReport)
                .entries
                .joinToString("&") { (key, value) ->
                    "${key.urlEncoded()}=${value.urlEncoded()}"
                }
                .toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(payload) }
            val responseCode = connection.responseCode
            when (responseCode) {
                in 200..299 -> FeedbackSubmissionResult.Sent
                in 400..499 -> FeedbackSubmissionResult.Rejected
                else -> FeedbackSubmissionResult.NetworkError
            }
        } catch (_: Exception) {
            FeedbackSubmissionResult.NetworkError
        } finally {
            connection.disconnect()
        }
    }

    internal fun FeedbackDraft.fields(statusReport: FeedbackStatusReport): Map<String, String> = buildMap {
        put("subject", subject())
        put("category", category.label)
        put("message", note.trim())
        put("source", "Task Tunnel Android")

        if (includeStatusReport) {
            put("app_version", statusReport.appVersion)
            put("android_version", statusReport.androidVersion)
            put("accessibility", statusReport.accessibilityEnabled.onOff())
            put("task_tunnel", statusReport.protectionEnabled.onOff())
            put("drift", statusReport.driftEnabled.onOff())
            put("notification_controls", statusReport.notificationControlsEnabled.onOff())
        }
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
    private fun Boolean.onOff(): String = if (this) "on" else "off"
}
