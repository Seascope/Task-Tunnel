# M4 Physical Test Guide - Drift Detection

M4 automated implementation is complete. This guide is for physical-device validation. M5 is outside this test scope.

## Setup

1. Install the current debug APK and enable the Task Tunnel accessibility service.
2. Open Task Tunnel and enable Instagram, YouTube, TikTok, and Reddit under **Drift check-in apps**.
3. Confirm the apps needed for the sequence you are testing are installed and signed in enough to open normally.
4. End any active Task Tunnel before beginning Drift tests.
5. End any ordinary Purpose Gate before starting only if you want a completely clean screen. A qualifying Drift check-in now takes priority over an ordinary Purpose Gate automatically.

The beta trigger is three distinct selected apps within 60 seconds. A quiet period of 60 seconds resets an acknowledged episode.

## Basic positive case

1. Switch from Instagram to Reddit to YouTube within 60 seconds.
2. Verify the YouTube Purpose Gate does **not** replace the qualifying Drift check-in.
3. Verify exactly one soft Drift check-in appears automatically.
4. Verify the heading says **Looking for something?**.
5. Verify the message names Instagram, Reddit, and YouTube; it must not show package IDs.
6. Verify the available actions are **Set an intention** and **Keep going**.
7. Verify the underlying app is not closed or navigated automatically.

## Keep Going

1. Trigger a Drift check-in.
2. Tap **Keep going**.
3. Continue switching rapidly among the same selected apps.
4. Verify no second check-in appears during that episode.
5. Stop qualifying switching for at least 60 seconds.
6. Repeat a three-app sequence within 60 seconds.
7. Verify one new check-in can appear for the new episode.

## Set an intention

### Instagram

1. End a qualifying sequence on Instagram.
2. Verify Drift appears instead of the ordinary Instagram Purpose Gate.
3. Tap **Set an intention**.
4. Verify the Instagram Purpose Gate appears.
5. Verify no purpose is auto-selected.
6. Choose a normal Instagram purpose and confirm the existing Tunnel flow still works.

### YouTube

1. Repeat the sequence, ending on YouTube.
2. Tap **Set an intention** on the Drift check-in.
3. Verify the YouTube Purpose Gate appears with no task auto-selected.

### Unsupported Task Tunnel app

1. End a qualifying sequence on Reddit.
2. Tap **Set an intention**.
3. Verify the check-in dismisses safely.
4. Verify no generic Reddit Tunnel or policy is created.

## Non-selected app

1. Disable Reddit in **Drift check-in apps**.
2. Switch among Instagram, Reddit, and YouTube within 60 seconds.
3. Verify Reddit does not satisfy the third selected-app requirement and no Drift check-in appears.
4. Re-enable Reddit for the remaining tests.

## Repeated and slow switching

1. Generate repeated accessibility activity while staying in Instagram.
2. Verify repeated same-package observations do not trigger Drift.
3. Switch among the three selected apps slowly, leaving more than 60 seconds between the first and third selected app.
4. Verify no Drift check-in appears.

## Existing Tunnel interaction

1. Start a valid Instagram or YouTube Tunnel.
2. Briefly switch to another app and return inside the M3.1 60-second grace period.
3. Verify the original Tunnel resumes.
4. Verify no contradictory Drift check-in appears.
5. Confirm allowed surfaces, mismatch intervention, temporary Allow Anyway, expiry, and Return behavior remain unchanged.

## Overlay priority

Exercise switching while each of these existing Task Tunnel overlays is visible:

- Purpose Gate
- incompatible-surface intervention
- session-expiry re-decision

Verify the priority order is:

1. active-Tunnel intervention / expiry / check-in
2. Drift check-in
3. ordinary Purpose Gate

A qualifying Drift check-in should replace an ordinary Purpose Gate rather than stacking over it. Drift must still never replace an interaction belonging to an active Tunnel. Tap **Keep going** on Drift and verify the displaced Purpose Gate does not immediately appear afterward. Tap **Set an intention** on Drift and verify a fresh Purpose Gate appears intentionally.

Also verify both of these exact sequences trigger Drift when all apps are enabled in the Drift pool:

- Reddit -> Instagram -> YouTube
- Instagram -> YouTube -> TikTok

## Background, unrelated apps, and lifecycle

1. Trigger a Drift check-in, then switch to an app outside the selected pool.
2. Verify Task Tunnel does not leave the Drift overlay over that unrelated app.
3. Exercise ordinary home-screen/background/resume transitions and System UI panels.
4. Verify those transitions do not contribute to the selected-app count.
5. Restart or disable/re-enable the accessibility service during a partial or triggered episode.
6. Verify transient Drift state is cleared and no stale check-in appears.
7. Start a fresh qualifying sequence and verify Drift can trigger normally afterward.

## Privacy and copy

Confirm the normal Drift UI never shows:

- package identifiers
- millisecond timestamps
- rolling-window counters or event queues
- accessibility internals or detector confidence
- content from any app

The tone should remain observational and neutral. **Keep going** must be treated as a valid choice and must not create a Tunnel or record a visible failure.

## Physical acceptance record

Record device model, Android version, app versions, pass/fail for each section, unexpected overlay timing, and whether the flow feels gentle rather than accusatory.
