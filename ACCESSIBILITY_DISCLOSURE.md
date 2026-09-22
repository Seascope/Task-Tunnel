# Task Tunnel Accessibility Disclosure

## Feature purpose

Task Tunnel is a digital-wellbeing feature for Android. A user chooses why they opened Instagram or YouTube. Task Tunnel then recognizes supported surfaces and offers a user-controlled intervention when the user moves outside that declared purpose.

Task Tunnel is not presented as a disability-support accessibility tool. It does not claim to make bypass impossible, and it does not close a supported app automatically.

## Why AccessibilityService is necessary

Instagram and YouTube do not expose a public API that tells Task Tunnel whether the visible surface is Messages, Reels, Explore, Search, a normal video, or Shorts. Android AccessibilityService supplies foreground-window events and a representation of the visible interface. Task Tunnel uses those signals to classify a small, fixed set of surfaces in the official apps.

The service is also used to show the Purpose Gate and intervention over the supported app. The user chooses every purpose and every intervention response. Return performs one standard Android Back action only after the user taps Return. End Tunnel and Allow Anyway are also explicit user actions.

## What the service can inspect

For Instagram and YouTube, the service can inspect enough of the visible interface to recognize supported surfaces. Android may make interface structure, view identifiers, element classes, state flags, visible text, and content descriptions available to an enabled accessibility service.

Task Tunnel's production detector path uses bounded structure, identifiers, and state flags. For YouTube top-level navigation only, it may derive a fixed semantic role such as Home, Shorts, Subscriptions, or You from an exact app-chrome accessibility label; the raw label is not retained. It does not need private message contents, video titles, usernames, or arbitrary accessibility text to classify supported surfaces.

## What is retained

Task Tunnel retains only structured local events needed for the product and Attention:

- timestamps;
- known app and known surface enums;
- the purpose the user selected;
- Task Tunnel and Drift episode identifiers;
- intervention type and the user's explicit decision;
- known apps involved in a Drift sequence.

The selected Drift pool and minimal onboarding completion state are also stored locally.

Task Tunnel may also retain bounded local history of detected supported-app surface durations. This contains only the app, semantic surface or an unclassified state, timestamps, and any active declared Tunnel task; it does not retain visible text or app content.

## What is not retained

Task Tunnel does not retain:

- screenshots;
- private message contents;
- accessibility text as history;
- usernames as Attention history;
- raw accessibility trees;
- raw fingerprints;
- resource identifiers or node classes in Attention history.

Developer-only fingerprints and the sanitized tree inspector are compiled behind the debug-build gate and are unavailable from normal release UI. Nothing uploads raw trees automatically.

## Processing and control

MVP processing occurs locally on the device. There is no Task Tunnel account, cloud backend, cloud sync, telemetry, or remote detector-rule download.

Uncertain classification returns `UNKNOWN` and fails open. One unknown surface is normal and does not create a protection warning. Task Tunnel never blocks use because a detector-health warning exists.

Every surface intervention offers Return, End Tunnel, and Allow Anyway. Drift offers Set an intention and Keep going. Intentional browsing is a supported purpose.

## Enabling and disabling access

Task Tunnel first explains its value, operation, data handling, and AccessibilityService use in the app. It opens Android Accessibility settings only after the user taps **Continue to Accessibility settings**.

The user can disable access at any time in Android Settings > Accessibility > Task Tunnel protection. Protection then reports **Protection off** and provides the same disclosure-led repair path. The rest of the app remains available for privacy information, diagnostics, and existing local Attention history.

This document supports review preparation. The final disclosure and Play declaration must be checked against the live Google Play policy and Console form at submission time.
