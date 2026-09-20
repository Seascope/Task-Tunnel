# M3 Physical Validation Guide

M3 is implemented and covered by local unit/build checks. The scenarios below still require a physical device with the Task Tunnel accessibility service enabled. Record each result as `PASS` or `FAIL` and include the app version when a detector result differs from the expected surface.

## Setup

1. Install the current debug APK and enable Task Tunnel in Android accessibility settings.
2. Open Task Tunnel once and confirm the service connection is active.
3. Leave any existing Instagram or YouTube Task Tunnel by using **End Tunnel**, or restart the accessibility service to begin with an empty in-memory session.
4. Keep the Task Tunnel developer screen closed during the normal interaction checks. It is only for diagnostics.

## Instagram — Reply to messages

1. Open Instagram with no active Instagram Tunnel.
2. Confirm the Purpose Gate appears once and asks what you opened Instagram to do.
3. Confirm no resource IDs, confidence values, signal names, fingerprints, or node data appear in the gate.
4. Choose **Reply to messages**.
5. Enter the DM inbox. Confirm it remains uninterrupted.
6. Open a conversation. Confirm it remains uninterrupted.
7. Enter Reels. Confirm an intervention says that Reels is outside the reply Tunnel.
8. Press **Return**. Confirm the overlay dismisses and Android Back makes the narrow expected return attempt. If Back cannot return safely, confirm the overlay still dismisses without trapping the app.
9. Enter Reels again after returning to an allowed screen. Confirm the intervention appears again.
10. Press **Allow anyway**. Confirm Reels remains usable and repeated accessibility events do not immediately reopen the overlay.
11. Navigate to an allowed Messages surface, then enter Reels again. Confirm a new transition can intervene again.
12. Press **End Tunnel**. Confirm the overlay closes and Instagram remains open.
13. Confirm the Purpose Gate does not immediately reopen from repeated events in the same foreground visit.
14. Switch to another app and return to Instagram. Confirm the Purpose Gate appears again.
15. Repeat the blocked-surface checks with Explore. Confirm Explore intervenes under the Messages task and all three actions remain usable.

## Instagram — Browse intentionally

1. End any active Tunnel, leave Instagram, and open it again.
2. Choose **Browse intentionally**.
3. Move through Home, Messages, Explore, Reels, and Profile.
4. Confirm normal browsing is not interrupted.
5. Switch away briefly and return. Confirm the active Browse Tunnel is reused and the gate does not reopen.
6. Restart the accessibility service. Confirm the old in-memory Tunnel is gone and a later supported-app foreground visit safely asks for purpose again.

## YouTube — Search / watch something

1. Open YouTube with no active YouTube Tunnel.
2. Confirm the Purpose Gate appears once.
3. Choose **Search / watch something**.
4. Open Search and perform a search. Confirm the detected Search surface remains uninterrupted.
5. Open a normal video. Confirm it remains uninterrupted.
6. Enter Shorts. Confirm the intervention says that Shorts is outside this Tunnel.
7. Press **Return** and verify the same safe dismiss/Back behavior described for Instagram.
8. Enter Shorts again, press **Allow anyway**, and confirm there is no immediate overlay loop while the same Shorts surface remains active.
9. Navigate to Search or a normal video, then return to Shorts. Confirm the new transition may intervene again.
10. Press **End Tunnel**. Confirm YouTube remains open and the gate waits until a later foreground visit.

## YouTube — Browse intentionally

1. End any active Tunnel, leave YouTube, and open it again.
2. Choose **Browse intentionally**.
3. Move through Search, normal videos, Shorts, and normal YouTube shell screens.
4. Confirm normal browsing is not interrupted.

## Purpose Gate and lifecycle

1. With no applicable Tunnel, open a supported app and leave the Purpose Gate visible for several seconds.
2. Confirm repeated window/content/scroll events do not create duplicate gates or visible flashing.
3. Press **Not now**. Confirm the gate stays dismissed for that foreground visit.
4. Leave the app and return. Confirm a fresh Purpose Gate appears.
5. Start a Tunnel, switch to an unrelated app, and return. Confirm the same Tunnel remains active.
6. With an Instagram Tunnel active, open YouTube. Confirm YouTube receives its own Purpose Gate. Choose **Not now**, return to Instagram, and confirm the Instagram Tunnel still applies.
7. Disable and re-enable the accessibility service. Confirm no stale intervention or session is restored and the service fails safely.

## Failure and privacy checks

1. On screens that the detector reports as `UNKNOWN`, confirm Task Tunnel does not intervene.
2. Temporarily trigger a root-unavailable state if reproducible (for example during app/system transitions). Confirm an existing intervention does not remain as a hard blocker.
3. Exercise **Allow anyway** on every blocked surface and confirm the same surface does not immediately nag again.
4. Exercise **Return** and confirm stale events do not instantly recreate the same intervention.
5. Exercise **End Tunnel** and confirm no stale intervention remains.
6. Confirm the Purpose Gate and intervention contain only app/task/surface wording. They must not show detector confidence, resource IDs, signal names, accessibility text, fingerprints, or node trees.
7. Confirm the app stores no message text, screenshots, raw accessibility trees, or network/analytics data.

## Session-window status

M3 stores an optional intended duration in the local session model, but the Purpose Gate does not offer timed sessions yet. Expiry and extension behavior are therefore not part of this physical matrix. No app is forcibly terminated.

## Go / no-go note

After completing the matrix, answer: **Does the flow feel faster and less annoying than manually fighting the feed?** Record any repeated prompt, slow gate, surprising Back action, or unclear override as an M3 usability failure even if the detector classification itself is correct.
