# Manual Test — Active Tunnel Notification Controls

Use a physical Android device with Task Tunnel Accessibility protection enabled. On Android 13+, grant notification permission when prompted.

## 1. Notification lifecycle

1. Open Instagram, YouTube, or TikTok with no active tunnel.
   - No active-tunnel notification should be present.
2. Start a tunnel with **No limit**.
   - A silent **<App> · Tunnel active** notification should appear.
   - It should show the selected purpose and **No time limit**.
3. End the tunnel from the overlay or notification.
   - The notification should disappear immediately.

## 2. Timed tunnel

1. Start a 10-minute tunnel.
   - The notification should show a live countdown chronometer.
2. Leave the notification shade open for a few seconds.
   - The countdown should continue without notification flicker/reposting.
3. End the tunnel normally.
   - The notification disappears.

## 3. Reverse an accidental detour allowance by recommitting

1. Start an Instagram **Reply to messages** tunnel.
2. Enter Reels and choose **Allow Reels for now**.
   - Notification changes to **Detour active** and still exposes **Change purpose** and **End**.
3. Pull down the shade and tap **Change purpose**.
   - The notification shade should dismiss.
   - Re-select **Reply to messages**.
   - The new session should inherit the actual remaining time and the temporary Reels allowance should be gone.
4. If Reels is still visible, normal enforcement should intervene again; choose **Return to Messages**.
   - Navigation should use the existing proven Instagram routing path.

Repeat the equivalent flow for TikTok Search → For You and YouTube Search → Shorts/Home.

## 4. Change purpose without timer extension

1. Start a 10-minute Instagram tunnel and wait long enough for the remaining time to decrease.
2. Pull down the shade and tap **Change purpose**.
   - The shade should dismiss first.
   - The Purpose Gate should then appear over Instagram, not over SystemUI/notification shade.
3. Leave the chooser open for ~30 seconds, then choose a different purpose without changing the time row.
   - The new tunnel inherits the time actually remaining at selection time; the chooser must not add those ~30 seconds back.
4. Open **Change purpose** again and choose **Keep current purpose**.
   - The original tunnel remains active.

## 5. Expiry and check-in actions

1. Let a timed tunnel expire while its protected app is foreground.
   - Notification changes to **Time complete** with **Continue · Change purpose · End**.
2. Tap **Continue**.
   - The shade dismisses and the same purpose continues.
3. For an open-ended Browse tunnel, wait for an intentional check-in.
   - Notification should expose **Continue · Change purpose · End**.

## 6. Notification permission and dismissal

1. Deny notification permission on Android 13+.
   - Core Task Tunnel overlays and policy should continue functioning.
   - Settings → **Tunnel notification controls** should provide a route to enable notifications.
2. Grant permission and start a tunnel.
   - Notification should appear without sound/vibration.
3. On an Android version that permits dismissing ongoing notifications, swipe it away.
   - It should remain hidden for the rest of that tunnel instead of immediately respawning.
4. End that tunnel and start another.
   - A fresh notification should appear.

## 7. Stale-action safety

1. Start Tunnel A, then change purpose so a new Tunnel B/session is created.
2. If an old notification/action can be triggered from notification history or delayed UI, trigger it.
   - It must not end or mutate Tunnel B.

## 8. Privacy / lock screen

1. With a tunnel active, lock the phone.
   - Public lock-screen notification content should reveal only **Task Tunnel active**, not the selected purpose or surface.
2. Confirm no search terms, usernames, video titles, message content, or accessibility text appear in notification content.
