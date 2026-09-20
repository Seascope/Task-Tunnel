# M0 Manual Test Plan

All checks below are **NOT RUN**. Run them first on a physical Android phone with Instagram and YouTube installed. An emulator is a fallback when a phone is unavailable. Run against a debug APK built from the current checkout.

## Setup

1. Build: `./gradlew.bat :app:assembleDebug` (PowerShell: `.\gradlew.bat :app:assembleDebug`).
2. Install or update: `F:\sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`.
3. Open Task Tunnel and use **Open accessibility settings** to complete check A first. Then open **Open sanitized inspector** and arm capture before switching away to either target app; the developer screen cannot remain visible while another app is foreground.

## Checks

| ID | Procedure | Expected result | Result |
|---|---|---|---|
| A | In Task Tunnel, tap **Open accessibility settings**, find Task Tunnel under Accessibility / Installed services, enable it, accept the platform warning, then return to Task Tunnel. | Settings shows the service enabled; Task Tunnel shows both **Accessibility setting: Enabled** and **Service connection: Active**. | NOT RUN |
| B | In the sanitized inspector, enable **Capture when a supported app is foreground**. Leave the app, switch Home, then launch Instagram (`com.instagram.android`) and YouTube (`com.google.android.youtube`) in turn. Return to Task Tunnel after each switch. | No crash or stuck state; **Recent foreground transitions** contains the observed target package(s), and the developer screen retains evidence after returning. | NOT RUN |
| C | With **Capture when a supported app is foreground** enabled, visit both YouTube and Instagram and return to Task Tunnel after each. Inspect the bounded tree summary for each package. | Each target produces a bounded structural summary labeled with package/time and safe node counts/flags, with resource IDs only where exposed by the target UI. If the accessibility root is unavailable, record that capture as unavailable/fail rather than passing. The summary never displays visible text, content descriptions, screenshots, or an unbounded/raw tree. | NOT RUN |
| D | In each target app, navigate across useful screens (YouTube home/search/video/back; Instagram feed/search/profile/back), wait at least one second for throttled capture, then return to Task Tunnel and compare the retained package/time summary and event/tree metadata. Tap **Clear captured tree** and verify the capture is removed. | Different screens produce observable sanitized event/tree changes for each target. Missing useful trees or missing navigation changes is a failed/blocked M0 criterion. The inspector remains bounded and does not retain raw content; Clear removes the retained capture. | NOT RUN |
| E | From the developer screen, tap **Show test overlay in 3 seconds**, immediately switch to YouTube or Instagram before the delay elapses, then wait for the delay to complete. Repeat with the other target app. | The overlay appears after the delay over the target app as a simple sanitized static debug view. It may cover part of the app, but it must not cause navigation or data changes. | NOT RUN |
| F | While the overlay is visible, tap **Close**, then continue interacting with the underlying target app. | Close removes the overlay and the underlying app remains usable at its current screen. | NOT RUN |
| G | Repeat Home → YouTube → Home → Instagram → Home and several rapid switches, then return to Task Tunnel. | Service remains alive or reconnects cleanly; retained developer state does not freeze, duplicate indefinitely, crash, or leave stale overlay windows. | NOT RUN |
| H | Perform a safe source and runtime privacy inspection: run `rg -n "text|contentDescription|AccessibilityNodeInfo|screenshot|Bitmap|SharedPreferences|Room|DataStore" app/src`; inspect `app\src\main` and APK resources; run `F:\sdk\platform-tools\adb.exe shell run-as com.example.tasktunnel find . -maxdepth 4 -type f -print` and inspect only app-created candidate data locally without pasting private values; run `F:\sdk\platform-tools\adb.exe logcat -d | F:\Windows\System32\findstr.exe /I "TaskTunnel tasktunnel"` after reproducing C–G. | No app-created persisted accessibility text, content descriptions, screenshots, or raw node trees are found, and filtered Task Tunnel logs contain no captured content. A file listing alone cannot prove file contents are absent; this also cannot prove absence from transient framework/OEM buffers, other processes, or logs outside the filter. | NOT RUN |

Optional cleanup: disable Task Tunnel in Android Accessibility Settings and confirm the app reports the service as unavailable on its next open.

## Evidence to record

Record Android version, device/emulator model, app version, service enabled state, and a short result for A–H. Screenshots may be used for test reporting only when they do not capture private user content; do not add screenshots or accessibility dumps to the repository.

## Environment readiness

The configured SDK is `F:\sdk` with platform `android-37.0`, build-tools `36.0.0`, and `F:\sdk\platform-tools\adb.exe`. The wrapper is Gradle `9.6.0` and the available JDK is Java 17. `gradle/gradle-daemon-jvm.properties` pins the installed JDK 17 without stale JDK 25 download URLs. The debug build passed with process-local `$env:GRADLE_USER_HOME = 'F:\coding\android-saas\.gradle-user-home'`, explicit `$env:JAVA_HOME = 'D:\jdk-17.0.20.101-hotspot'`, `$env:ANDROID_HOME = 'F:\sdk'`, `$env:ANDROID_SDK_ROOT = 'F:\sdk'`, `--no-daemon --refresh-dependencies`, and `:app:assembleDebug`. APK output: `app\build\outputs\apk\debug\app-debug.apk`. Physical A–H checks remain NOT RUN.
