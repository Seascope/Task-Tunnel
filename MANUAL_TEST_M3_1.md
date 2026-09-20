# M3.1 Physical Validation Guide

M3 has passed physical-device validation. M3.1 automated checks cover the timing state machine, while the Android accessibility overlay, active-root timing, and real app lifecycle behavior below still require physical validation.

## Setup

1. Install the current debug APK and enable Task Tunnel in Android accessibility settings.
2. Restart the accessibility service once so the in-memory Tunnel state is empty.
3. Use a clock or timer for the five-minute allowance, 60-second return grace, and selected session windows. The production overlay intentionally shows no live countdown or internal timestamp.
4. Keep the developer diagnostics screen closed except during the explicit fail-open checks.

## Instagram - Allow Anyway

1. Open Instagram and choose **Reply to messages** with **No limit**.
2. Enter Reels and confirm the normal incompatibility intervention appears.
3. Press **Allow anyway** and remain in Reels. Confirm there is no immediate re-prompt despite repeated scrolling and accessibility events.
4. Return to Messages, then re-enter Reels before five minutes have elapsed. Confirm Reels remains allowed for the same temporary allowance.
5. Enter Explore during the Reels allowance. Confirm Explore does not inherit the Reels allowance and can still intervene.
6. Return to an allowed Messages surface and wait until the five-minute allowance has expired.
7. Enter or re-enter Reels and confirm the intervention can appear again.
8. Press **End Tunnel** from an intervention and confirm the allowance is gone with the Tunnel and Instagram remains open.

## YouTube - Allow Anyway

1. Open YouTube and choose **Search / watch something** with **No limit**.
2. Enter Shorts, press **Allow anyway**, and keep using Shorts.
3. Confirm repeated Shorts events do not immediately re-prompt.
4. Leave Shorts for Search or a normal video, then re-enter Shorts before five minutes. Confirm the temporary Shorts allowance still applies.
5. Wait until the allowance expires, then enter or re-enter Shorts. Confirm an intervention can appear again.

## Quick-return grace

1. Start an Instagram or YouTube Tunnel and note the chosen task.
2. Switch to an unrelated app for less than 60 seconds.
3. Confirm no Task Tunnel overlay appears over the unrelated app.
4. Return to the protected app and confirm the same Tunnel resumes without a Purpose Gate.
5. Switch away again and remain away for at least 60 seconds.
6. Confirm no overlay appears over the unrelated app when grace expires.
7. Return to the protected app and confirm a fresh Purpose Gate appears.
8. Start another Tunnel, switch away briefly, then explicitly use **End Tunnel** when available. Confirm explicit ending is immediate and does not preserve a grace session.
9. Restart the accessibility service during an active Tunnel. Confirm the old session is cleared and the next supported-app foreground visit gets a fresh Purpose Gate.

## Optional session window

1. Open a supported app with no active Tunnel.
2. Confirm the Purpose Gate still gives equal presentation to its two task choices and offers **No limit**, **5 min**, **10 min**, and **20 min** as secondary duration choices.
3. Start an intentional Browse Tunnel with **No limit**. Confirm ordinary browsing remains quiet and no hidden expiry appears.
4. End that Tunnel, leave and return to the app, then start a five-minute Tunnel.
5. Stay on an allowed surface until the window expires. Confirm there is no persistent timer, floating widget, border, or motivational overlay during the active window.
6. At expiry, confirm the neutral re-decision offers **Finish**, **Continue**, and **Choose another purpose**, without warning-red, guilt, or punishment language.
7. Press **Finish**. Confirm the Tunnel ends while the underlying app remains open and no Purpose Gate immediately replaces the expiry prompt.
8. Start another five-minute Tunnel, wait for expiry, and press **Continue**. Confirm the overlay closes, the same purpose resumes, and another expiry does not happen immediately.
9. Start another timed Tunnel, wait for expiry, and press **Choose another purpose**. Confirm the Purpose Gate appears without closing the host app, then select either task normally.

## Background expiry

1. Start a five-minute timed Tunnel.
2. Before it expires, switch to an unrelated app and remain there through the earlier of session expiry or the 60-second return-grace deadline.
3. Confirm Task Tunnel does not place an expiry or Purpose Gate overlay over the unrelated app.
4. Reopen the supported app and confirm a fresh Purpose Gate appears rather than the old Tunnel or an expiry re-decision.

## Regression, fail-open, and privacy

1. Repeat the core Instagram Messages flow: Messages remains allowed; Reels and Explore intervene when no temporary allowance applies.
2. Repeat the core YouTube Search/Watch flow: Search and normal video remain allowed; Shorts intervenes when no temporary allowance applies.
3. Confirm **Return** still dismisses the intervention before making one narrow Android Back attempt and does not immediately loop on stale events.
4. Visit a detector state reported as `UNKNOWN`. Confirm Task Tunnel allows it and does not show an intervention.
5. Switch among unrelated apps while a Tunnel is in grace. Confirm none receives a Task Tunnel overlay.
6. Confirm ordinary Purpose Gates, interventions, and expiry prompts contain no timestamps, detector confidence, resource IDs, fingerprints, node trees, signal names, or accessibility internals.
7. Confirm no accessibility text, screenshots, raw trees, analytics, or network data were added or persisted.

## Acceptance note

Record each scenario as `PASS` or `FAIL`, including device, Android version, Instagram version, and YouTube version for any failure. M3.1 is physically complete only after the matrix passes. M4 is outside this guide and has not started.
