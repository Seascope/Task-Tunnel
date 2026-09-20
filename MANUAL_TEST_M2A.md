# M2A Instagram diagnostic evidence collection

Use only the debug APK. These checks collect sanitized structural evidence; they do not classify Instagram surfaces.

## Install and prepare

1. Build the debug APK with `gradlew.bat --no-daemon :app:assembleDebug` using JDK 17 and Android SDK `F:\sdk`.
2. Install the latest APK with `F:\sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`.
3. Open Task Tunnel, open Android accessibility settings, and enable the Task Tunnel service.
4. Confirm the developer screen reports an active service connection.

## Capture each surface

Repeat these steps separately for every surface below. Task Tunnel retains only the latest Instagram fingerprint, so store and label each result externally before capturing the next surface.

1. Open the named Instagram surface and let it fully load and stabilize for at least about 1 second.
2. Return to Task Tunnel.
3. Confirm the card says **Last observed Instagram diagnostic** and shows:
   - `Classification: UNCLASSIFIED (M2A evidence collection)`
   - `Confidence: N/A`
   - Package `com.instagram.android`
4. Tap **Copy sanitized Instagram fingerprint**.
5. Record the surface label, the copied sanitized fingerprint, `Classification: UNCLASSIFIED`, and `Confidence: N/A` together.
6. Before saving or sharing it, inspect the copied output. Never paste or share it if it unexpectedly contains sensitive data. Expected output contains no text, usernames, message contents, or content descriptions.

## Evidence matrix

- [ ] **NOT RUN — Home**
- [ ] **NOT RUN — DM inbox**
- [ ] **NOT RUN — Individual DM conversation**
- [ ] **NOT RUN — Explore**
- [ ] **NOT RUN — Reels tab**
- [ ] **NOT RUN — Reel opened from another surface, if possible**
- [ ] **NOT RUN — Profile (optional)**

For every completed row, retain the exact surface label and fingerprint together. Resource IDs and hierarchy can vary by Instagram version, account, locale, and experiments.
