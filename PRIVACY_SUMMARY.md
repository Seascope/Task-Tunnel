# Task Tunnel Privacy Summary

## Plain-language summary

Task Tunnel runs locally on the Android device. The MVP has no account, sync backend, analytics SDK, telemetry, or user-data upload. Core protection and activity processing stay on-device. It uses Accessibility access to understand a narrow set of visible Instagram, YouTube, and TikTok surfaces and protect a purpose chosen by the user.

## Data used while protection runs

Android can expose the active app and visible accessibility interface to Task Tunnel. Production classification uses a bounded snapshot of interface structure, identifiers, and state flags. The result is reduced to a supported app/surface enum or `UNKNOWN`. Uncertainty fails open.

Task Tunnel does not take screenshots. It does not need private message contents, usernames, or a history of accessibility text.

## Data stored on-device

The Room database stores semantic Attention events only: time, event family/subtype, supported app, semantic surface, selected task, Task Tunnel ID, Drift episode ID, explicit decision, and exact package names involved in Drift episodes.

It also stores bounded local surface-usage segments for supported Instagram, YouTube, and TikTok surfaces: app, semantic surface or unclassified state, start/end timestamps, and optional declared Tunnel task. This metadata is used for local measurement only; it does not contain app content.

Shared preferences store local configuration such as the selected Drift apps, onboarding progress, check-in preference, master protection state, adaptive-friction counters, and notification state. Runtime detector observations, active Task Tunnel state, service heartbeat, sanitized debug snapshots, and developer fingerprints remain in memory.

All Task Tunnel database files and shared preferences are excluded from Android cloud backup and device transfer by the project's backup/data-extraction rules.

## User controls

- Accessibility access can be disabled in Android settings.
- Drift Detection can be turned off and any installed launchable apps can be selected without requesting `QUERY_ALL_PACKAGES`.
- **Clear local history** removes both semantic Attention events and tracked in-app surface-usage history after confirmation.
- **Copy diagnostics** copies only app/device versions, permission and service state, Drift configuration, database availability, and report time.

The diagnostic report excludes app content, message text, usernames, accessibility text, screenshots, raw trees, raw fingerprints, resource identifiers, node classes, detector scores, and Attention history entries.

## External services

Task Tunnel does not declare the Internet permission. There is no background analytics, telemetry, remote detector-rule download, activity-history upload, accessibility-text upload, or app-content upload.

The Drift picker uses normal launcher-intent visibility to list launchable apps and excludes Task Tunnel itself; `QUERY_ALL_PACKAGES` is not requested. Detailed accessibility-tree classification remains limited to Instagram, YouTube, and TikTok.

Before Play submission, the developer must publish a final privacy policy and reconcile this summary with the live Data safety form and current AccessibilityService policy.
