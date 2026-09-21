# Task Tunnel Privacy Summary

## Plain-language summary

Task Tunnel runs locally on the Android device. The MVP has no account, cloud backend, analytics SDK, or telemetry. It uses Accessibility access to understand a narrow set of visible Instagram and YouTube surfaces and protect a purpose chosen by the user.

## Data used while protection runs

Android can expose the active app and visible accessibility interface to Task Tunnel. Production classification uses a bounded snapshot of interface structure, identifiers, and state flags. The result is reduced to a supported app/surface enum or `UNKNOWN`. Uncertainty fails open.

Task Tunnel does not take screenshots. It does not need private message contents, usernames, or a history of accessibility text.

## Data stored on-device

The Room database stores semantic Attention events only: time, event family/subtype, known app, known surface, selected task, Task Tunnel ID, Drift episode ID, explicit decision, and known involved apps.

It also stores bounded local surface-usage segments for supported Instagram and YouTube surfaces: app, semantic surface or unclassified state, start/end timestamps, and optional declared Tunnel task. This metadata is used for local measurement only; it does not contain app content.

Shared preferences store the selected Instagram/YouTube/Reddit Drift pool and minimal onboarding progress. Runtime detector observations, active Task Tunnel state, service heartbeat, sanitized debug snapshots, and developer fingerprints remain in memory.

Attention database files, including SQLite journal files, and the Task Tunnel onboarding/Drift preferences are excluded from Android backup and device transfer by the project's backup/data-extraction rules.

## User controls

- Accessibility access can be disabled in Android settings.
- Drift Detection can be turned off and its fixed local app pool can be changed.
- **Clear Attention history** removes the local Attention event database contents after confirmation.
- **Copy diagnostics** copies only app/device versions, permission and service state, Drift configuration, database availability, and report time.

The diagnostic report excludes app content, message text, usernames, accessibility text, screenshots, raw trees, raw fingerprints, resource identifiers, node classes, detector scores, and Attention history entries.

## External services

The MVP has no Internet permission and no generic installed-app discovery. Package visibility is limited to Instagram, YouTube, and official Reddit. `QUERY_ALL_PACKAGES` is not requested.

Before Play submission, the developer must publish a final privacy policy and reconcile this summary with the live Data safety form and current AccessibilityService policy.
