# Task Tunnel Accessibility Disclosure

## Feature purpose

Task Tunnel is a digital-wellbeing feature for Android. A user chooses why they opened Instagram, YouTube, or TikTok. Task Tunnel then recognizes supported surfaces and offers a user-controlled intervention when the user moves outside that declared purpose.

Task Tunnel is not presented as a disability-support accessibility tool. It does not claim to make bypass impossible, and it does not close a supported app automatically.

## Why AccessibilityService is necessary

Instagram, YouTube, and TikTok do not expose a public API that tells Task Tunnel which supported in-app surface is currently visible. Android AccessibilityService supplies foreground-window events and a representation of the visible interface. Task Tunnel uses those signals to classify a small, fixed set of semantic surfaces in those three supported apps.

The service is also used to show the Purpose Gate, Drift check-in, and tunnel interventions. The user chooses every purpose and every intervention response. Directed navigation and Back actions occur only after an explicit user choice that requires them.

## What the service can inspect

For Instagram, YouTube, and TikTok, the service can inspect enough of the visible interface to recognize supported surfaces. Android may make interface structure, view identifiers, element classes, state flags, visible text, and content descriptions available to an enabled accessibility service.

Task Tunnel's production detector path derives bounded semantic state and does not retain arbitrary accessibility text. It does not need private message contents, video titles, channel names, usernames, search queries, or other arbitrary visible content as history.

Apps selected only for Drift Detection are different: they contribute foreground package identity only. Task Tunnel does not parse their accessibility trees. The Drift app picker uses normal launcher/package visibility and does not request `QUERY_ALL_PACKAGES`.

## What is retained

Task Tunnel retains only structured local data needed for the product and Attention:

- timestamps;
- supported app and semantic surface state;
- the purpose the user selected;
- Task Tunnel and Drift episode identifiers;
- intervention type and the user's explicit decision;
- exact package names involved in a Drift episode;
- bounded supported-app surface-duration history.

Local preferences also retain configuration such as protected-app settings, selected Drift apps, check-in settings, notification state, adaptive-friction counters, the master pause state, and onboarding completion.

## What is not retained

Task Tunnel does not retain:

- screenshots;
- private message contents;
- arbitrary accessibility text as history;
- video titles, channel names, usernames, or search queries as history;
- raw accessibility trees;
- raw fingerprints;
- resource identifiers or node classes in Attention history.

Developer-only fingerprints and the sanitized tree inspector are compiled behind the debug-build gate and are unavailable from normal release UI. Nothing uploads raw trees automatically.

## Processing, backup, and control

MVP processing occurs locally on the device. There is no Task Tunnel account, cloud backend, cloud sync, telemetry, or remote detector-rule download.

Task Tunnel's local database and shared preferences are excluded from Android cloud backup and device-to-device transfer. Clearing local history removes both Attention events and tracked supported-app activity history.

Uncertain classification returns `UNKNOWN` and fails open. One unknown surface is normal and does not create a protection warning. Task Tunnel never blocks use because a detector-health warning exists.

Supported-app Drift check-ins can offer **Set an intention** and **Keep going**. Drift-only apps show an acknowledgement action instead of pretending they support Task Tunnel intentions. Tunnel interventions remain explicit and reversible.

## Enabling and disabling access

Task Tunnel first explains its value, operation, data handling, and AccessibilityService use in the app. It opens Android Accessibility settings only after the user taps **Continue to Accessibility settings**.

The in-app master control can pause all Task Tunnel behavior without erasing the user's configuration or disabling Android Accessibility permission. The user can also disable Accessibility access entirely in Android Settings > Accessibility > Task Tunnel protection. The rest of the app remains available for privacy information, diagnostics, and existing local Attention history.

This document supports review preparation. The final disclosure and Play declaration must be checked against the live Google Play policy and Console form at submission time.
