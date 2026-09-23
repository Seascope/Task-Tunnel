# Task Tunnel Play Review Video Script

Record a short, continuous, factual demonstration on the final release candidate. Keep Android taps and resulting state changes visible. Do not edit the video in a way that obscures the Accessibility permission sequence.

1. Launch Task Tunnel with fresh app data. Show **Use distracting apps with a reason** before any Android permission screen.
2. Continue to **Built around the apps you already use** and show that Task Tunnel supports Instagram, YouTube, and TikTok purpose flows.
3. Continue to **Let Task Tunnel notice where you are**. Pause long enough to show what Accessibility can expose, why Task Tunnel needs it, what is not stored as content, local processing, and fail-open behavior.
4. Show the explicit consent copy and the separate **Not now** action. Tap **Agree & open settings**. Emphasize that Android settings open only after this affirmative action.
5. In Android Accessibility settings, enable **Task Tunnel protection**. Return to Task Tunnel.
6. Show the optional Drift setup. Explain that other launchable apps can participate in Drift by package identity only; detailed surface parsing remains limited to Instagram, YouTube, and TikTok. Continue or skip Drift deliberately.
7. Finish setup, then open **Protection** and show that protection is active.
8. Open Instagram. At Purpose Gate choose **Reply to messages** and, if useful, show the time-limit selector.
9. Enter Messages and show that normal matching use continues without an intervention.
10. Enter Reels. Show the intervention and its three user-controlled choices: **Return to Messages**, the temporary **Allow Reels for now** action, and **End Tunnel**.
11. Tap **Return to Messages** and show directed recovery. Trigger the detour again and use **Allow Reels for now** to demonstrate that the override is temporary and reversible. Do not imply Task Tunnel prevents all bypasses.
12. Expand the active-tunnel notification and briefly show its session-scoped controls. Return to the app without implying the notification owns separate tunnel state.
13. Return to Task Tunnel and show **Attention** with the local session history. Briefly show **Review** only if it has a real high-confidence insight; an empty Review is acceptable.
14. Open **Protection** and show the supported apps, check-ins, Drift configuration, and Advanced/troubleshooting separation.
15. Open **Settings > Privacy** and show the Accessibility/privacy explanation. Open **Advanced options > Troubleshooting** and show that the production report contains technical status rather than app content.
16. Show **Clear activity history** and its confirmation dialog. Complete the clear only if useful for the reviewer flow.

Before submission, compare this script with the exact shipped UI and the live Play Console Accessibility declaration. The current Google Play policy requires non-accessibility-tool apps using AccessibilityService to provide an in-app prominent disclosure, describe accessed data and use, and obtain affirmative consent before the sensitive capability is enabled. This script is review evidence, not a guarantee of approval.
