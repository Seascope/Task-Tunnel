# Task Tunnel battery audit — 5 October 2026

## Scope

This pass audits the full Android app for avoidable CPU wakeups, background work, accessibility-event churn, notification churn, database churn, package-manager work, polling, and UI work while backgrounded. The goal is lower battery use without slowing Purpose Gate, Drift, surface classification, or intervention behavior.

## What was already good

- No explicit wake locks or `WAKE_LOCK` permission.
- No `AlarmManager`, WorkManager, JobScheduler, location, sensors, Bluetooth, media, or background analytics.
- No foreground service loop.
- Feedback networking occurs only after an explicit user submission.
- Accessibility tree captures are already throttled and bounded to 200 nodes.
- Protection already stops overlays/captures and pauses active-use clocks while the device is locked.

## Changes implemented

1. **Lifecycle-aware Compose collection**
   - Main UI now uses `collectAsStateWithLifecycle()` for runtime, Attention, and Home state.
   - Room flows and the one-minute Attention clock stop collecting shortly after the Activity becomes backgrounded and resume automatically when visible.

2. **Master OFF is actually quiescent**
   - When Task Tunnel protection is OFF, the AccessibilityService dynamically sets its requested event mask to `0`.
   - Turning protection ON restores exactly the five event types Task Tunnel needs.
   - This removes system-to-app accessibility IPC while the user's master switch is off.

3. **Drop irrelevant accessibility traffic immediately**
   - Non-window content/scroll/click events from apps other than Instagram, YouTube, and TikTok are rejected before PowerManager, active-root, Drift/Tunnel coordinator, notification, or persistence work.
   - Window events remain global because Task Tunnel must still recognize app switches and arbitrary Drift apps.

4. **Do foreground work only on real foreground changes**
   - Same-package window churn no longer re-runs Drift/Tunnel foreground reconciliation.
   - Release builds no longer republish diagnostic foreground state for same-package window changes.
   - Supported-app window events still schedule surface captures, so detection responsiveness is preserved.

5. **Notification updates reduced drastically**
   - Countdown is still exact because Android's `RemoteViews` chronometer runs in SystemUI.
   - The decorative progress bar refresh interval moved from 2 seconds to 30 seconds.
   - Refresh callbacks stop while the device is locked/non-interactive.
   - Notification channel creation is cached per controller instance.
   - Idle state cancels a stale notification once instead of issuing repeated cancel Binder calls.
   - Notification permission/channel checks now happen only when the render key actually changes; returning to Task Tunnel invalidates the cached render state so changed system settings are picked up immediately.

6. **YouTube Subscriptions probe bounded**
   - The slow-loading creator-subscription fallback no longer polls every 5 seconds indefinitely.
   - It uses a bounded exponential retry window, then returns to normal event-driven captures.
   - Normal YouTube accessibility events can still resolve the creator relationship later, so this removes polling without disabling recovery.

7. **Room/persistence work coalesced**
   - Surface-usage writes now stay on their intended `Dispatchers.IO.limitedParallelism(1)` scope instead of overriding it with the unrestricted IO dispatcher.
   - 30-day retention cleanup now runs at most once every six hours instead of executing a DELETE after every persisted surface segment.

8. **PackageManager work reduced**
   - The Drift picker enumerates launcher apps once per Activity instance instead of every resume.
   - Default Drift selection checks only the four default packages rather than enumerating every launcher app.
   - Arbitrary Drift app labels are cached after first resolution.
   - Protection snapshot package/version work no longer rebuilds merely because the service heartbeat timestamp changed.

9. **Runtime state churn reduced**
   - Diagnostic service heartbeat publication is throttled to once per five seconds while connected.
   - Clearing an already-empty current detector state returns the existing StateFlow value instead of allocating/publishing redundant copies.
   - Home no longer performs a redundant forced database refresh on every Activity resume.

## Deliberately not changed

- Active-tunnel capture throttle remains **400 ms**. Raising it would save more CPU but would directly increase intervention latency.
- Normal capture throttle remains **1 second** because surface-usage accuracy is a core product feature.
- Accessibility `notificationTimeout` remains **100 ms**. Increasing it can reduce IPC further, but it also adds event-delivery latency; this pass favors interaction responsiveness.
- No battery-optimization exemption is requested. Task Tunnel should work within Android's normal optimized mode rather than asking users for unrestricted background execution.
- Release R8/minification settings were not changed in this battery pass because changing shrink/optimization behavior immediately before Play testing adds regression risk unrelated to the major runtime drain sources found here.

## Physical measurement before Play

Static analysis cannot give a real mAh number. On the exact signed release candidate, measure at least these scenarios on one Pixel-class and one Samsung-class device if available:

1. Protection ON, phone idle/locked for 30–60 minutes.
2. Protection ON, 30 minutes normal use with no supported apps.
3. 20–30 minutes Instagram/TikTok scrolling with an active tunnel.
4. 20–30 minute YouTube long-form video under a Subscriptions tunnel, including a creator state that remains unresolved.
5. Protection OFF for 30–60 minutes; Task Tunnel should show effectively no app-side background work.

Useful commands:

```bash
adb shell dumpsys batterystats --reset
# exercise one scenario
adb shell dumpsys batterystats com.rubin.tasktunnel
adb shell dumpsys procstats com.rubin.tasktunnel
```

Also inspect the release build in Android Studio's Energy Profiler / Background Task Inspector and confirm there are no unexpected wake locks, alarms, jobs, or sustained CPU bursts.
